package com.example.paripariapp.ui.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.data.model.SyncStatus;
import com.example.paripariapp.databinding.FragmentNuovaSpesaBinding;
import com.example.paripariapp.ui.viewmodel.DettaglioSchedaViewModel;
import com.example.paripariapp.ui.viewmodel.SpeseViewModel;


import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Schermata a schermo intero (Fragment) per la creazione di una nuova spesa o rimborso (Tricount-style).
 */
public class NuovaSpesaFragment extends BaseSpesaFragment {

    private FragmentNuovaSpesaBinding binding;
    private DettaglioSchedaViewModel viewModel;
    private boolean isRimborso = false;
    private android.net.Uri scontrinoUri = null;
    private android.net.Uri cameraTempUri = null;
    private java.io.File cameraTempFile = null;
    private com.example.paripariapp.data.model.ScontrinoDigitale scontrinoDigitaleCorrente = null;

    private final androidx.activity.result.ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest> pickMediaLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    gestisciImmagineScontrino(uri);
                }
            });

    private final androidx.activity.result.ActivityResultLauncher<android.net.Uri> takePictureLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.TakePicture(), success -> {
                if (Boolean.TRUE.equals(success) && cameraTempUri != null) {
                    gestisciImmagineScontrino(cameraTempUri);
                }
            });


    public static NuovaSpesaFragment newInstance(String schedaId, String valuta) {
        NuovaSpesaFragment fragment = new NuovaSpesaFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SCHEDA_ID, schedaId);
        args.putString(ARG_VALUTA, valuta);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setEnterTransition(new com.google.android.material.transition.MaterialSharedAxis(com.google.android.material.transition.MaterialSharedAxis.Z, true));
        setReturnTransition(new com.google.android.material.transition.MaterialSharedAxis(com.google.android.material.transition.MaterialSharedAxis.Z, false));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentNuovaSpesaBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(DettaglioSchedaViewModel.class);

        com.example.paripariapp.util.KeyboardUtil.showKeyboard(binding.campoDescrizione);

        initCommonViews(
                binding.toolbarNuovaSpesa,
                binding.campoDescrizione,
                binding.campoImporto,
                binding.campoValuta,
                binding.campoData,
                binding.menuPagante,
                binding.menuCategoria,
                binding.toggleGruppoDivisione,
                binding.layoutElencoQuote,
                binding.azioneSalva
        );

        setupScontrinoListeners();

        binding.toggleGruppoDivisione.check(R.id.btn_divisione_equa);

        binding.toggleTipoOperazione.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            isRimborso = (checkedId == R.id.btn_tipo_rimborso);
            if (isRimborso) {
                binding.titoloSpesa.setText(R.string.titolo_nuovo_rimborso);
                binding.azioneSalva.setText(R.string.btn_salva_rimborso);
                binding.contenitoreDestinatario.setVisibility(View.VISIBLE);
                binding.contenitoreCategoria.setVisibility(View.GONE);
                binding.sezioneDivisione.setVisibility(View.GONE);
                binding.sezioneScontrino.setVisibility(View.GONE);
                binding.contenitoreDescrizione.setHint(getString(R.string.label_descrizione_opzionale));
                if ("Rimborso".equals(String.valueOf(binding.campoDescrizione.getText()))) {
                    binding.campoDescrizione.setText("");
                }
                aggiornaDropdownDestinatario();
            } else {
                binding.titoloSpesa.setText(R.string.spesa_titolo);
                binding.azioneSalva.setText(R.string.btn_salva_spesa);
                binding.contenitoreDestinatario.setVisibility(View.GONE);
                binding.contenitoreCategoria.setVisibility(View.VISIBLE);
                binding.sezioneDivisione.setVisibility(View.VISIBLE);
                binding.sezioneScontrino.setVisibility(View.VISIBLE);
                binding.contenitoreDescrizione.setHint(getString(R.string.hint_descrizione_spesa));
            }
        });

        setupObserverPartecipanti();
        setupSalva();
        setupWindowInsets();
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int navBarBottom = systemBars.bottom;
            float density = getResources().getDisplayMetrics().density;

            int basePadding = (int) (36 * density);
            binding.scrollNuovaSpesa.setPadding(
                    binding.scrollNuovaSpesa.getPaddingLeft(),
                    binding.scrollNuovaSpesa.getPaddingTop(),
                    binding.scrollNuovaSpesa.getPaddingRight(),
                    basePadding + navBarBottom
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.getRoot());
    }

    private void setupScontrinoListeners() {
        binding.btnAllegaFotoScontrino.setOnClickListener(v -> {
            pickMediaLauncher.launch(new androidx.activity.result.PickVisualMediaRequest.Builder()
                    .setMediaType(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });

        binding.btnFotocameraScontrino.setOnClickListener(v -> avviaFotocameraConUri());

        binding.btnRimuoviScontrino.setOnClickListener(v -> {
            scontrinoUri = null;
            scontrinoDigitaleCorrente = null;
            eliminaFotoTemporanea();
            if (binding != null) {
                binding.ivAnteprimaScontrino.setImageDrawable(null);
                binding.cardAnteprimaScontrino.setVisibility(View.GONE);
                binding.layoutOcrProgress.setVisibility(View.GONE);
            }
        });

        binding.cardAnteprimaScontrino.setOnClickListener(v -> mostraDettaglioVociScontrino());
    }

    private void avviaFotocameraConUri() {
        try {
            cameraTempFile = java.io.File.createTempFile("scontrino_", ".jpg", requireContext().getCacheDir());
            cameraTempUri = androidx.core.content.FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    cameraTempFile
            );
            takePictureLauncher.launch(cameraTempUri);
        } catch (Exception e) {
            com.example.paripariapp.util.AppSnackbar.show(binding.getRoot(), R.string.errore_avvio_fotocamera);
        }
    }

    private void gestisciImmagineScontrino(android.net.Uri uri) {
        scontrinoUri = uri;
        binding.cardAnteprimaScontrino.setVisibility(View.VISIBLE);
        android.graphics.Bitmap miniatura = com.example.paripariapp.util.ScontrinoOcrUtil.caricaMiniatura(requireContext(), uri, 256);
        if (miniatura != null) {
            binding.ivAnteprimaScontrino.setImageBitmap(miniatura);
        } else {
            binding.ivAnteprimaScontrino.setImageURI(uri);
        }
        binding.layoutOcrProgress.setVisibility(View.VISIBLE);
        binding.tvOcrStatus.setText(R.string.ocr_in_corso);

        com.example.paripariapp.util.ScontrinoOcrUtil.analizzaScontrino(requireContext(), uri, new com.example.paripariapp.util.ScontrinoOcrUtil.OcrCallback() {
            @Override
            public void onSuccess(com.example.paripariapp.util.ScontrinoOcrUtil.RisultatoOcr risultato) {
                if (!isAdded() || binding == null) return;
                binding.layoutOcrProgress.setVisibility(View.GONE);

                scontrinoDigitaleCorrente = risultato.scontrinoDigitale;

                if (risultato.importo != null && risultato.importo > 0.0) {
                    binding.campoImporto.setText(String.format(java.util.Locale.US, "%.2f", risultato.importo));
                }

                if (risultato.dataTimestamp != null) {
                    dataSelezionataTimestamp = risultato.dataTimestamp;
                    java.text.DateFormat dateFormat = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, java.util.Locale.getDefault());
                    binding.campoData.setText(dateFormat.format(new java.util.Date(risultato.dataTimestamp)));
                }

                if (risultato.esercenteSuggerito != null && !risultato.esercenteSuggerito.isEmpty()) {
                    String descAttuale = binding.campoDescrizione.getText() != null ? binding.campoDescrizione.getText().toString().trim() : "";
                    if (descAttuale.isEmpty()) {
                        binding.campoDescrizione.setText(risultato.esercenteSuggerito);
                    }
                }

                aggiornaAnteprimaScontrinoDigitale();
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded() || binding == null) return;
                binding.layoutOcrProgress.setVisibility(View.GONE);
                binding.tvOcrStatus.setText(R.string.scontrino_allegato);
            }
        });
    }

    private void eliminaFotoTemporanea() {
        if (cameraTempFile != null && cameraTempFile.exists()) {
            try {
                cameraTempFile.delete();
            } catch (Exception ignored) {}
            cameraTempFile = null;
        }
    }

    private void aggiornaAnteprimaScontrinoDigitale() {
        if (binding == null) return;
        if (scontrinoDigitaleCorrente == null) {
            binding.tvOcrStatus.setText(R.string.scontrino_allegato);
            return;
        }

        int numVoci = scontrinoDigitaleCorrente.getVoci().size();
        String val = getValutaEffettiva();
        String totaleStr = scontrinoDigitaleCorrente.getTotale() != null
                ? String.format(java.util.Locale.US, "%.2f %s", scontrinoDigitaleCorrente.getTotale(), val) : "";

        StringBuilder info = new StringBuilder();
        info.append(getString(R.string.ocr_voci_digitalizzate, numVoci));
        if (!totaleStr.isEmpty()) {
            info.append(" ").append(getString(R.string.ocr_tot_label, totaleStr));
        }
        if (scontrinoDigitaleCorrente.isQuadrato()) {
            info.append(" ").append(getString(R.string.ocr_verificato));
        } else if (scontrinoDigitaleCorrente.getDiscrepanza() != 0.0) {
            info.append(" ").append(getString(R.string.ocr_discrepanza_formato, scontrinoDigitaleCorrente.getDiscrepanza(), val));
        }

        binding.tvOcrStatus.setText(info.toString());
        com.example.paripariapp.util.AppSnackbar.show(binding.getRoot(), getString(R.string.ocr_scontrino_digitalizzato_toast, numVoci));
    }

    private void mostraDettaglioVociScontrino() {
        if (scontrinoDigitaleCorrente == null || !isAdded()) return;

        String val = getValutaEffettiva();
        StringBuilder sb = new StringBuilder();
        if (scontrinoDigitaleCorrente.getEsercente() != null) {
            sb.append(getString(R.string.ocr_dettaglio_esercente, scontrinoDigitaleCorrente.getEsercente()));
        }
        if (scontrinoDigitaleCorrente.getDataFormatted() != null && !scontrinoDigitaleCorrente.getDataFormatted().isEmpty()) {
            sb.append(getString(R.string.ocr_dettaglio_data, scontrinoDigitaleCorrente.getDataFormatted()));
        }
        sb.append("----------------------------\n");

        if (scontrinoDigitaleCorrente.getVoci().isEmpty()) {
            sb.append(getString(R.string.ocr_nessuna_voce));
        } else {
            for (com.example.paripariapp.data.model.ScontrinoDigitale.VoceScontrino v : scontrinoDigitaleCorrente.getVoci()) {
                String prefix = v.getQuantita() > 1 ? String.format(java.util.Locale.US, "%.0fx ", v.getQuantita()) : "";
                sb.append(prefix).append(v.getDescrizione())
                        .append(" : ")
                        .append(String.format(java.util.Locale.US, "%.2f %s", v.getPrezzoTotale(), val))
                        .append("\n");
            }
        }
        sb.append("----------------------------\n");
        if (scontrinoDigitaleCorrente.getTotale() != null) {
            sb.append(getString(R.string.ocr_totale_rilevato, scontrinoDigitaleCorrente.getTotale(), val));
        }
        sb.append(getString(R.string.ocr_somma_voci, scontrinoDigitaleCorrente.calcolaSommaVoci(), val));
        if (scontrinoDigitaleCorrente.isQuadrato()) {
            sb.append(getString(R.string.ocr_quadratura_perfetta));
        } else {
            sb.append(getString(R.string.ocr_discrepanza_info, scontrinoDigitaleCorrente.getDiscrepanza(), val));
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_titolo_scontrino_digitalizzato)
                .setMessage(sb.toString())
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void aggiornaDropdownDestinatario() {
        if (partecipanti == null || !isAdded()) return;
        String paganteSelezionato = binding.menuPagante.getText() != null ? binding.menuPagante.getText().toString() : "";
        List<String> destinatariDisponibili = new ArrayList<>();
        for (Partecipante p : partecipanti) {
            if (p.isAttivo() && !p.getNome().equalsIgnoreCase(paganteSelezionato)) {
                destinatariDisponibili.add(p.getNome());
            }
        }
        ArrayAdapter<String> destAdapter = SpesaUiHelper.creaDropdownAdapter(requireContext(), destinatariDisponibili);
        binding.menuDestinatario.setAdapter(destAdapter);

        String destinatarioAttuale = binding.menuDestinatario.getText() != null ? binding.menuDestinatario.getText().toString() : "";
        if (destinatarioAttuale.equalsIgnoreCase(paganteSelezionato) || (!destinatariDisponibili.isEmpty() && !destinatariDisponibili.contains(destinatarioAttuale))) {
            if (!destinatariDisponibili.isEmpty()) {
                binding.menuDestinatario.setText(destinatariDisponibili.get(0), false);
            } else {
                binding.menuDestinatario.setText("", false);
            }
        }
    }

    private void setupObserverPartecipanti() {
        viewModel.getPartecipanti(schedaId).observe(getViewLifecycleOwner(), lista -> {
            if (lista == null) return;
            List<Partecipante> attivi = new ArrayList<>();
            for (Partecipante p : lista) {
                if (p.isAttivo()) {
                    attivi.add(p);
                }
            }
            this.partecipanti = attivi;

            final List<String> nomi = new ArrayList<>();
            for (Partecipante p : attivi) {
                nomi.add(p.getNome());
            }
            ArrayAdapter<String> adapter = SpesaUiHelper.creaDropdownAdapter(requireContext(), nomi);
            binding.menuPagante.setAdapter(adapter);

            String defaultPagatoreNome = null;
            com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            for (Partecipante p : attivi) {
                if (Partecipante.isCurrentUserParticipant(p, currentUser)) {
                    defaultPagatoreNome = p.getNome();
                    break;
                }
            }
            if (defaultPagatoreNome == null && !nomi.isEmpty()) {
                defaultPagatoreNome = nomi.get(0);
            }

            if (defaultPagatoreNome != null && (binding.menuPagante.getText() == null || binding.menuPagante.getText().toString().isEmpty())) {
                binding.menuPagante.setText(defaultPagatoreNome, false);
            }

            aggiornaDropdownDestinatario();

            binding.menuPagante.setOnItemClickListener((parent, v, position, id) -> {
                aggiornaDropdownDestinatario();
            });

            binding.menuPagante.setOnClickListener(v -> binding.menuPagante.showDropDown());
            binding.menuDestinatario.setOnClickListener(v -> binding.menuDestinatario.showDropDown());

            popolaRighePartecipantiComuni(lista, null, null, 0.0);
            cambiaTipoDivisione(tipoDivisione);
        });
    }

    private void setupSalva() {
        binding.azioneSalva.setOnClickListener(v -> {
            double importo = getImportoTotale();
            if (importo <= 0) {
                binding.campoImporto.setError(getString(R.string.error_importo_spesa));
                return;
            }

            if (isRimborso) {
                String paganteNome = binding.menuPagante.getText() != null ? binding.menuPagante.getText().toString() : "";
                String destinatarioNome = binding.menuDestinatario.getText() != null ? binding.menuDestinatario.getText().toString() : "";

                if (paganteNome.equals(destinatarioNome)) {
                    binding.contenitoreDestinatario.setError(getString(R.string.errore_mittente_destinatario_uguali));
                    return;
                } else {
                    binding.contenitoreDestinatario.setError(null);
                }

                String daId = null;
                String aId = null;
                for (Partecipante p : partecipanti) {
                    if (p.getNome().equalsIgnoreCase(paganteNome)) daId = p.getId();
                    if (p.getNome().equalsIgnoreCase(destinatarioNome)) aId = p.getId();
                }

                if (daId != null && aId != null) {
                    String desc = binding.campoDescrizione.getText() != null ? binding.campoDescrizione.getText().toString().trim() : null;
                    if (desc != null && desc.isEmpty()) desc = null;

                    SpeseViewModel speseVm = new ViewModelProvider(requireActivity()).get(SpeseViewModel.class);
                    speseVm.registraRimborso(schedaId, daId, paganteNome, aId, destinatarioNome, importo, getValutaEffettiva(), desc);
                    com.example.paripariapp.util.KeyboardUtil.hideKeyboard(requireView());
                    if (getParentFragmentManager() != null) {
                        getParentFragmentManager().popBackStack();
                    }
                }
            } else {
                String spesaId = UUID.randomUUID().toString();
                DatiFormValidi dati = validaEdEstraiDatiForm(spesaId, SyncStatus.PENDING_INSERT);
                if (dati == null) return;

                String scontrinoJson = scontrinoDigitaleCorrente != null ? scontrinoDigitaleCorrente.toJson() : null;
                salvaSpesaFinale(spesaId, dati, scontrinoJson);
            }
        });
    }

    private void salvaSpesaFinale(String spesaId, DatiFormValidi dati, @Nullable String scontrinoJson) {
        String valutaSpesa = getValutaEffettiva();
        String valutaGruppo = getValutaScheda();
        double tasso = SpesaUiHelper.calcolaTassoCambioAttuale(valutaSpesa, valutaGruppo, requireContext());

        Spesa spesa = new Spesa(
                spesaId,
                schedaId,
                dati.titolo,
                dati.importo,
                valutaSpesa,
                dati.timestamp,
                dati.categoria,
                dati.pagatoreId,
                null,
                SyncStatus.PENDING_INSERT
        );
        spesa.setScontrinoJson(scontrinoJson);
        spesa.setTassoCambio(tasso);

        viewModel.inserisciSpesaConQuote(spesa, dati.quoteCalcolate);
        eliminaFotoTemporanea();
        com.example.paripariapp.util.KeyboardUtil.hideKeyboard(requireView());

        if (getParentFragmentManager() != null) {
            getParentFragmentManager().popBackStack();
        }
    }

    @Override
    public void onDestroyView() {
        if (getView() != null) {
            com.example.paripariapp.util.KeyboardUtil.hideKeyboard(getView());
        }
        if (binding != null) {
            binding.ivAnteprimaScontrino.setImageDrawable(null);
        }
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        eliminaFotoTemporanea();
    }
}
