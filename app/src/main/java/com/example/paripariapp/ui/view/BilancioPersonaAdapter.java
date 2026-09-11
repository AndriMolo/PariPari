package com.example.paripariapp.ui.view;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.BilancioPersonaItem;
import com.example.paripariapp.databinding.ItemBilancioPersonaBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BilancioPersonaAdapter extends RecyclerView.Adapter<BilancioPersonaAdapter.ViewHolder> {

    private final List<BilancioPersonaItem> items = new ArrayList<>();

    public void submitList(List<BilancioPersonaItem> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemBilancioPersonaBinding binding = ItemBilancioPersonaBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BilancioPersonaItem item = items.get(position);
        holder.binding.tvNomePersona.setText(item.getNomePersona());
        holder.binding.tvDettaglioGruppo.setText(item.getNomeGruppo());

        String iniziale = !item.getNomePersona().isEmpty() ? String.valueOf(item.getNomePersona().charAt(0)).toUpperCase() : "?";
        holder.binding.tvAvatar.setText(iniziale);

        if (item.isCredito()) {
            holder.binding.tvImportoSaldo.setTextColor(Color.parseColor("#2E7D32"));
            holder.binding.tvImportoSaldo.setText(String.format(Locale.getDefault(), "+%.2f %s", item.getImporto(), item.getValuta()));
        } else {
            holder.binding.tvImportoSaldo.setTextColor(Color.parseColor("#C62828"));
            holder.binding.tvImportoSaldo.setText(String.format(Locale.getDefault(), "-%.2f %s", Math.abs(item.getImporto()), item.getValuta()));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ItemBilancioPersonaBinding binding;
        ViewHolder(@NonNull ItemBilancioPersonaBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}