package com.example.paripariapp.ui.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paripariapp.data.repository.UserPreferencesRepository;
import com.example.paripariapp.databinding.ItemValutaSelettoreBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter per la lista di selezione valute nel Searchable Bottom Sheet.
 * Supporta la ricerca istantanea (filtraggio dinamico) e l'evidenziazione
 * della valuta correntemente attiva.
 */
public class ValutaSelettoreAdapter extends RecyclerView.Adapter<ValutaSelettoreAdapter.ValutaViewHolder> {

    public interface OnCurrencyClickListener {
        void onCurrencyClicked(String currencyFull);
    }

    private final List<String> allCurrencies;
    private final List<String> filteredCurrencies;
    private final String selectedCode;
    private final OnCurrencyClickListener listener;

    public ValutaSelettoreAdapter(@NonNull List<String> currencies,
                                  @Nullable String currentlySelected,
                                  @NonNull OnCurrencyClickListener listener) {
        this.allCurrencies = new ArrayList<>(currencies);
        this.filteredCurrencies = new ArrayList<>(currencies);
        this.selectedCode = UserPreferencesRepository.extractCurrencyCode(currentlySelected);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ValutaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemValutaSelettoreBinding binding = ItemValutaSelettoreBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new ValutaViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ValutaViewHolder holder, int position) {
        String item = filteredCurrencies.get(position);
        holder.bind(item, selectedCode, listener);
    }

    @Override
    public int getItemCount() {
        return filteredCurrencies.size();
    }

    /**
     * Filtra la lista per codice o nome valuta.
     *
     * @param query Testo digitato dall'utente
     * @return Numero di elementi rimanenti dopo il filtro
     */
    public int filter(String query) {
        filteredCurrencies.clear();
        if (query == null || query.trim().isEmpty()) {
            filteredCurrencies.addAll(allCurrencies);
        } else {
            String lower = query.trim().toLowerCase();
            for (String item : allCurrencies) {
                String code = UserPreferencesRepository.extractCurrencyCode(item).toLowerCase();
                String name = UserPreferencesRepository.extractCurrencyName(item).toLowerCase();
                if (code.contains(lower) || name.contains(lower)) {
                    filteredCurrencies.add(item);
                }
            }
        }
        notifyDataSetChanged();
        return filteredCurrencies.size();
    }

    static class ValutaViewHolder extends RecyclerView.ViewHolder {

        private final ItemValutaSelettoreBinding binding;

        public ValutaViewHolder(@NonNull ItemValutaSelettoreBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(String item, String selectedCode, OnCurrencyClickListener listener) {
            String code = UserPreferencesRepository.extractCurrencyCode(item);
            String name = UserPreferencesRepository.extractCurrencyName(item);
            String flag = UserPreferencesRepository.getCurrencyFlag(code);

            binding.tvValutaBandiera.setText(flag);
            binding.tvValutaCodice.setText(code);
            binding.tvValutaNome.setText(name);

            boolean isSelected = selectedCode != null && selectedCode.equalsIgnoreCase(code);
            binding.ivValutaCheck.setVisibility(isSelected ? View.VISIBLE : View.GONE);

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCurrencyClicked(item);
                }
            });
        }
    }
}
