package com.example.paripariapp.ui.view;

import android.content.Context;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paripariapp.data.model.Scheda;
import com.example.paripariapp.databinding.ItemSchedaBinding;

import java.util.Date;
import java.util.Objects;

/**
 * Adapter per la RecyclerView delle Schede Spese.
 * Utilizza ListAdapter e DiffUtil per aggiornamenti di lista efficienti a 60/120 fps.
 */
public class SchedaAdapter extends ListAdapter<Scheda, SchedaAdapter.SchedaViewHolder> {

    public interface OnSchedaClickListener {
        void onSchedaClick(Scheda scheda);
    }

    private final OnSchedaClickListener listener;

    public SchedaAdapter(OnSchedaClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<Scheda> DIFF_CALLBACK = new DiffUtil.ItemCallback<Scheda>() {
        @Override
        public boolean areItemsTheSame(@NonNull Scheda oldItem, @NonNull Scheda newItem) {
            return Objects.equals(oldItem.getId(), newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Scheda oldItem, @NonNull Scheda newItem) {
            return Objects.equals(oldItem.getTitolo(), newItem.getTitolo()) &&
                    Objects.equals(oldItem.getValutaPredefinita(), newItem.getValutaPredefinita()) &&
                    oldItem.getDataAggiornamento() == newItem.getDataAggiornamento();
        }
    };

    @NonNull
    @Override
    public SchedaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSchedaBinding binding = ItemSchedaBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new SchedaViewHolder(binding, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull SchedaViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class SchedaViewHolder extends RecyclerView.ViewHolder {

        private final ItemSchedaBinding binding;
        private final OnSchedaClickListener listener;

        SchedaViewHolder(@NonNull ItemSchedaBinding binding, OnSchedaClickListener listener) {
            super(binding.getRoot());
            this.binding = binding;
            this.listener = listener;
        }

        void bind(Scheda scheda) {
            binding.tvTitoloScheda.setText(scheda.getTitolo());
            binding.tvValutaBadge.setText(scheda.getValutaPredefinita());

            Context context = itemView.getContext();
            String dataFormatted = DateFormat.getDateFormat(context).format(new Date(scheda.getDataCreazione()));
            binding.tvDataScheda.setText(dataFormatted);

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSchedaClick(scheda);
                }
            });
        }
    }
}
