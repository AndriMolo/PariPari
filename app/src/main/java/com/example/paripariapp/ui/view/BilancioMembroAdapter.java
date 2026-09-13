package com.example.paripariapp.ui.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paripariapp.R;
import com.example.paripariapp.util.CalcolatoreSaldi.BilancioMembro;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BilancioMembroAdapter extends RecyclerView.Adapter<BilancioMembroAdapter.ViewHolder> {

    private final List<BilancioMembro> items = new ArrayList<>();

    public void submitList(List<BilancioMembro> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bilancio_membro, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BilancioMembro item = items.get(position);
        holder.tvNome.setText(item.getNomePartecipante());

        String iniziale = !item.getNomePartecipante().isEmpty()
                ? String.valueOf(item.getNomePartecipante().charAt(0)).toUpperCase()
                : "?";
        holder.tvIniziale.setText(iniziale);

        double saldo = item.getSaldoNetto();
        String valuta = item.getValuta() != null ? item.getValuta() : "EUR";

        if (saldo > 0.009) {
            holder.tvImporto.setText(String.format(Locale.getDefault(), "+%.2f %s", saldo, valuta));
            holder.tvImporto.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.credit_green));
        } else if (saldo < -0.009) {
            holder.tvImporto.setText(String.format(Locale.getDefault(), "%.2f %s", saldo, valuta));
            holder.tvImporto.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.debt_red));
        } else {
            holder.tvImporto.setText(String.format(Locale.getDefault(), "0,00 %s", valuta));
            holder.tvImporto.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.md_theme_outline));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvIniziale;
        TextView tvNome;
        TextView tvImporto;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvIniziale = itemView.findViewById(R.id.avatar_iniziale);
            tvNome = itemView.findViewById(R.id.nome_membro);
            tvImporto = itemView.findViewById(R.id.importo_bilancio);
        }
    }
}
