package com.example.paripariapp.ui.view;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.data.model.SpesaPartecipante;
import com.example.paripariapp.data.model.SyncStatus;
import com.example.paripariapp.databinding.FragmentModificaSpesaBinding;
import com.example.paripariapp.ui.viewmodel.DettaglioSchedaViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Schermata per la modifica di una spesa esistente, gestione quote in percentuale (%) e pagamenti effettuati.
 */
public class ModificaSpesaFragment extends Fragment {

    private static final String ARG_SPESA_ID = "arg_spesa_id";
    private static final String ARG_SCHEDA_ID = "arg_scheda_id";
    private static final String ARG_VALUTA = "arg_valuta";

    private FragmentModificaSpesaBinding binding;
    private DettaglioSchedaViewModel viewModel;

    private String spesaId;
    private String schedaId;
    private String valuta;

    private Spesa spesaCorrente;
    private List<Partecipante> partecipanti = new ArrayList<>();
    private List<SpesaPartecipante> quoteEsistenti = new ArrayList<>();

    private final Map<String, CheckBox> checkMap = new HashMap<>();
    private final Map<String, EditText> quotaInputMap = new HashMap<>();
    private final Map<String, EditText> quotaPagataInputMap = new HashMap<>();
    private final Map<String, TextView> statoSaldoMap = new HashMap<>();
    private final Map<String, View> quotaContainerMap = new HashMap<>();

    private boolean isDataLoaded = false;
    private boolean isQuoteLoaded = false;
    private boolean isReadOnly = false;

    public static ModificaSpesaFragment newInstance(String spesaId, String schedaId, String valuta) {
        ModificaSpesaFragment fragment = new ModificaSpesaFragment();
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
            valuta = getArguments().getString(ARG_VALUTA);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentModificaSpesaBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(DettaglioSchedaViewModel.class);

        binding.toolbarModificaSpesa.setNavigationOnClickListener(v -> getParentFragmentManager().popBackStack());
        binding.campoValuta.setText(valuta != null ? valuta : getString(R.string.valuta_default));

        setupCategorieDropdown();
        setupSwitchDivisione();
        setupAzioneElimina();
        setupObservers();
        setupSalva();
    }

    private void setupCategorieDropdown() {
        String[] categorie = new String[]{
                getString(R.string.cat_cibo),
                getString(R.string.cat_trasporti),
                getString(R.string.cat_alloggio),
                getString(R.string.cat_svago),
                getString(R.string.cat_altro)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, categorie);
        binding.menuCategoria.setAdapter(adapter);
    }

    private void setupSwitchDivisione() {
        binding.interruttorePersonalizzata.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (View container : quotaContainerMap.values()) {
                if (container != null) {
                    container.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                }
            }
            ricalcolaStatiSaldo();
        });

        binding.campoImporto.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                ricalcolaStatiSaldo();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupAzioneElimina() {
        binding.azioneElimina.setOnClickListener(v -> {
            if (isReadOnly) {
                Toast.makeText(requireContext(), R.string.msg_spesa_non_modificabile_membro_assente, Toast.LENGTH_SHORT).show();
                return;
            }
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.dialog_titolo_elimina_spesa)
                    .setMessage(R.string.dialog_msg_elimina_spesa)
                    .setPositiveButton(R.string.btn_elimina, (dialog, which) -> {
                        viewModel.eliminaSpesa(spesaId, schedaId);
                        Toast.makeText(requireContext(), R.string.msg_spesa_eliminata, Toast.LENGTH_SHORT).show();
                        if (getParentFragmentManager() != null) {
                            getParentFragmentManager().popBackStack();
                        }
                    })
                    .setNegativeButton(R.string.btn_annulla, null)
                    .show();
        });
    }

    private void setupObservers() {
        viewModel.getPartecipanti(schedaId).observe(getViewLifecycleOwner(), lista -> {
            if (lista == null) return;
            this.partecipanti = lista;

            List<String> nomi = new ArrayList<>();
            for (Partecipante p : lista) {
                nomi.add(p.getNome());
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, nomi);
            binding.menuPagante.setAdapter(adapter);

            caricaDatiFormSePronto();
        });

        viewModel.getSpesaById(spesaId).observe(getViewLifecycleOwner(), spesa -> {
            if (spesa == null) return;
            this.spesaCorrente = spesa;
            caricaDatiFormSePronto();
        });

        viewModel.getQuoteBySpesa(spesaId).observe(getViewLifecycleOwner(), quote -> {
            if (quote == null) return;
            this.quoteEsistenti = quote;
            this.isQuoteLoaded = true;
            caricaDatiFormSePronto();
        });
    }

    private synchronized void caricaDatiFormSePronto() {
        if (isDataLoaded || spesaCorrente == null || partecipanti.isEmpty() || !isQuoteLoaded) {
            return;
        }
        isDataLoaded = true;

        binding.campoDescrizione.setText(spesaCorrente.getTitolo());
        binding.campoImporto.setText(String.format(Locale.US, "%.2f", spesaCorrente.getImporto()));
        binding.campoValuta.setText(spesaCorrente.getValuta() != null ? spesaCorrente.getValuta() : (valuta != null ? valuta : "EUR"));

        // Verifica presenza di partecipanti assenti
        java.util.Set<String> activeParticipantIds = new java.util.HashSet<>();
        for (Partecipante p : partecipanti) {
            activeParticipantIds.add(p.getId());
        }

        boolean haPartecipantiAssenti = false;
        if (spesaCorrente.getPagatoDaId() != null && !activeParticipantIds.contains(spesaCorrente.getPagatoDaId())) {
            haPartecipantiAssenti = true;
            binding.menuPagante.setText(getString(R.string.nome_sconosciuto), false);
        } else {
            for (Partecipante p : partecipanti) {
                if (p.getId().equals(spesaCorrente.getPagatoDaId())) {
                    binding.menuPagante.setText(p.getNome(), false);
                    break;
                }
            }
        }

        if (quoteEsistenti != null) {
            for (SpesaPartecipante q : quoteEsistenti) {
                if (q.getPartecipanteId() != null && !activeParticipantIds.contains(q.getPartecipanteId())) {
                    haPartecipantiAssenti = true;
                    break;
                }
            }
        }

        this.isReadOnly = haPartecipantiAssenti;

        // Categoria
        if (spesaCorrente.getCategoria() != null) {
            binding.menuCategoria.setText(spesaCorrente.getCategoria(), false);
        }

        popolaRighePartecipanti();

        if (isReadOnly) {
            applicaModalitaSolaLettura();
        }
    }

    private void applicaModalitaSolaLettura() {
        if (binding == null) return;

        binding.cardBannerReadonly.setVisibility(View.VISIBLE);
        Toast.makeText(requireContext(), R.string.msg_spesa_non_modificabile_membro_assente, Toast.LENGTH_LONG).show();

        binding.azioneSalva.setVisibility(View.GONE);
        binding.azioneElimina.setVisibility(View.GONE);

        binding.campoDescrizione.setEnabled(false);
        binding.campoImporto.setEnabled(false);
        binding.campoValuta.setEnabled(false);
        binding.menuPagante.setEnabled(false);
        binding.menuCategoria.setEnabled(false);
        binding.interruttorePersonalizzata.setEnabled(false);

        for (CheckBox cb : checkMap.values()) {
            if (cb != null) cb.setEnabled(false);
        }
        for (EditText et : quotaInputMap.values()) {
            if (et != null) et.setEnabled(false);
        }
        for (EditText et : quotaPagataInputMap.values()) {
            if (et != null) et.setEnabled(false);
        }
    }

    private void popolaRighePartecipanti() {
        binding.layoutElencoQuote.removeAllViews();
        checkMap.clear();
        quotaInputMap.clear();
        quotaPagataInputMap.clear();
        statoSaldoMap.clear();
        quotaContainerMap.clear();

        Map<String, SpesaPartecipante> quoteMap = new HashMap<>();
        for (SpesaPartecipante q : quoteEsistenti) {
            quoteMap.put(q.getPartecipanteId(), q);
        }

        double importoTotale = spesaCorrente != null ? spesaCorrente.getImporto() : 0.0;
        boolean haQuotePersonalizzate = false;

        if (importoTotale > 0 && quoteEsistenti.size() > 1) {
            double quotaStandard = importoTotale / quoteEsistenti.size();
            for (SpesaPartecipante q : quoteEsistenti) {
                if (Math.abs(q.getQuota() - quotaStandard) > 0.05) {
                    haQuotePersonalizzate = true;
                    break;
                }
            }
        }

        binding.interruttorePersonalizzata.setChecked(haQuotePersonalizzate);

        for (Partecipante p : partecipanti) {
            View row = getLayoutInflater().inflate(R.layout.item_quota_partecipante_modifica, binding.layoutElencoQuote, false);
            CheckBox cb = row.findViewById(R.id.spunta_partecipante);
            TextView tvNome = row.findViewById(R.id.nome_partecipante);
            EditText etQuota = row.findViewById(R.id.campo_quota);
            EditText etQuotaPagata = row.findViewById(R.id.campo_quota_pagata);
            TextView tvSaldo = row.findViewById(R.id.tv_stato_saldo);
            View quotaContainer = row.findViewById(R.id.contenitore_quota);

            tvNome.setText(p.getNome());

            SpesaPartecipante q = quoteMap.get(p.getId());
            boolean isIncluso = (q != null && q.getQuota() >= 0);
            cb.setChecked(isIncluso || quoteEsistenti.isEmpty());

            quotaContainer.setVisibility(binding.interruttorePersonalizzata.isChecked() ? View.VISIBLE : View.GONE);

            if (q != null && importoTotale > 0) {
                double percentuale = (q.getQuota() / importoTotale) * 100.0;
                etQuota.setText(String.format(Locale.US, "%.1f", percentuale));
                etQuotaPagata.setText(String.format(Locale.US, "%.2f", q.getQuotaPagata()));
            } else {
                double percDefault = 100.0 / Math.max(1, partecipanti.size());
                etQuota.setText(String.format(Locale.US, "%.1f", percDefault));
                etQuotaPagata.setText(String.format(Locale.US, "%.2f", 0.0));
            }

            checkMap.put(p.getId(), cb);
            quotaInputMap.put(p.getId(), etQuota);
            quotaPagataInputMap.put(p.getId(), etQuotaPagata);
            statoSaldoMap.put(p.getId(), tvSaldo);
            quotaContainerMap.put(p.getId(), quotaContainer);

            cb.setOnCheckedChangeListener((buttonView, isChecked) -> ricalcolaStatiSaldo());

            TextWatcher watcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    ricalcolaStatiSaldo();
                }

                @Override
                public void afterTextChanged(Editable s) {}
            };

            etQuota.addTextChangedListener(watcher);
            etQuotaPagata.addTextChangedListener(watcher);

            binding.layoutElencoQuote.addView(row);
        }

        ricalcolaStatiSaldo();
    }

    private void ricalcolaStatiSaldo() {
        double importoTotale = 0.0;
        try {
            String strImp = binding.campoImporto.getText() != null ? binding.campoImporto.getText().toString().replace(",", ".") : "0";
            importoTotale = Double.parseDouble(strImp);
        } catch (Exception ignored) {}

        List<Partecipante> inclusi = new ArrayList<>();
        for (Partecipante p : partecipanti) {
            CheckBox cb = checkMap.get(p.getId());
            if (cb != null && cb.isChecked()) {
                inclusi.add(p);
            }
        }

        boolean personalizzata = binding.interruttorePersonalizzata.isChecked();
        double quotaEquaEuro = (!inclusi.isEmpty() && importoTotale > 0) ? (importoTotale / inclusi.size()) : 0.0;

        String valutaStr = valuta != null ? valuta : "EUR";

        for (Partecipante p : partecipanti) {
            CheckBox cb = checkMap.get(p.getId());
            TextView tvSaldo = statoSaldoMap.get(p.getId());
            EditText etQuota = quotaInputMap.get(p.getId());
            EditText etQuotaPagata = quotaPagataInputMap.get(p.getId());

            if (tvSaldo == null) continue;

            if (cb == null || !cb.isChecked()) {
                tvSaldo.setVisibility(View.GONE);
                continue;
            }

            tvSaldo.setVisibility(View.VISIBLE);

            double quotaAssegnataEuro = quotaEquaEuro;
            if (personalizzata) {
                try {
                    String strPerc = etQuota != null && etQuota.getText() != null ? etQuota.getText().toString().replace(",", ".") : "0";
                    double perc = Double.parseDouble(strPerc);
                    quotaAssegnataEuro = importoTotale * (perc / 100.0);
                } catch (Exception ignored) {}
            }

            double quotaPagataEuro = 0.0;
            try {
                String strQP = etQuotaPagata != null && etQuotaPagata.getText() != null ? etQuotaPagata.getText().toString().replace(",", ".") : "0";
                quotaPagataEuro = Double.parseDouble(strQP);
            } catch (Exception ignored) {}

            double differenza = quotaAssegnataEuro - quotaPagataEuro;

            if (Math.abs(differenza) < 0.01) {
                tvSaldo.setText(R.string.spesa_saldo_saldato);
                tvSaldo.setTextColor(requireContext().getColor(android.R.color.holo_green_dark));
            } else if (differenza > 0.01) {
                tvSaldo.setText(getString(R.string.spesa_saldo_da_pagare, differenza, valutaStr));
                tvSaldo.setTextColor(requireContext().getColor(android.R.color.holo_orange_dark));
            } else {
                tvSaldo.setText(getString(R.string.spesa_saldo_rimborso, -differenza, valutaStr));
                tvSaldo.setTextColor(requireContext().getColor(android.R.color.holo_blue_dark));
            }
        }
    }

    private void setupSalva() {
        binding.azioneSalva.setOnClickListener(v -> {
            if (isReadOnly) {
                Toast.makeText(requireContext(), R.string.msg_spesa_non_modificabile_membro_assente, Toast.LENGTH_SHORT).show();
                return;
            }
            String titolo = binding.campoDescrizione.getText() != null
                    ? binding.campoDescrizione.getText().toString().trim() : "";
            String importoStr = binding.campoImporto.getText() != null
                    ? binding.campoImporto.getText().toString().trim().replace(",", ".") : "";

            if (titolo.isEmpty()) {
                binding.campoDescrizione.setError(getString(R.string.error_descrizione_spesa));
                return;
            }

            double importo;
            try {
                importo = Double.parseDouble(importoStr);
                if (importo <= 0) throw new NumberFormatException();
            } catch (Exception e) {
                binding.campoImporto.setError(getString(R.string.error_importo_spesa));
                return;
            }

            String nomePagatore = binding.menuPagante.getText().toString();
            String pagatoreId = null;
            for (Partecipante p : partecipanti) {
                if (p.getNome().equals(nomePagatore)) {
                    pagatoreId = p.getId();
                    break;
                }
            }
            if (pagatoreId == null && !partecipanti.isEmpty()) {
                pagatoreId = partecipanti.get(0).getId();
            }

            String categoria = binding.menuCategoria.getText().toString();
            List<SpesaPartecipante> nuoveQuote = new ArrayList<>();

            List<Partecipante> partecipantiInclusi = new ArrayList<>();
            for (Partecipante p : partecipanti) {
                CheckBox cb = checkMap.get(p.getId());
                if (cb != null && cb.isChecked()) {
                    partecipantiInclusi.add(p);
                }
            }

            if (partecipantiInclusi.isEmpty()) {
                Toast.makeText(requireContext(), R.string.spesa_errore_nessun_partecipante, Toast.LENGTH_SHORT).show();
                return;
            }

            if (!binding.interruttorePersonalizzata.isChecked()) {
                // Divisione Equa
                double quotaSingolaEuro = Math.round((importo / partecipantiInclusi.size()) * 100.0) / 100.0;
                for (Partecipante p : partecipanti) {
                    CheckBox cb = checkMap.get(p.getId());
                    boolean isIncluso = (cb != null && cb.isChecked());

                    double qPagata = 0.0;
                    EditText etQP = quotaPagataInputMap.get(p.getId());
                    try {
                        if (etQP != null && etQP.getText() != null) {
                            qPagata = Double.parseDouble(etQP.getText().toString().replace(",", "."));
                        }
                    } catch (Exception ignored) {}

                    double qVal = isIncluso ? quotaSingolaEuro : 0.0;
                    nuoveQuote.add(new SpesaPartecipante(spesaId, p.getId(), qVal, qPagata, SyncStatus.PENDING_UPDATE));
                }
            } else {
                // Divisione Personalizzata in Percentuale (%)
                Map<String, Double> percentualiInserite = new HashMap<>();
                double sommaPercentuali = 0.0;

                for (Partecipante p : partecipantiInclusi) {
                    EditText et = quotaInputMap.get(p.getId());
                    double perc = 0.0;
                    try {
                        if (et != null && et.getText() != null && !et.getText().toString().trim().isEmpty()) {
                            perc = Double.parseDouble(et.getText().toString().replace(",", "."));
                        }
                    } catch (Exception ignored) {}

                    percentualiInserite.put(p.getId(), perc);
                    sommaPercentuali += perc;
                }

                if (Math.abs(sommaPercentuali - 100.0) > 0.05) {
                    Toast.makeText(requireContext(),
                            getString(R.string.spesa_errore_somma_percentuali, sommaPercentuali),
                            Toast.LENGTH_LONG).show();
                    return;
                }

                // Conversione percentuale -> importo in valuta
                double totaleAssegnato = 0.0;
                for (int i = 0; i < partecipantiInclusi.size(); i++) {
                    Partecipante p = partecipantiInclusi.get(i);
                    Double percObj = percentualiInserite.get(p.getId());
                    double perc = percObj != null ? percObj : 0.0;
                    double quotaEuro;

                    if (i == partecipantiInclusi.size() - 1) {
                        quotaEuro = Math.round((importo - totaleAssegnato) * 100.0) / 100.0;
                    } else {
                        quotaEuro = Math.round((importo * (perc / 100.0)) * 100.0) / 100.0;
                        totaleAssegnato += quotaEuro;
                    }

                    double qPagata = 0.0;
                    EditText etQP = quotaPagataInputMap.get(p.getId());
                    try {
                        if (etQP != null && etQP.getText() != null) {
                            qPagata = Double.parseDouble(etQP.getText().toString().replace(",", "."));
                        }
                    } catch (Exception ignored) {}

                    nuoveQuote.add(new SpesaPartecipante(spesaId, p.getId(), quotaEuro, qPagata, SyncStatus.PENDING_UPDATE));
                }

                // Aggiungi anche gli eslcusi con quota 0 per mantenere la registrazione storica
                for (Partecipante p : partecipanti) {
                    CheckBox cb = checkMap.get(p.getId());
                    if (cb == null || !cb.isChecked()) {
                        double qPagata = 0.0;
                        EditText etQP = quotaPagataInputMap.get(p.getId());
                        try {
                            if (etQP != null && etQP.getText() != null) {
                                qPagata = Double.parseDouble(etQP.getText().toString().replace(",", "."));
                            }
                        } catch (Exception ignored) {}

                        nuoveQuote.add(new SpesaPartecipante(spesaId, p.getId(), 0.0, qPagata, SyncStatus.PENDING_UPDATE));
                    }
                }
            }

            Spesa spesaAggiornata = new Spesa(
                    spesaId,
                    schedaId,
                    titolo,
                    importo,
                    valuta != null ? valuta : getString(R.string.valuta_default),
                    spesaCorrente != null ? spesaCorrente.getDataSpesa() : System.currentTimeMillis(),
                    categoria,
                    pagatoreId,
                    spesaCorrente != null ? spesaCorrente.getScontrinoUrl() : null,
                    SyncStatus.PENDING_UPDATE
            );

            viewModel.aggiornaSpesaConQuote(spesaAggiornata, nuoveQuote);
            Toast.makeText(requireContext(), R.string.msg_spesa_aggiornata, Toast.LENGTH_SHORT).show();

            if (getParentFragmentManager() != null) {
                getParentFragmentManager().popBackStack();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
