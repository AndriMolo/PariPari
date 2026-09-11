package com.example.paripariapp.ui.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.data.model.SpesaConDettagli;
import com.example.paripariapp.databinding.ItemSpesaBinding;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import com.example.paripariapp.R;

/**
 * Adapter per la lista delle spese in una scheda.
 * Utilizza ListAdapter con DiffUtil e SpesaConDettagli per visualizzare pagatore e split reale.
 */
public class SpesaAdapter extends ListAdapter<SpesaConDettagli, SpesaAdapter.SpesaViewHolder> {

    public SpesaAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<SpesaConDettagli> DIFF_CALLBACK = new DiffUtil.ItemCallback<SpesaConDettagli>() {
        @Override
        public boolean areItemsTheSame(@NonNull SpesaConDettagli oldItem, @NonNull SpesaConDettagli newItem) {
            return Objects.equals(oldItem.getSpesa().getId(), newItem.getSpesa().getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull SpesaConDettagli oldItem, @NonNull SpesaConDettagli newItem) {
            Spesa o = oldItem.getSpesa();
            Spesa n = newItem.getSpesa();
            return Objects.equals(o.getTitolo(), n.getTitolo()) &&
                    Double.compare(o.getImporto(), n.getImporto()) == 0 &&
                    Objects.equals(o.getValuta(), n.getValuta()) &&
                    Objects.equals(oldItem.getNomePagatore(), newItem.getNomePagatore()) &&
                    oldItem.getNumeroPartecipanti() == newItem.getNumeroPartecipanti() &&
                    o.getDataSpesa() == n.getDataSpesa();
        }
    };

    @NonNull
    @Override
    public SpesaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSpesaBinding binding = ItemSpesaBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new SpesaViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull SpesaViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class SpesaViewHolder extends RecyclerView.ViewHolder {

        private final ItemSpesaBinding binding;

        SpesaViewHolder(@NonNull ItemSpesaBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(SpesaConDettagli item) {
            Spesa spesa = item.getSpesa();
            Context context = binding.getRoot().getContext();

            // Titolo e Importo
            binding.tvTitoloSpesa.setText(spesa.getTitolo());
            binding.tvImportoSpesa.setText(
                    context.getString(R.string.spesa_formato_importo, spesa.getImporto(), spesa.getValuta())
            );

            // Pagatore e Data localizzata
            String dataFmt = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(new Date(spesa.getDataSpesa()));
            binding.tvDettaglioPagatore.setText(
                    context.getString(R.string.spesa_pagato_da, item.getNomePagatore(), dataFmt)
            );

            // Conteggio partecipanti con plurali (gestisce singolare/plurale correttamente)
            int persone = item.getNumeroPartecipanti();
            String testoDivisione = context.getResources().getQuantityString(
                    R.plurals.spesa_split_persone,
                    persone,
                    persone
            );
            binding.tvInfoDivisione.setText(testoDivisione);
        }
    }
}