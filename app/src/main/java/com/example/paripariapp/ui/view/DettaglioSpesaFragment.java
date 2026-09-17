package com.example.paripariapp.ui.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.data.model.SpesaConDettagli;
import com.example.paripariapp.data.model.SpesaPartecipante;
import com.example.paripariapp.data.repository.CurrencyRepository;
import com.example.paripariapp.databinding.FragmentDettaglioSpesaBinding;
import com.example.paripariapp.databinding.ItemQuotaPartecipanteBinding;
import com.example.paripariapp.ui.viewmodel.DettaglioSchedaViewModel;
import com.example.paripariapp.util.CategoriaUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DettaglioSpesaFragment extends Fragment {

    private static final String ARG_SPESA_ID = "arg_spesa_id";
    private static final String ARG_SCHEDA_ID = "arg_scheda_id";
    private static final String ARG_VALUTA = "arg_valuta";

    private FragmentDettaglioSpesaBinding binding;
    private DettaglioSchedaViewModel viewModel;

    private String spesaId;
    private String schedaId;
    private String valutaGruppo;

    private List<Partecipante> partecipantiCache = new ArrayList<>();
    private List<SpesaPartecipante> quoteCache = new ArrayList<>();
    private SpesaConDettagli spesaCorrente;

    public static DettaglioSpesaFragment newInstance(String spesaId, String schedaId, String valuta) {
        DettaglioSpesaFragment fragment = new DettaglioSpesaFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SPESA_ID, spesaId);
        args.putString(ARG_SCHEDA_ID, schedaId);
        args.putString(ARG_VALUTA, valuta);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            spesaId = getArguments().getString(ARG_SPESA_ID);
            schedaId = getArguments().getString(ARG_SCHEDA_ID);
            valutaGruppo = getArguments().getString(ARG_VALUTA);
        }
        setEnterTransition(new com.google.android.material.transition.MaterialSharedAxis(com.google.android.material.transition.MaterialSharedAxis.Z, true));
        setReturnTransition(new com.google.android.material.transition.MaterialSharedAxis(com.google.android.material.transition.MaterialSharedAxis.Z, false));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDettaglioSpesaBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(DettaglioSchedaViewModel.class);

        binding.toolbarDettaglioSpesa.setNavigationOnClickListener(v -> {
            if (getParentFragmentManager() != null) {
                getParentFragmentManager().popBackStack();
            }
        });

        binding.toolbarDettaglioSpesa.setOnMenuItemClickListener(item -> {
            if (spesaCorrente == null || spesaCorrente.getSpesa() == null) return false;
            Spesa spesa = spesaCorrente.getSpesa();

            if (CategoriaUtil.isCategoriaSaldi(spesa.getCategoria())) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.badge_saldato_effettuato)
                        .setMessage(R.string.msg_spesa_saldo_non_modificabile)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                return true;
            }

            // Controlla se qualche partecipante ha lasciato il gruppo
            boolean haMembroAssente = SpesaUiHelper.haPartecipantiAssenti(spesa, quoteCache, partecipantiCache);

            if (haMembroAssente) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.dialog_titolo_spesa_non_modificabile)
                        .setMessage(R.string.dialog_msg_spesa_membro_assente)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                return true;
            }

            int id = item.getItemId();
            if (id == R.id.action_modifica_spesa) {
                ModificaSpesaFragment fragment = ModificaSpesaFragment.newInstance(
                        spesa.getId(),
                        schedaId,
                        valutaGruppo
                );
                getParentFragmentManager().beginTransaction()
                        .setCustomAnimations(
                                android.R.anim.fade_in,
                                android.R.anim.fade_out,
                                android.R.anim.fade_in,
                                android.R.anim.fade_out
                        )
                        .replace(R.id.dettaglio_container, fragment)
                        .addToBackStack(null)
                        .commit();
                return true;
            } else if (id == R.id.action_elimina_spesa) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.dialog_titolo_elimina_spesa)
                        .setMessage(R.string.dialog_msg_elimina_spesa)
                        .setPositiveButton(R.string.btn_elimina, (dialog, which) -> {
                            viewModel.eliminaSpesa(spesa.getId(), schedaId);
                            if (getParentFragmentManager() != null) {
                                getParentFragmentManager().popBackStack();
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return true;
            }
            return false;
        });

        setupObservers();
    }

    private void setupObservers() {
        if (schedaId != null) {
            viewModel.getPartecipanti(schedaId).observe(getViewLifecycleOwner(), partecipanti -> {
                partecipantiCache = partecipanti != null ? partecipanti : new ArrayList<>();
                aggiornaInterfaccia();
            });

            viewModel.getQuoteDellaScheda(schedaId).observe(getViewLifecycleOwner(), quote -> {
                quoteCache = quote != null ? quote : new ArrayList<>();
                aggiornaInterfaccia();
            });

            viewModel.getSpeseConDettagli(schedaId).observe(getViewLifecycleOwner(), spese -> {
                if (spese != null) {
                    for (SpesaConDettagli scd : spese) {
                        if (scd.getSpesa() != null && scd.getSpesa().getId().equals(spesaId)) {
                            spesaCorrente = scd;
                            break;
                        }
                    }
                    aggiornaInterfaccia();
                }
            });
        }
    }

    private void aggiornaInterfaccia() {
        if (binding == null || spesaCorrente == null || spesaCorrente.getSpesa() == null) return;

        Spesa spesa = spesaCorrente.getSpesa();
        String valutaSpesa = spesa.getValuta();
        String gruppoVal = valutaGruppo != null ? valutaGruppo : "EUR";

        // Emoji & Categoria
        String categoria = spesa.getCategoria() != null ? spesa.getCategoria() : getString(R.string.cat_altro);
        String emoji = CategoriaUtil.getEmojiForCategoria(categoria, spesa.getTitolo());
        binding.tvCategoriaDettaglio.setText(getString(R.string.format_categoria_con_emoji, emoji, categoria));

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

        // Spesa pagata da: + Nome
        String pagatoreNome = spesaCorrente.getNomePagatore() != null ? spesaCorrente.getNomePagatore() : getString(R.string.nome_sconosciuto);
        binding.tvPagatoDaDettaglio.setText(getString(R.string.format_spesa_pagata_da, pagatoreNome));

        // Scontrino Digitale / Ricevuta allegata
        mostraScontrinoDigitale(spesa, valutaSpesa);

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

    private void mostraScontrinoDigitale(Spesa spesa, String valutaSpesa) {
        if (binding == null) return;
        binding.containerVociScontrino.removeAllViews();

        String scontrinoJson = spesa.getScontrinoJson();
        if (scontrinoJson != null && !scontrinoJson.trim().isEmpty()) {
            com.example.paripariapp.data.model.ScontrinoDigitale scontrino =
                    com.example.paripariapp.data.model.ScontrinoDigitale.fromJson(scontrinoJson);

            if (scontrino != null) {
                binding.cardScontrinoDettaglio.setVisibility(View.VISIBLE);
                binding.ivScontrinoDettaglio.setVisibility(View.GONE);

                String esercente = scontrino.getEsercente() != null ? scontrino.getEsercente() : "Scontrino Digitale";
                binding.tvTitoloScontrinoDettaglio.setText(esercente);

                StringBuilder sottotitolo = new StringBuilder();
                if (scontrino.getDataFormatted() != null && !scontrino.getDataFormatted().isEmpty()) {
                    sottotitolo.append(scontrino.getDataFormatted());
                }
                if (scontrino.getMetodoPagamento() != null && !scontrino.getMetodoPagamento().isEmpty()) {
                    if (sottotitolo.length() > 0) sottotitolo.append(" • ");
                    sottotitolo.append(scontrino.getMetodoPagamento());
                }
                if (sottotitolo.length() == 0) sottotitolo.append("OCR On-Device locale");
                binding.tvEsercenteDataScontrino.setText(sottotitolo.toString());

                // Quadratura
                if (scontrino.isQuadrato()) {
                    binding.tvBadgeQuadratura.setVisibility(View.VISIBLE);
                    binding.tvBadgeQuadratura.setText("✓ Quadratura 100%");
                    binding.tvBadgeQuadratura.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.credit_green));
                } else if (scontrino.getDiscrepanza() != 0.0 && scontrino.getTotale() != null) {
                    binding.tvBadgeQuadratura.setVisibility(View.VISIBLE);
                    binding.tvBadgeQuadratura.setText(String.format(Locale.US, "Diff: %+.2f €", scontrino.getDiscrepanza()));
                    binding.tvBadgeQuadratura.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.warning_orange));
                } else {
                    binding.tvBadgeQuadratura.setVisibility(View.GONE);
                }

                // Voci
                List<com.example.paripariapp.data.model.ScontrinoDigitale.VoceScontrino> voci = scontrino.getVoci();
                if (voci != null && !voci.isEmpty()) {
                    for (com.example.paripariapp.data.model.ScontrinoDigitale.VoceScontrino v : voci) {
                        android.widget.LinearLayout row = new android.widget.LinearLayout(requireContext());
                        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                        row.setPadding(0, 8, 0, 8);

                        android.widget.TextView tvNome = new android.widget.TextView(requireContext());
                        android.widget.LinearLayout.LayoutParams pNome = new android.widget.LinearLayout.LayoutParams(
                                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                        );
                        tvNome.setLayoutParams(pNome);
                        tvNome.setTextSize(14);
                        tvNome.setTextColor(com.google.android.material.color.MaterialColors.getColor(tvNome, com.google.android.material.R.attr.colorOnSurface));
                        String desc = (v.getQuantita() > 1 ? String.format(Locale.US, "%.0fx ", v.getQuantita()) : "") + v.getDescrizione();
                        tvNome.setText(desc);

                        android.widget.TextView tvPrezzo = new android.widget.TextView(requireContext());
                        tvPrezzo.setTextSize(14);
                        tvPrezzo.setTypeface(null, android.graphics.Typeface.BOLD);
                        tvPrezzo.setTextColor(com.google.android.material.color.MaterialColors.getColor(tvPrezzo, com.google.android.material.R.attr.colorOnSurface));
                        tvPrezzo.setText(String.format(Locale.US, "%.2f %s", v.getPrezzoTotale(), scontrino.getValuta()));

                        row.addView(tvNome);
                        row.addView(tvPrezzo);
                        binding.containerVociScontrino.addView(row);
                    }
                }

                // Totale scontrino
                if (scontrino.getTotale() != null) {
                    binding.divisoreTotaleScontrino.setVisibility(View.VISIBLE);
                    binding.layoutTotaleScontrinoRow.setVisibility(View.VISIBLE);
                    binding.tvTotaleScontrinoValore.setText(
                            String.format(Locale.US, "%.2f %s", scontrino.getTotale(), scontrino.getValuta())
                    );
                } else {
                    binding.divisoreTotaleScontrino.setVisibility(View.GONE);
                    binding.layoutTotaleScontrinoRow.setVisibility(View.GONE);
                }
                return;
            }
        }

        // Fallback per vecchie foto remote (legacy http/https)
        String url = spesa.getScontrinoUrl();
        if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
            binding.cardScontrinoDettaglio.setVisibility(View.VISIBLE);
            binding.tvTitoloScontrinoDettaglio.setText(R.string.scontrino_allegato);
            binding.tvEsercenteDataScontrino.setText("Immagine cloud legacy");
            binding.tvBadgeQuadratura.setVisibility(View.GONE);
            binding.divisoreTotaleScontrino.setVisibility(View.GONE);
            binding.layoutTotaleScontrinoRow.setVisibility(View.GONE);
            binding.ivScontrinoDettaglio.setVisibility(View.VISIBLE);
            com.example.paripariapp.util.ImageLoaderUtil.caricaImmagine(url, binding.ivScontrinoDettaglio, R.drawable.ic_receipt);
        } else {
            binding.cardScontrinoDettaglio.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
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
