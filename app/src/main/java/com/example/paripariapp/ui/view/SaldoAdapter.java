package com.example.paripariapp.ui.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.TrasferimentoSaldo;
import com.example.paripariapp.databinding.ItemSaldoTrasferimentoBinding;

import java.util.Objects;

public class SaldoAdapter extends ListAdapter<TrasferimentoSaldo, SaldoAdapter.SaldoViewHolder> {

    public SaldoAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<TrasferimentoSaldo> DIFF_CALLBACK = new DiffUtil.ItemCallback<TrasferimentoSaldo>() {
        @Override
        public boolean areItemsTheSame(@NonNull TrasferimentoSaldo oldItem, @NonNull TrasferimentoSaldo newItem) {
            return Objects.equals(oldItem.getDaPartecipanteId(), newItem.getDaPartecipanteId()) &&
                    Objects.equals(oldItem.getAPartecipanteId(), newItem.getAPartecipanteId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull TrasferimentoSaldo oldItem, @NonNull TrasferimentoSaldo newItem) {
            return Double.compare(oldItem.getImporto(), newItem.getImporto()) == 0 &&
                    Objects.equals(oldItem.getValuta(), newItem.getValuta());
        }
    };

    @NonNull
    @Override
    public SaldoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSaldoTrasferimentoBinding binding = ItemSaldoTrasferimentoBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new SaldoViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull SaldoViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class SaldoViewHolder extends RecyclerView.ViewHolder {
        private final ItemSaldoTrasferimentoBinding binding;

        SaldoViewHolder(@NonNull ItemSaldoTrasferimentoBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(TrasferimentoSaldo item) {
            Context ctx = binding.getRoot().getContext();
            binding.tvDebitore.setText(item.getDaPartecipanteNome());
            binding.tvCreditore.setText(item.getAPartecipanteNome());
            binding.tvImporto.setText(ctx.getString(R.string.saldi_formato_importo, item.getImporto(), item.getValuta()));
        }
    }
}