package com.example.paripariapp.ui.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.MembroGruppoPreview;
import com.example.paripariapp.databinding.ItemMembroUnioneBinding;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

public class MembriUnioneAdapter extends RecyclerView.Adapter<MembriUnioneAdapter.MembroViewHolder> {

    public interface OnMembroSelectedListener {
        void onMembroSelected(@Nullable MembroGruppoPreview membro);
    }

    private final List<MembroGruppoPreview> membri = new ArrayList<>();
    private final OnMembroSelectedListener listener;
    private int selectedPosition = RecyclerView.NO_POSITION;

    public MembriUnioneAdapter(OnMembroSelectedListener listener) {
        this.listener = listener;
    }

    public void setMembri(List<MembroGruppoPreview> nuoviMembri) {
        membri.clear();
        if (nuoviMembri != null) {
            membri.addAll(nuoviMembri);
        }
        selectedPosition = RecyclerView.NO_POSITION;
        notifyDataSetChanged();
    }

    public void clearSelection() {
        if (selectedPosition != RecyclerView.NO_POSITION) {
            int oldPos = selectedPosition;
            selectedPosition = RecyclerView.NO_POSITION;
            notifyItemChanged(oldPos);
        }
    }

    @Nullable
    public MembroGruppoPreview getMembroSelezionato() {
        if (selectedPosition >= 0 && selectedPosition < membri.size()) {
            return membri.get(selectedPosition);
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
        MembroGruppoPreview membro = membri.get(position);
        holder.bind(membro, position == selectedPosition);
    }

    @Override
    public int getItemCount() {
        return membri.size();
    }

    class MembroViewHolder extends RecyclerView.ViewHolder {
        private final ItemMembroUnioneBinding binding;

        MembroViewHolder(ItemMembroUnioneBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(MembroGruppoPreview membro, boolean isSelected) {
            Context ctx = itemView.getContext();
            String nome = com.example.paripariapp.data.model.Partecipante.pulisciNome(membro.getNome());
            binding.tvNomeMembro.setText(nome);

            // Iniziale avatar
            String initial = (!nome.isEmpty()) ? String.valueOf(nome.charAt(0)).toUpperCase() : "?";
            binding.tvAvatarInitial.setText(initial);

            int colorPrimary = MaterialColors.getColor(itemView, androidx.appcompat.R.attr.colorPrimary);
            int colorOutlineVariant = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOutlineVariant);
            int colorSurface = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorSurface);
            int colorSurfaceVariant = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorSurfaceVariant);
            int colorOnSurface = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnSurface);
            int colorOutline = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOutline);

            int strokeWidthPx1 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, ctx.getResources().getDisplayMetrics());
            int strokeWidthPx2 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, ctx.getResources().getDisplayMetrics());

            if (!membro.isSostituibile()) {
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

                binding.ivLockedStatus.setVisibility(View.VISIBLE);
                binding.rbSelezioneMembro.setVisibility(View.GONE);
                binding.cardMembro.setOnClickListener(null);
            } else {
                // Membro anonimo/offline: pienamente selezionabile
                binding.cardMembro.setAlpha(1.0f);
                binding.cardMembro.setClickable(true);
                binding.cardMembro.setFocusable(true);

                binding.containerAvatar.setBackgroundResource(R.drawable.bg_circle_avatar);
                binding.tvAvatarInitial.setTextColor(MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnPrimaryContainer));
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

                binding.cardMembro.setOnClickListener(v -> {
                    int pos = getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        int prevPos = selectedPosition;
                        selectedPosition = pos;
                        if (prevPos != RecyclerView.NO_POSITION) notifyItemChanged(prevPos);
                        notifyItemChanged(selectedPosition);

                        if (listener != null) {
                            listener.onMembroSelected(membro);
                        }
                    }
                });
            }
        }
    }
}
