package com.example.paripariapp.ui.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.Scheda;
import com.example.paripariapp.databinding.ItemSchedaBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private List<Scheda> localList = new ArrayList<>();
    private final Map<String, Integer> partecipantiCountMap = new HashMap<>();

    public SchedaAdapter(OnSchedaClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    @Override
    public void submitList(@Nullable List<Scheda> list) {
        if (list != null) {
            localList = new ArrayList<>(list);
        } else {
            localList = new ArrayList<>();
        }
        super.submitList(list);
    }

    public void moveItem(int fromPosition, int toPosition) {
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(localList, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(localList, i, i - 1);
            }
        }
        notifyItemMoved(fromPosition, toPosition);
    }

    public List<Scheda> getLocalList() {
        return localList;
    }

    public static final Object PAYLOAD_COUNT = new Object();

    public void aggiornaConteggioPartecipanti(Map<String, Integer> mappaConteggi) {
        if (mappaConteggi != null) {
            partecipantiCountMap.clear();
            partecipantiCountMap.putAll(mappaConteggi);
            notifyItemRangeChanged(0, getItemCount(), PAYLOAD_COUNT);
        }
    }

    private static final DiffUtil.ItemCallback<Scheda> DIFF_CALLBACK = new DiffUtil.ItemCallback<>() {
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
        Scheda scheda = getItem(position);
        Integer countObj = partecipantiCountMap.get(scheda.getId());
        int count = countObj != null ? countObj : 1;
        holder.bind(scheda, count);
    }

    @Override
    public void onBindViewHolder(@NonNull SchedaViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains(PAYLOAD_COUNT)) {
            Scheda scheda = getItem(position);
            Integer countObj = partecipantiCountMap.get(scheda.getId());
            int count = countObj != null ? countObj : 1;
            holder.updateCountOnly(count);
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    public static class SchedaViewHolder extends RecyclerView.ViewHolder {

        private final ItemSchedaBinding binding;
        private final OnSchedaClickListener listener;

        public SchedaViewHolder(@NonNull ItemSchedaBinding binding, OnSchedaClickListener listener) {
            super(binding.getRoot());
            this.binding = binding;
            this.listener = listener;
        }

        public void bind(Scheda scheda, int count) {
            Context context = itemView.getContext();

            binding.tvTitoloScheda.setText(scheda.getTitolo());
            binding.tvValutaBadge.setText(scheda.getValutaPredefinita());

            updateCountOnly(count);

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSchedaClick(scheda);
                }
            });
        }

        public void updateCountOnly(int count) {
            Context context = itemView.getContext();
            String testoPartecipanti = context.getResources().getQuantityString(
                    R.plurals.label_partecipanti_count,
                    count,
                    count
            );
            binding.tvPartecipantiCount.setText(testoPartecipanti);
        }
    }
}