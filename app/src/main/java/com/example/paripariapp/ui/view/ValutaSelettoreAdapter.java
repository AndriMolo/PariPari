package com.example.paripariapp.ui.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paripariapp.data.repository.UserPreferencesRepository;
import com.example.paripariapp.databinding.ItemValutaSelettoreBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter per la lista di selezione valute nel Searchable Bottom Sheet.
 * Utilizza ListAdapter con DiffUtil e ViewBinding per filtraggio istantaneo e fluido.
 */
public class ValutaSelettoreAdapter extends ListAdapter<String, ValutaSelettoreAdapter.ValutaViewHolder> {

    private static final DiffUtil.ItemCallback<String> DIFF_CALLBACK = new DiffUtil.ItemCallback<String>() {
        @Override
        public boolean areItemsTheSame(@NonNull String oldItem, @NonNull String newItem) {
            return oldItem.equals(newItem);
        }

        @Override
        public boolean areContentsTheSame(@NonNull String oldItem, @NonNull String newItem) {
            return oldItem.equals(newItem);
        }
    };

    public interface OnCurrencyClickListener {
        void onCurrencyClicked(String currencyFull);
    }

    private final List<String> allCurrencies;
    private final String selectedCode;
    private final OnCurrencyClickListener listener;

    public ValutaSelettoreAdapter(@NonNull List<String> currencies,
                                  @Nullable String currentlySelected,
                                  @NonNull OnCurrencyClickListener listener) {
        super(DIFF_CALLBACK);
        this.allCurrencies = new ArrayList<>(currencies);
        this.selectedCode = UserPreferencesRepository.extractCurrencyCode(currentlySelected);
        this.listener = listener;
        submitList(new ArrayList<>(currencies));
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
        String item = getItem(position);
        holder.bind(item, selectedCode, listener);
    }

    /**
     * Filtra la lista per codice o nome valuta.
     *
     * @param query Testo digitato dall'utente
     * @return Numero di elementi rimanenti dopo il filtro
     */
    public int filter(String query) {
        List<String> filtered = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) {
            filtered.addAll(allCurrencies);
        } else {
            String lower = query.trim().toLowerCase(java.util.Locale.getDefault());
            for (String item : allCurrencies) {
                String code = UserPreferencesRepository.extractCurrencyCode(item).toLowerCase(java.util.Locale.ROOT);
                String name = UserPreferencesRepository.extractCurrencyName(item).toLowerCase(java.util.Locale.getDefault());
                if (code.contains(lower) || name.contains(lower)) {
                    filtered.add(item);
                }
            }
        }
        submitList(filtered);
        return filtered.size();
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
            binding.getRoot().setSelected(isSelected);

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCurrencyClicked(item);
                }
            });
        }
    }
}
