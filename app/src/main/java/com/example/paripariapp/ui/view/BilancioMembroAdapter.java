package com.example.paripariapp.ui.view;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paripariapp.R;
import com.example.paripariapp.databinding.ItemBilancioMembroBinding;
import com.example.paripariapp.util.CalcolatoreSaldi.BilancioMembro;

import java.util.Locale;
import java.util.Objects;

/**
 * Adapter per la visualizzazione del bilancio netto di ciascun membro.
 * Utilizza ListAdapter con DiffUtil e ViewBinding.
 */
public class BilancioMembroAdapter extends ListAdapter<BilancioMembro, BilancioMembroAdapter.ViewHolder> {

    private static final DiffUtil.ItemCallback<BilancioMembro> DIFF_CALLBACK = new DiffUtil.ItemCallback<BilancioMembro>() {
        @Override
        public boolean areItemsTheSame(@NonNull BilancioMembro oldItem, @NonNull BilancioMembro newItem) {
            return Objects.equals(oldItem.getPartecipanteId(), newItem.getPartecipanteId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull BilancioMembro oldItem, @NonNull BilancioMembro newItem) {
            return Double.compare(oldItem.getSaldoNetto(), newItem.getSaldoNetto()) == 0 &&
                    Objects.equals(oldItem.getNomePartecipante(), newItem.getNomePartecipante()) &&
                    Objects.equals(oldItem.getValuta(), newItem.getValuta());
        }
    };

    public BilancioMembroAdapter() {
        super(DIFF_CALLBACK);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemBilancioMembroBinding binding = ItemBilancioMembroBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BilancioMembro item = getItem(position);
        holder.binding.nomeMembro.setText(item.getNomePartecipante());

        String iniziale = !item.getNomePartecipante().isEmpty()
                ? String.valueOf(item.getNomePartecipante().charAt(0)).toUpperCase(Locale.getDefault())
                : "?";
        holder.binding.avatarIniziale.setText(iniziale);

        double saldo = item.getSaldoNetto();
        String valuta = item.getValuta() != null ? item.getValuta() : "EUR";

        if (saldo > 0.009) {
            holder.binding.importoBilancio.setText(String.format(Locale.getDefault(), "+%.2f %s", saldo, valuta));
            holder.binding.importoBilancio.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.credit_green));
        } else if (saldo < -0.009) {
            holder.binding.importoBilancio.setText(String.format(Locale.getDefault(), "%.2f %s", saldo, valuta));
            holder.binding.importoBilancio.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.debt_red));
        } else {
            holder.binding.importoBilancio.setText(String.format(Locale.getDefault(), "0,00 %s", valuta));
            holder.binding.importoBilancio.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.md_theme_outline));
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemBilancioMembroBinding binding;

        ViewHolder(@NonNull ItemBilancioMembroBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
