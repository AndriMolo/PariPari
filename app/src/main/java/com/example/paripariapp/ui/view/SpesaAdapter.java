package com.example.paripariapp.ui.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.data.model.SpesaConDettagli;
import com.example.paripariapp.data.model.SpesaListItem;
import com.example.paripariapp.databinding.ItemSpesaBinding;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

/**
 * Adapter per la visualizzazione raggruppata per data delle spese.
 * Gestisce due ViewType: TYPE_HEADER (per la data) e TYPE_ITEM (per la spesa).
 */
public class SpesaAdapter extends ListAdapter<SpesaListItem, RecyclerView.ViewHolder> {

    public SpesaAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<SpesaListItem> DIFF_CALLBACK = new DiffUtil.ItemCallback<SpesaListItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull SpesaListItem oldItem, @NonNull SpesaListItem newItem) {
            if (oldItem.getType() != newItem.getType()) return false;
            if (oldItem.getType() == SpesaListItem.TYPE_HEADER) {
                return Objects.equals(oldItem.getHeaderTitle(), newItem.getHeaderTitle());
            } else {
                return Objects.equals(oldItem.getSpesa().getSpesa().getId(), newItem.getSpesa().getSpesa().getId());
            }
        }

        @Override
        public boolean areContentsTheSame(@NonNull SpesaListItem oldItem, @NonNull SpesaListItem newItem) {
            if (oldItem.getType() == SpesaListItem.TYPE_HEADER) {
                return Objects.equals(oldItem.getHeaderTitle(), newItem.getHeaderTitle());
            }
            SpesaConDettagli o = oldItem.getSpesa();
            SpesaConDettagli n = newItem.getSpesa();
            return Objects.equals(o.getSpesa().getTitolo(), n.getSpesa().getTitolo()) &&
                    Double.compare(o.getSpesa().getImporto(), n.getSpesa().getImporto()) == 0 &&
                    Objects.equals(o.getSpesa().getValuta(), n.getSpesa().getValuta()) &&
                    Objects.equals(o.getNomePagatore(), n.getNomePagatore()) &&
                    o.getNumeroPartecipanti() == n.getNumeroPartecipanti() &&
                    o.getSpesa().getDataSpesa() == n.getSpesa().getDataSpesa();
        }
    };

    @Override
    public int getItemViewType(int position) {
        return getItem(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == SpesaListItem.TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_spesa_header_data, parent, false);
            return new HeaderViewHolder(view);
        } else {
            ItemSpesaBinding binding = ItemSpesaBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new SpesaViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        SpesaListItem item = getItem(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind(item.getHeaderTitle());
        } else if (holder instanceof SpesaViewHolder) {
            ((SpesaViewHolder) holder).bind(item.getSpesa());
        }
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvHeader;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvHeader = itemView.findViewById(R.id.tv_header_data);
        }

        public void bind(String dataTitolo) {
            tvHeader.setText(dataTitolo);
        }
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

            // Pagatore e Data
            String dataFmt = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(new Date(spesa.getDataSpesa()));
            String pagatore = item.getNomePagatore() != null ? item.getNomePagatore() : "—";
            binding.tvDettaglioPagatore.setText(
                    context.getString(R.string.spesa_pagato_da, pagatore, dataFmt)
            );

            // Divisione partecipanti tramite risorsa plurals
            int persone = item.getNumeroPartecipanti();
            binding.tvInfoDivisione.setText(
                    context.getResources().getQuantityString(R.plurals.spesa_split_persone, persone, persone)
            );
        }
    }
}