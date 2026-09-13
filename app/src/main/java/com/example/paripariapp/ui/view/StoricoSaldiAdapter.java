package com.example.paripariapp.ui.view;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.databinding.ItemStoricoSaldoBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StoricoSaldiAdapter extends RecyclerView.Adapter<StoricoSaldiAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(Spesa spesa);
    }

    private final List<Spesa> items = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("d MMM yyyy • HH:mm", Locale.getDefault());
    private OnItemClickListener listener;

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<Spesa> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemStoricoSaldoBinding binding = ItemStoricoSaldoBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Spesa item = items.get(position);
        holder.binding.tvTitoloSaldo.setText(item.getTitolo());
        holder.binding.tvDataSaldo.setText(dateFormat.format(new Date(item.getDataSpesa())));
        holder.binding.tvImportoSaldo.setText(String.format(Locale.getDefault(), "%.2f %s", item.getImporto(), item.getValuta()));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemStoricoSaldoBinding binding;

        ViewHolder(@NonNull ItemStoricoSaldoBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
