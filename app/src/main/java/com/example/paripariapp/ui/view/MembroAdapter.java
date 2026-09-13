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
    private OnModificaClickListener onModificaClickListener;
    private boolean isCapogruppo = true;

    public interface OnEliminaClickListener {
        void onEliminaClick(Partecipante partecipante);
    }

    public interface OnModificaClickListener {
        void onModificaClick(Partecipante partecipante);
    }

    public void setOnEliminaClickListener(OnEliminaClickListener listener) {
        this.onEliminaClickListener = listener;
    }

    public void setOnModificaClickListener(OnModificaClickListener listener) {
        this.onModificaClickListener = listener;
    }

    public void setCapogruppo(boolean capogruppo) {
        this.isCapogruppo = capogruppo;
        notifyDataSetChanged();
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

        com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        String myId = Partecipante.findCurrentUserId(items, currentUser);
        boolean isMe = Partecipante.isCurrentUserParticipant(p, currentUser) || (myId != null && myId.equals(p.getId()));

        String nomeDisplay = p.getNome();
        if (isMe && !nomeDisplay.toLowerCase().endsWith("(io)") && !nomeDisplay.toLowerCase().endsWith("(me)")) {
            nomeDisplay = nomeDisplay + " (io)";
        }
        holder.tvNome.setText(nomeDisplay);

        String iniziale = !p.getNome().isEmpty() ? String.valueOf(p.getNome().charAt(0)).toUpperCase() : "?";
        holder.tvIniziale.setText(iniziale);

        holder.btnModifica.setVisibility(isMe ? View.VISIBLE : View.GONE);
        holder.btnModifica.setOnClickListener(v -> {
            if (onModificaClickListener != null) {
                onModificaClickListener.onModificaClick(p);
            }
        });

        holder.btnElimina.setVisibility((isCapogruppo || isMe) ? View.VISIBLE : View.GONE);
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
        View btnModifica;
        View btnElimina;

        MembroViewHolder(@NonNull View itemView) {
            super(itemView);
            tvIniziale = itemView.findViewById(R.id.avatar_iniziale);
            tvNome = itemView.findViewById(R.id.nome_partecipante);
            btnModifica = itemView.findViewById(R.id.bottone_modifica);
            btnElimina = itemView.findViewById(R.id.bottone_elimina);
        }
    }
}
