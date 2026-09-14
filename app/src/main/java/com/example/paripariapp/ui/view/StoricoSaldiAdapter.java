package com.example.paripariapp.ui.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.databinding.ItemStoricoSaldoBinding;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

/**
 * Adapter per la visualizzazione dello storico dei saldi e pareggi effettuati.
 * Differenzia visivamente pagamenti ricevuti (verde) e inviati (rosso).
 */
public class StoricoSaldiAdapter extends ListAdapter<StoricoSaldiAdapter.StoricoItem, StoricoSaldiAdapter.ViewHolder> {

    public static class StoricoItem {
        private final Spesa spesa;
        private final String nomeScheda;
        private final String testoDescrizione;
        private final boolean isRicevuto;
        private final boolean showGroupTitle;

        public StoricoItem(Spesa spesa, String nomeScheda, String testoDescrizione, boolean isRicevuto, boolean showGroupTitle) {
            this.spesa = spesa;
            this.nomeScheda = nomeScheda;
            this.testoDescrizione = testoDescrizione;
            this.isRicevuto = isRicevuto;
            this.showGroupTitle = showGroupTitle;
        }

        public Spesa getSpesa() {
            return spesa;
        }

        public String getNomeScheda() {
            return nomeScheda;
        }

        public String getTestoDescrizione() {
            return testoDescrizione;
        }

        public boolean isRicevuto() {
            return isRicevuto;
        }

        public boolean isShowGroupTitle() {
            return showGroupTitle;
        }
    }

    private static final DiffUtil.ItemCallback<StoricoItem> DIFF_CALLBACK = new DiffUtil.ItemCallback<StoricoItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull StoricoItem oldItem, @NonNull StoricoItem newItem) {
            return Objects.equals(oldItem.getSpesa().getId(), newItem.getSpesa().getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull StoricoItem oldItem, @NonNull StoricoItem newItem) {
            return Objects.equals(oldItem.getTestoDescrizione(), newItem.getTestoDescrizione()) &&
                    Double.compare(oldItem.getSpesa().getImporto(), newItem.getSpesa().getImporto()) == 0 &&
                    oldItem.getSpesa().getDataSpesa() == newItem.getSpesa().getDataSpesa() &&
                    oldItem.isRicevuto() == newItem.isRicevuto() &&
                    oldItem.isShowGroupTitle() == newItem.isShowGroupTitle();
        }
    };

    public interface OnItemClickListener {
        void onItemClick(StoricoItem item);
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
<<<<<<< HEAD
        StoricoItem item = getItem(position);
        Spesa spesa = item.getSpesa();
        Context ctx = holder.itemView.getContext();

        if (item.isShowGroupTitle()) {
            holder.binding.tvGruppoSaldo.setVisibility(View.VISIBLE);
            holder.binding.tvGruppoSaldo.setText(item.getNomeScheda());
        } else {
            holder.binding.tvGruppoSaldo.setVisibility(View.GONE);
        }

        holder.binding.tvTitoloSaldo.setText(item.getTestoDescrizione());
        holder.binding.tvDataSaldo.setText(dateFormat.format(new Date(spesa.getDataSpesa())));
        holder.binding.tvImportoSaldo.setText(String.format(Locale.getDefault(), "%.2f %s", spesa.getImporto(), spesa.getValuta()));

        if (item.isRicevuto()) {
            int greenColor = ctx.getResources().getColor(R.color.credit_green, null);
            holder.binding.ivIconaSaldo.setColorFilter(greenColor);
            holder.binding.tvImportoSaldo.setTextColor(greenColor);
            holder.binding.tvBadgeTipoSaldo.setText(R.string.badge_ricevuto);
            holder.binding.tvBadgeTipoSaldo.setTextColor(greenColor);
        } else {
            int redColor = ctx.getResources().getColor(android.R.color.holo_red_dark, null);
            holder.binding.ivIconaSaldo.setColorFilter(redColor);
            holder.binding.tvImportoSaldo.setTextColor(redColor);
            holder.binding.tvBadgeTipoSaldo.setText(R.string.badge_inviato);
            holder.binding.tvBadgeTipoSaldo.setTextColor(redColor);
        }
=======
        Spesa item = getItem(position);
        holder.binding.tvTitoloSaldo.setText(item.getTitolo());
        holder.binding.tvDataSaldo.setText(dateFormat.format(new Date(item.getDataSpesa())));
        holder.binding.tvImportoSaldo.setText(com.example.paripariapp.util.ImportoUtil.formatta(item.getImporto(), item.getValuta()));
>>>>>>> a35494efb78696866faa77aeae4e6b31d79d114c

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
