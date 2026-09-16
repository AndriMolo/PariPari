package com.example.paripariapp.ui.view;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.BilancioPersonaItem;
import com.example.paripariapp.databinding.ItemBilancioPersonaBinding;

import java.util.Locale;
import java.util.Objects;

/**
 * Adapter per la visualizzazione dei bilanci aggregati per persona.
 * Utilizza ListAdapter con DiffUtil e ViewBinding.
 */
public class BilancioPersonaAdapter extends ListAdapter<BilancioPersonaItem, BilancioPersonaAdapter.ViewHolder> {

    private static final DiffUtil.ItemCallback<BilancioPersonaItem> DIFF_CALLBACK = new DiffUtil.ItemCallback<BilancioPersonaItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull BilancioPersonaItem oldItem, @NonNull BilancioPersonaItem newItem) {
            return Objects.equals(oldItem.getSchedaId(), newItem.getSchedaId()) &&
                    Objects.equals(oldItem.getNomePersona(), newItem.getNomePersona());
        }

        @Override
        public boolean areContentsTheSame(@NonNull BilancioPersonaItem oldItem, @NonNull BilancioPersonaItem newItem) {
            return Double.compare(oldItem.getImporto(), newItem.getImporto()) == 0 &&
                    Objects.equals(oldItem.getNomeGruppo(), newItem.getNomeGruppo()) &&
                    Objects.equals(oldItem.getValuta(), newItem.getValuta());
        }
    };

    public interface OnItemClickListener {
        void onItemClick(BilancioPersonaItem item);
    }

    private OnItemClickListener listener;

    public BilancioPersonaAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
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
        BilancioPersonaItem item = getItem(position);
        holder.binding.tvNomePersona.setText(item.getNomePersona());
        holder.binding.tvDettaglioGruppo.setText(item.getNomeGruppo());

        com.example.paripariapp.util.AvatarVisualUtil.applyInitialToTextView(
                holder.binding.tvAvatar,
                item.getNomePersona()
        );

        if (item.isCredito()) {
            holder.binding.tvImportoSaldo.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.credit_green));
            holder.binding.tvImportoSaldo.setText(com.example.paripariapp.util.ImportoUtil.formattaConSegno(item.getImporto(), item.getValuta()));
        } else {
            holder.binding.tvImportoSaldo.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.debt_red));
            holder.binding.tvImportoSaldo.setText(com.example.paripariapp.util.ImportoUtil.formattaConSegno(-Math.abs(item.getImporto()), item.getValuta()));
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemBilancioPersonaBinding binding;
        ViewHolder(@NonNull ItemBilancioPersonaBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}