package com.example.paripariapp.ui.view;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.databinding.ItemStoricoSaldoBinding;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

/**
 * Adapter per la visualizzazione dello storico dei saldi e pareggi effettuati.
 * Utilizza ListAdapter con DiffUtil e ViewBinding.
 */
public class StoricoSaldiAdapter extends ListAdapter<Spesa, StoricoSaldiAdapter.ViewHolder> {

    private static final DiffUtil.ItemCallback<Spesa> DIFF_CALLBACK = new DiffUtil.ItemCallback<Spesa>() {
        @Override
        public boolean areItemsTheSame(@NonNull Spesa oldItem, @NonNull Spesa newItem) {
            return Objects.equals(oldItem.getId(), newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Spesa oldItem, @NonNull Spesa newItem) {
            return Objects.equals(oldItem.getTitolo(), newItem.getTitolo()) &&
                    Double.compare(oldItem.getImporto(), newItem.getImporto()) == 0 &&
                    oldItem.getDataSpesa() == newItem.getDataSpesa() &&
                    Objects.equals(oldItem.getValuta(), newItem.getValuta());
        }
    };

    public interface OnItemClickListener {
        void onItemClick(Spesa spesa);
    }

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("d MMM yyyy • HH:mm", Locale.getDefault());
    private OnItemClickListener listener;

    public StoricoSaldiAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
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
        Spesa item = getItem(position);
        holder.binding.tvTitoloSaldo.setText(item.getTitolo());
        holder.binding.tvDataSaldo.setText(dateFormat.format(new Date(item.getDataSpesa())));
        holder.binding.tvImportoSaldo.setText(String.format(Locale.getDefault(), "%.2f %s", item.getImporto(), item.getValuta()));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemStoricoSaldoBinding binding;

        ViewHolder(@NonNull ItemStoricoSaldoBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
