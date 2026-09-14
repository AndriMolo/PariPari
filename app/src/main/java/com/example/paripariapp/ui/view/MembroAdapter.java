package com.example.paripariapp.ui.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.databinding.ItemMembroGestioneBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Adapter per la gestione dei partecipanti di una scheda.
 * Utilizza ListAdapter con DiffUtil e ViewBinding per massimizzare le performance.
 */
public class MembroAdapter extends ListAdapter<Partecipante, MembroAdapter.MembroViewHolder> {

    private static final DiffUtil.ItemCallback<Partecipante> DIFF_CALLBACK = new DiffUtil.ItemCallback<Partecipante>() {
        @Override
        public boolean areItemsTheSame(@NonNull Partecipante oldItem, @NonNull Partecipante newItem) {
            return Objects.equals(oldItem.getId(), newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Partecipante oldItem, @NonNull Partecipante newItem) {
            return Objects.equals(oldItem.getNome(), newItem.getNome()) &&
                    Objects.equals(oldItem.getEmail(), newItem.getEmail()) &&
                    Objects.equals(oldItem.getSchedaId(), newItem.getSchedaId());
        }
    };

    private OnEliminaClickListener onEliminaClickListener;
    private OnModificaClickListener onModificaClickListener;
    private boolean isCapogruppo = true;
    private FirebaseUser currentUser;
    private String currentMyId;

    public interface OnEliminaClickListener {
        void onEliminaClick(Partecipante partecipante);
    }

    public interface OnModificaClickListener {
        void onModificaClick(Partecipante partecipante);
    }

    public MembroAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnEliminaClickListener(OnEliminaClickListener listener) {
        this.onEliminaClickListener = listener;
    }

    public void setOnModificaClickListener(OnModificaClickListener listener) {
        this.onModificaClickListener = listener;
    }

    public void setCapogruppo(boolean capogruppo) {
        if (this.isCapogruppo != capogruppo) {
            this.isCapogruppo = capogruppo;
            notifyItemRangeChanged(0, getItemCount());
        }
    }

    @Override
    public void submitList(@Nullable List<Partecipante> list) {
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        currentMyId = Partecipante.findCurrentUserId(list, currentUser);
        super.submitList(list != null ? new ArrayList<>(list) : null);
    }

    @NonNull
    @Override
    public MembroViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMembroGestioneBinding binding = ItemMembroGestioneBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new MembroViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MembroViewHolder holder, int position) {
        Partecipante p = getItem(position);

        boolean isMe = (currentMyId != null && currentMyId.equals(p.getId())) ||
                Partecipante.isCurrentUserParticipant(p, currentUser);

        String nomeDisplay = p.getNome();
        if (isMe && !nomeDisplay.toLowerCase().endsWith("(io)") && !nomeDisplay.toLowerCase().endsWith("(me)")) {
            nomeDisplay = nomeDisplay + " (io)";
        }
        holder.binding.nomePartecipante.setText(nomeDisplay);

        String iniziale = !p.getNome().isEmpty() ? String.valueOf(p.getNome().charAt(0)).toUpperCase() : "?";
        holder.binding.avatarIniziale.setText(iniziale);

        holder.binding.bottoneModifica.setVisibility(isMe ? View.VISIBLE : View.GONE);
        holder.binding.bottoneModifica.setOnClickListener(v -> {
            if (onModificaClickListener != null) {
                onModificaClickListener.onModificaClick(p);
            }
        });

        holder.binding.bottoneElimina.setVisibility((isCapogruppo || isMe) ? View.VISIBLE : View.GONE);
        holder.binding.bottoneElimina.setOnClickListener(v -> {
            if (onEliminaClickListener != null) {
                onEliminaClickListener.onEliminaClick(p);
            }
        });
    }

    static class MembroViewHolder extends RecyclerView.ViewHolder {
        final ItemMembroGestioneBinding binding;

        MembroViewHolder(@NonNull ItemMembroGestioneBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
