package com.example.paripariapp.ui.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.data.model.SpesaConDettagli;
import com.example.paripariapp.data.model.SpesaPartecipante;
import com.example.paripariapp.data.repository.CurrencyRepository;
import com.example.paripariapp.databinding.BottomSheetDettaglioSpesaBinding;
import com.example.paripariapp.databinding.ItemQuotaPartecipanteBinding;
import com.example.paripariapp.util.CategoriaUtil;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DettaglioSpesaBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetDettaglioSpesaBinding binding;
    private SpesaConDettagli spesaConDettagli;
    private String valutaGruppo;
    private List<Partecipante> partecipantiCache;
    private List<SpesaPartecipante> quoteCache;

    public static DettaglioSpesaBottomSheet newInstance(
            SpesaConDettagli item,
            String valutaGruppo,
            List<Partecipante> partecipanti,
            List<SpesaPartecipante> quote
    ) {
        DettaglioSpesaBottomSheet sheet = new DettaglioSpesaBottomSheet();
        sheet.spesaConDettagli = item;
        sheet.valutaGruppo = valutaGruppo;
        sheet.partecipantiCache = partecipanti;
        sheet.quoteCache = quote;
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetDettaglioSpesaBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (spesaConDettagli == null || spesaConDettagli.getSpesa() == null) {
            dismiss();
            return;
        }

        Spesa spesa = spesaConDettagli.getSpesa();
        String valutaSpesa = spesa.getValuta();
        String gruppoVal = valutaGruppo != null ? valutaGruppo : "EUR";

        // Emoji & Categoria
        String categoria = spesa.getCategoria() != null ? spesa.getCategoria() : getString(R.string.cat_altro);
        String emoji = CategoriaUtil.getEmojiForCategoria(categoria, spesa.getTitolo());
        binding.tvCategoriaDettaglio.setText(emoji + " " + categoria);

        // Titolo
        binding.tvTitoloDettaglio.setText(spesa.getTitolo());

        // Data
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault());
        String dataFmt = formatter.format(
                Instant.ofEpochMilli(spesa.getDataSpesa()).atZone(ZoneId.systemDefault())
        );
        binding.tvDataDettaglio.setText(dataFmt);

        // Importo Totale
        binding.tvImportoTotaleDettaglio.setText(
                getString(R.string.spesa_formato_importo, spesa.getImporto(), valutaSpesa)
        );

        // Controllo multivaluta
        if (!valutaSpesa.equalsIgnoreCase(gruppoVal)) {
            CurrencyRepository currencyRepo = CurrencyRepository.getInstance(requireContext().getApplicationContext());
            double rateSpesa = currencyRepo.getRate(valutaSpesa);
            double rateGruppo = currencyRepo.getRate(gruppoVal);
            double importoConvertito = (spesa.getImporto() / rateSpesa) * rateGruppo;

            binding.tvSecondariaValutaDettaglio.setVisibility(View.VISIBLE);
            binding.tvSecondariaValutaDettaglio.setText(
                    getString(R.string.spesa_formato_importo, importoConvertito, gruppoVal)
            );
        } else {
            binding.tvSecondariaValutaDettaglio.setVisibility(View.GONE);
        }

        // Pagato da (con soldi in ROSSO)
        String pagatoreNome = spesaConDettagli.getNomePagatore() != null ? spesaConDettagli.getNomePagatore() : getString(R.string.nome_sconosciuto);
        String testoPagatoDa = pagatoreNome + ": " + getString(R.string.spesa_formato_importo, spesa.getImporto(), valutaSpesa);

        android.text.SpannableString spannablePagatoDa = new android.text.SpannableString(testoPagatoDa);
        int colorRed = getResources().getColor(android.R.color.holo_red_dark, null);
        int startImporto = testoPagatoDa.lastIndexOf(":");
        if (startImporto != -1) {
            spannablePagatoDa.setSpan(
                    new android.text.style.ForegroundColorSpan(colorRed),
                    startImporto + 1,
                    testoPagatoDa.length(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        binding.tvPagatoDaDettaglio.setText(spannablePagatoDa);

        // Quote partecipanti
        setupQuoteRecycler(spesa, valutaSpesa, gruppoVal);
    }

    private void setupQuoteRecycler(Spesa spesa, String valutaSpesa, String gruppoVal) {
        List<SpesaPartecipante> quoteDellaSpesa = new ArrayList<>();
        if (quoteCache != null) {
            for (SpesaPartecipante q : quoteCache) {
                if (q.getSpesaId().equals(spesa.getId())) {
                    quoteDellaSpesa.add(q);
                }
            }
        }

        QuoteDettaglioAdapter adapter = new QuoteDettaglioAdapter(quoteDellaSpesa, partecipantiCache, valutaSpesa, gruppoVal);
        binding.recyclerQuoteDettaglio.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerQuoteDettaglio.setAdapter(adapter);
    }

    private static class QuoteDettaglioAdapter extends RecyclerView.Adapter<QuoteDettaglioAdapter.ViewHolder> {
        private final List<SpesaPartecipante> quote;
        private final List<Partecipante> partecipanti;
        private final String valutaSpesa;
        private final String gruppoVal;

        QuoteDettaglioAdapter(List<SpesaPartecipante> quote, List<Partecipante> partecipanti, String valutaSpesa, String gruppoVal) {
            this.quote = quote;
            this.partecipanti = partecipanti;
            this.valutaSpesa = valutaSpesa;
            this.gruppoVal = gruppoVal;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemQuotaPartecipanteBinding binding = ItemQuotaPartecipanteBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false
            );
            return new ViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SpesaPartecipante q = quote.get(position);
            String nome = "Membro";
            if (partecipanti != null) {
                for (Partecipante p : partecipanti) {
                    if (p.getId().equals(q.getPartecipanteId())) {
                        nome = p.getNome();
                        break;
                    }
                }
            }

            holder.binding.spuntaPartecipante.setVisibility(View.GONE);
            holder.binding.contenitoreQuota.setVisibility(View.GONE);
            holder.binding.tvQuotaEqua.setVisibility(View.VISIBLE);

            holder.binding.nomePartecipante.setText(nome);

            String importoStr = String.format(Locale.getDefault(), "%.2f %s", q.getQuota(), valutaSpesa);
            if (!valutaSpesa.equalsIgnoreCase(gruppoVal)) {
                CurrencyRepository currencyRepo = CurrencyRepository.getInstance(holder.itemView.getContext().getApplicationContext());
                double rateSpesa = currencyRepo.getRate(valutaSpesa);
                double rateGruppo = currencyRepo.getRate(gruppoVal);
                double importoConv = (q.getQuota() / rateSpesa) * rateGruppo;
                importoStr += String.format(Locale.getDefault(), " (≈ %.2f %s)", importoConv, gruppoVal);
            }
            holder.binding.tvQuotaEqua.setText(importoStr);
        }

        @Override
        public int getItemCount() {
            return quote.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final ItemQuotaPartecipanteBinding binding;

            ViewHolder(ItemQuotaPartecipanteBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
