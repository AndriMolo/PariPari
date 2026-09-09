package com.example.paripariapp.ui.view;

import android.content.Context;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.databinding.ItemSpesaBinding;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Adapter per la lista delle spese in una scheda.
 * Utilizza ListAdapter con DiffUtil per prestazioni elevate a 60/120 fps.
 */
public class SpesaAdapter extends ListAdapter<Spesa, SpesaAdapter.SpesaViewHolder> {

    private final Map<String, String> partecipantiMap = new HashMap<>();
    private int numeroPartecipanti = 1;

    public SpesaAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setPartecipanti(List<Partecipante> partecipanti) {
        partecipantiMap.clear();
        if (partecipanti != null) {
            numeroPartecipanti = Math.max(1, partecipanti.size());
            for (Partecipante p : partecipanti) {
                partecipantiMap.put(p.getId(), p.getNome());
            }
        }
        notifyDataSetChanged();
    }

    private static final DiffUtil.ItemCallback<Spesa> DIFF_CALLBACK = new DiffUtil.ItemCallback<Spesa>() {
        @Override
        public boolean areItemsTheSame(@NonNull Spesa oldItem, @NonNull Spesa newItem) {
            return Objects.equals(oldItem.getId(), newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Spesa oldItem, @NonNull Spesa newItem) {
            return Objects.equals(oldItem.getTitolo(), newItem.getTitolo()) &&
                    Double.compare(oldItem.getImporto(), newItem.getImporto()) == 0 &&
                    Objects.equals(oldItem.getValuta(), newItem.getValuta()) &&
                    Objects.equals(oldItem.getPagatoDaId(), newItem.getPagatoDaId()) &&
                    oldItem.getDataSpesa() == newItem.getDataSpesa();
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

    class SpesaViewHolder extends RecyclerView.ViewHolder {

        private final ItemSpesaBinding binding;

        SpesaViewHolder(@NonNull ItemSpesaBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Spesa spesa) {
            Context context = itemView.getContext();

            binding.tvTitoloSpesa.setText(spesa.getTitolo());

            // Nome pagatore
            String nomePagatore = partecipantiMap.get(spesa.getPagatoDaId());
            if (nomePagatore == null || nomePagatore.isEmpty()) {
                nomePagatore = context.getString(R.string.partecipante_io);
            }

            // Data formattata
            String dataFormattata = DateFormat.getMediumDateFormat(context).format(new Date(spesa.getDataSpesa()));
            String pagatoreTesto = context.getString(R.string.label_pagato_da, nomePagatore);
            binding.tvDettaglioPagatore.setText(String.format("%s • %s", pagatoreTesto, dataFormattata));

            // Importo formattato
            binding.tvImportoSpesa.setText(String.format(Locale.getDefault(), "%.2f %s", spesa.getImporto(), spesa.getValuta()));

            // Divisione equa
            binding.tvInfoDivisione.setText(context.getString(R.string.label_divisione_equa, numeroPartecipanti));
        }
    }
}
