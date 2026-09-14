package com.example.paripariapp.ui.view;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.MembroGruppoPreview;
import com.example.paripariapp.databinding.ItemMembroUnioneBinding;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Adapter ottimizzato per la selezione del membro in fase di unione a una scheda condivisa.
 * Utilizza ListAdapter con DiffUtil, pre-risolve i colori del tema e le dimensioni di stroke nel ViewHolder,
 * evitando allocazioni e calcoli ripetuti durante lo scorrimento.
 */
public class MembriUnioneAdapter extends ListAdapter<MembroGruppoPreview, MembriUnioneAdapter.MembroViewHolder> {

    public interface OnMembroSelectedListener {
        void onMembroSelected(@Nullable MembroGruppoPreview membro);
    }

    private static final DiffUtil.ItemCallback<MembroGruppoPreview> DIFF_CALLBACK = new DiffUtil.ItemCallback<MembroGruppoPreview>() {
        @Override
        public boolean areItemsTheSame(@NonNull MembroGruppoPreview oldItem, @NonNull MembroGruppoPreview newItem) {
            return Objects.equals(oldItem.getId(), newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull MembroGruppoPreview oldItem, @NonNull MembroGruppoPreview newItem) {
            return Objects.equals(oldItem.getNome(), newItem.getNome()) &&
                    oldItem.isSostituibile() == newItem.isSostituibile() &&
                    Objects.equals(oldItem.getEmail(), newItem.getEmail());
        }
    };

    private final OnMembroSelectedListener listener;
    private int selectedPosition = RecyclerView.NO_POSITION;
    @Nullable
    private String currentUid;

    public MembriUnioneAdapter(OnMembroSelectedListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    public void setCurrentUid(@Nullable String currentUid) {
        this.currentUid = currentUid;
    }

    public void setMembri(List<MembroGruppoPreview> nuoviMembri) {
        selectedPosition = RecyclerView.NO_POSITION;
        submitList(nuoviMembri != null ? new ArrayList<>(nuoviMembri) : null);
    }

    public void clearSelection() {
        if (selectedPosition != RecyclerView.NO_POSITION) {
            int oldPos = selectedPosition;
            selectedPosition = RecyclerView.NO_POSITION;
            notifyItemChanged(oldPos);
        }
    }

    public void selectMemberById(@Nullable String id) {
        if (id == null) return;
        for (int i = 0; i < getItemCount(); i++) {
            MembroGruppoPreview m = getItem(i);
            if (m != null && id.equals(m.getId())) {
                int prev = selectedPosition;
                selectedPosition = i;
                if (prev != RecyclerView.NO_POSITION) notifyItemChanged(prev);
                notifyItemChanged(selectedPosition);
                break;
            }
        }
    }

    @Nullable
    public MembroGruppoPreview getMembroSelezionato() {
        if (selectedPosition >= 0 && selectedPosition < getItemCount()) {
            return getItem(selectedPosition);
        }
        return null;
    }

    @NonNull
    @Override
    public MembroViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMembroUnioneBinding binding = ItemMembroUnioneBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new MembroViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MembroViewHolder holder, int position) {
        MembroGruppoPreview membro = getItem(position);
        holder.bind(membro, position == selectedPosition);
    }

    class MembroViewHolder extends RecyclerView.ViewHolder {
        private final ItemMembroUnioneBinding binding;
        private final int colorPrimary;
        private final int colorOutlineVariant;
        private final int colorSurface;
        private final int colorSurfaceVariant;
        private final int colorOnSurface;
        private final int colorOutline;
        private final int colorOnPrimaryContainer;
        private final int strokeWidthPx1;
        private final int strokeWidthPx2;

        MembroViewHolder(ItemMembroUnioneBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            Context ctx = itemView.getContext();
            colorPrimary = MaterialColors.getColor(itemView, androidx.appcompat.R.attr.colorPrimary);
            colorOutlineVariant = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOutlineVariant);
            colorSurface = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorSurface);
            colorSurfaceVariant = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorSurfaceVariant);
            colorOnSurface = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnSurface);
            colorOutline = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOutline);
            colorOnPrimaryContainer = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnPrimaryContainer);

            strokeWidthPx1 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, ctx.getResources().getDisplayMetrics());
            strokeWidthPx2 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, ctx.getResources().getDisplayMetrics());

            binding.cardMembro.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && pos < getItemCount()) {
                    MembroGruppoPreview m = getItem(pos);
                    if (m != null && m.isSelezionabileDa(currentUid)) {
                        int prevPos = selectedPosition;
                        selectedPosition = pos;
                        if (prevPos != RecyclerView.NO_POSITION) notifyItemChanged(prevPos);
                        notifyItemChanged(selectedPosition);

                        if (listener != null) {
                            listener.onMembroSelected(m);
                        }
                    }
                }
            });
        }

        void bind(MembroGruppoPreview membro, boolean isSelected) {
            String nome = com.example.paripariapp.data.model.Partecipante.pulisciNome(membro.getNome());
            binding.tvNomeMembro.setText(nome);

            // Iniziale avatar con Locale esplicito
            String initial = (!nome.isEmpty()) ? String.valueOf(nome.charAt(0)).toUpperCase(Locale.getDefault()) : "?";
            binding.tvAvatarInitial.setText(initial);

            boolean isMine = membro.isMyProfile(currentUid);

            if (isMine) {
                // Il profilo dell'utente: evidenziato e pienamente selezionabile
                binding.cardMembro.setAlpha(1.0f);
                binding.cardMembro.setClickable(true);
                binding.cardMembro.setFocusable(true);

                binding.containerAvatar.setBackgroundResource(R.drawable.bg_circle_avatar);
                binding.tvAvatarInitial.setTextColor(colorOnPrimaryContainer);
                binding.tvNomeMembro.setTextColor(colorOnSurface);
                binding.tvStatoMembro.setVisibility(View.VISIBLE);
                binding.tvStatoMembro.setText(R.string.stato_tuo_profilo);
                binding.tvStatoMembro.setTextColor(colorPrimary);

                binding.ivLockedStatus.setVisibility(View.GONE);
                binding.rbSelezioneMembro.setVisibility(View.VISIBLE);
                binding.rbSelezioneMembro.setChecked(isSelected);

                if (isSelected) {
                    binding.cardMembro.setStrokeWidth(strokeWidthPx2);
                    binding.cardMembro.setStrokeColor(colorPrimary);
                    binding.cardMembro.setCardBackgroundColor(colorSurfaceVariant);
                } else {
                    binding.cardMembro.setStrokeWidth(strokeWidthPx1);
                    binding.cardMembro.setStrokeColor(colorPrimary);
                    binding.cardMembro.setCardBackgroundColor(colorSurface);
                }
            } else if (!membro.isSostituibile()) {
                // Membro già autenticato / bloccato: stile grigio attenuato, non cliccabile
                binding.cardMembro.setAlpha(0.38f);
                binding.cardMembro.setClickable(false);
                binding.cardMembro.setFocusable(false);
                binding.cardMembro.setStrokeWidth(strokeWidthPx1);
                binding.cardMembro.setStrokeColor(colorOutlineVariant);
                binding.cardMembro.setCardBackgroundColor(colorSurface);

                binding.containerAvatar.setBackgroundResource(R.drawable.bg_circle_avatar_muted);
                binding.tvAvatarInitial.setTextColor(colorOutline);
                binding.tvNomeMembro.setTextColor(colorOutline);

                binding.tvStatoMembro.setVisibility(View.VISIBLE);
                binding.tvStatoMembro.setText(R.string.stato_gia_registrato);
                binding.tvStatoMembro.setTextColor(colorOutline);

                binding.ivLockedStatus.setVisibility(View.VISIBLE);
                binding.rbSelezioneMembro.setVisibility(View.GONE);
            } else {
                // Membro anonimo/offline: pienamente selezionabile
                binding.cardMembro.setAlpha(1.0f);
                binding.cardMembro.setClickable(true);
                binding.cardMembro.setFocusable(true);

                binding.containerAvatar.setBackgroundResource(R.drawable.bg_circle_avatar);
                binding.tvAvatarInitial.setTextColor(colorOnPrimaryContainer);
                binding.tvNomeMembro.setTextColor(colorOnSurface);
                binding.tvStatoMembro.setVisibility(View.GONE);

                binding.ivLockedStatus.setVisibility(View.GONE);
                binding.rbSelezioneMembro.setVisibility(View.VISIBLE);
                binding.rbSelezioneMembro.setChecked(isSelected);

                if (isSelected) {
                    binding.cardMembro.setStrokeWidth(strokeWidthPx2);
                    binding.cardMembro.setStrokeColor(colorPrimary);
                    binding.cardMembro.setCardBackgroundColor(colorSurfaceVariant);
                } else {
                    binding.cardMembro.setStrokeWidth(strokeWidthPx1);
                    binding.cardMembro.setStrokeColor(colorOutlineVariant);
                    binding.cardMembro.setCardBackgroundColor(colorSurface);
                }
            }
        }
    }
}
