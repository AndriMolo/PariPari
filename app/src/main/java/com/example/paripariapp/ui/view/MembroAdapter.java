package com.example.paripariapp.ui.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.Partecipante;

import java.util.ArrayList;
import java.util.List;

public class MembroAdapter extends RecyclerView.Adapter<MembroAdapter.MembroViewHolder> {

    private final List<Partecipante> items = new ArrayList<>();
    private OnEliminaClickListener onEliminaClickListener;

    public interface OnEliminaClickListener {
        void onEliminaClick(Partecipante partecipante);
    }

    public void setOnEliminaClickListener(OnEliminaClickListener listener) {
        this.onEliminaClickListener = listener;
    }

    public void submitList(List<Partecipante> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MembroViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_membro_gestione, parent, false);
        return new MembroViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull MembroViewHolder holder, int position) {
        Partecipante p = items.get(position);
        holder.tvNome.setText(p.getNome());

        String iniziale = !p.getNome().isEmpty() ? String.valueOf(p.getNome().charAt(0)).toUpperCase() : "?";
        holder.tvIniziale.setText(iniziale);

        holder.btnElimina.setOnClickListener(v -> {
            if (onEliminaClickListener != null) {
                onEliminaClickListener.onEliminaClick(p);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class MembroViewHolder extends RecyclerView.ViewHolder {
        TextView tvIniziale;
        TextView tvNome;
        View btnElimina;

        MembroViewHolder(@NonNull View itemView) {
            super(itemView);
            tvIniziale = itemView.findViewById(R.id.avatar_iniziale);
            tvNome = itemView.findViewById(R.id.nome_partecipante);
            btnElimina = itemView.findViewById(R.id.bottone_elimina);
        }
    }
}