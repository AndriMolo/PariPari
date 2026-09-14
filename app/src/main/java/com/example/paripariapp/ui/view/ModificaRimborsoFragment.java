package com.example.paripariapp.ui.view;

import android.os.Bundle;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.data.model.SpesaConDettagli;
import com.example.paripariapp.data.model.SpesaPartecipante;
import com.example.paripariapp.data.model.SyncStatus;
import com.example.paripariapp.databinding.FragmentModificaRimborsoBinding;
import com.example.paripariapp.ui.viewmodel.DettaglioSchedaViewModel;
import com.example.paripariapp.util.DecimalDigitsInputFilter;
import com.example.paripariapp.util.HapticUtil;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.transition.MaterialSharedAxis;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Schermata per la modifica di un rimborso esistente.
 * Supporta cambio di mittente, destinatario, importo, valuta, data e nota opzionale.
 */
public class ModificaRimborsoFragment extends Fragment {

    private static final String ARG_SPESA_ID = "arg_spesa_id";
    private static final String ARG_SCHEDA_ID = "arg_scheda_id";
    private static final String ARG_VALUTA = "arg_valuta";

    private FragmentModificaRimborsoBinding binding;
    private DettaglioSchedaViewModel viewModel;

    private String spesaId;
    private String schedaId;
    private String valutaGruppo;

    private Spesa spesaCorrente;
    private List<Partecipante> partecipanti = new ArrayList<>();
    private List<SpesaPartecipante> quoteEsistenti = new ArrayList<>();
    private long dataSelezionataTimestamp = System.currentTimeMillis();
    private boolean isDataLoaded = false;

    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault());

    public static ModificaRimborsoFragment newInstance(String spesaId, String schedaId, String valuta) {
        ModificaRimborsoFragment fragment = new ModificaRimborsoFragment();
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
        setEnterTransition(new MaterialSharedAxis(MaterialSharedAxis.Z, true));
        setReturnTransition(new MaterialSharedAxis(MaterialSharedAxis.Z, false));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentModificaRimborsoBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(DettaglioSchedaViewModel.class);

        binding.toolbarModificaRimborso.setNavigationOnClickListener(v -> {
            if (getParentFragmentManager() != null) {
                getParentFragmentManager().popBackStack();
            }
        });

        setupValute();
        setupDatePicker();
        setupInputFilters();
        setupAzioneElimina();
        setupSalva();
        setupObservers();
    }

    private void setupValute() {
        binding.campoValuta.setText(valutaGruppo != null ? valutaGruppo : "EUR");
        binding.campoValuta.setFocusable(false);
        binding.campoValuta.setClickable(true);
        binding.campoValuta.setOnClickListener(v -> {
            String valutaAttuale = binding.campoValuta.getText() != null ? binding.campoValuta.getText().toString() : "EUR";
            SelettoreValutaBottomSheet sheet = SelettoreValutaBottomSheet.newInstance(valutaAttuale);
            sheet.setOnCurrencySelectedListener(currencyFull -> {
                String nuovaValuta = com.example.paripariapp.data.repository.UserPreferencesRepository.extractCurrencyCode(currencyFull);
                binding.campoValuta.setText(nuovaValuta);
            });
            sheet.show(getParentFragmentManager(), "selettore_valuta_rimborso");
        });
    }

    private void setupDatePicker() {
        aggiornaDataVisualizzata(dataSelezionataTimestamp);

        View.OnClickListener clickListener = v -> {
            MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText(R.string.label_data_spesa)
                    .setSelection(dataSelezionataTimestamp)
                    .build();

            picker.addOnPositiveButtonClickListener(selection -> {
                if (selection != null) {
                    dataSelezionataTimestamp = selection;
                    aggiornaDataVisualizzata(dataSelezionataTimestamp);
                }
            });

            picker.show(getParentFragmentManager(), "MODIFICA_RIMBORSO_DATE_PICKER");
        };

        binding.campoData.setOnClickListener(clickListener);
        binding.contenitoreData.setEndIconOnClickListener(clickListener);
    }

    private void aggiornaDataVisualizzata(long timestamp) {
        if (binding == null) return;
        String dataFmt = dateFormatter.format(
                Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
        );
        binding.campoData.setText(dataFmt);
    }

    private void setupInputFilters() {
        binding.campoImporto.setFilters(new InputFilter[]{new DecimalDigitsInputFilter(2)});
    }

    private void setupAzioneElimina() {
        binding.azioneElimina.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.dialog_titolo_elimina_rimborso)
                    .setMessage(R.string.dialog_msg_elimina_rimborso)
                    .setPositiveButton(R.string.btn_elimina_rimborso, (dialog, which) -> {
                        viewModel.eliminaSpesa(spesaId, schedaId);
                        if (getParentFragmentManager() != null) {
                            getParentFragmentManager().popBackStack();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });
    }

    private void setupObservers() {
        if (schedaId != null) {
            viewModel.getPartecipanti(schedaId).observe(getViewLifecycleOwner(), lista -> {
                if (lista == null) return;
                this.partecipanti = lista;
                popolaDropdownPartecipanti();
            });

            viewModel.getQuoteDellaScheda(schedaId).observe(getViewLifecycleOwner(), quote -> {
                this.quoteEsistenti = (quote != null) ? quote : new ArrayList<>();
                popolaCampiSePronto();
            });

            viewModel.getSpeseConDettagli(schedaId).observe(getViewLifecycleOwner(), spese -> {
                if (spese != null && !isDataLoaded) {
                    for (SpesaConDettagli scd : spese) {
                        if (scd.getSpesa() != null && scd.getSpesa().getId().equals(spesaId)) {
                            this.spesaCorrente = scd.getSpesa();
                            popolaCampiSePronto();
                            break;
                        }
                    }
                }
            });
        }
    }

    private void popolaDropdownPartecipanti() {
        if (partecipanti == null || !isAdded()) return;

        List<String> nomi = new ArrayList<>();
        for (Partecipante p : partecipanti) {
            nomi.add(p.getNome());
        }
        ArrayAdapter<String> adapter = SpesaUiHelper.creaDropdownAdapter(requireContext(), nomi);
        binding.menuPagante.setAdapter(adapter);

        binding.menuPagante.setOnItemClickListener((parent, v, position, id) -> {
            HapticUtil.tick(binding.menuPagante);
            aggiornaDropdownDestinatario();
        });

        binding.menuPagante.setOnClickListener(v -> binding.menuPagante.showDropDown());
        binding.menuDestinatario.setOnClickListener(v -> binding.menuDestinatario.showDropDown());

        popolaCampiSePronto();
    }

    private void aggiornaDropdownDestinatario() {
        if (partecipanti == null || !isAdded()) return;
        String paganteSelezionato = binding.menuPagante.getText() != null ? binding.menuPagante.getText().toString() : "";
        List<String> destinatariDisponibili = new ArrayList<>();
        for (Partecipante p : partecipanti) {
            if (!p.getNome().equalsIgnoreCase(paganteSelezionato)) {
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

    private void popolaCampiSePronto() {
        if (isDataLoaded || spesaCorrente == null || partecipanti.isEmpty()) return;

        // 1. Descrizione opzionale
        String desc = spesaCorrente.getScontrinoUrl();
        if (desc != null && !desc.trim().isEmpty()) {
            binding.campoDescrizione.setText(desc.trim());
        }

        // 2. Importo
        binding.campoImporto.setText(String.format(Locale.US, "%.2f", spesaCorrente.getImporto()));

        // 3. Valuta
        if (spesaCorrente.getValuta() != null) {
            binding.campoValuta.setText(spesaCorrente.getValuta());
        }

        // 4. Data
        dataSelezionataTimestamp = spesaCorrente.getDataSpesa();
        aggiornaDataVisualizzata(dataSelezionataTimestamp);

        // 5. Mittente (Pagatore)
        for (Partecipante p : partecipanti) {
            if (p.getId().equals(spesaCorrente.getPagatoDaId())) {
                binding.menuPagante.setText(p.getNome(), false);
                break;
            }
        }

        // 6. Destinatario (dalla quota)
        aggiornaDropdownDestinatario();
        for (SpesaPartecipante q : quoteEsistenti) {
            if (q.getSpesaId().equals(spesaCorrente.getId())) {
                for (Partecipante p : partecipanti) {
                    if (p.getId().equals(q.getPartecipanteId())) {
                        binding.menuDestinatario.setText(p.getNome(), false);
                        break;
                    }
                }
                break;
            }
        }

        isDataLoaded = true;
    }

    private void setupSalva() {
        binding.azioneSalva.setOnClickListener(v -> {
            String importoStr = binding.campoImporto.getText() != null ? binding.campoImporto.getText().toString().trim() : "";
            double importo = 0.0;
            try {
                importo = Double.parseDouble(importoStr.replace(",", "."));
            } catch (NumberFormatException ignored) {}

            if (importo <= 0) {
                binding.campoImporto.setError(getString(R.string.error_importo_spesa));
                return;
            } else {
                binding.campoImporto.setError(null);
            }

            String paganteNome = binding.menuPagante.getText() != null ? binding.menuPagante.getText().toString().trim() : "";
            String destinatarioNome = binding.menuDestinatario.getText() != null ? binding.menuDestinatario.getText().toString().trim() : "";

            if (paganteNome.isEmpty() || destinatarioNome.isEmpty() || paganteNome.equalsIgnoreCase(destinatarioNome)) {
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

            if (daId == null || aId == null) {
                binding.contenitoreDestinatario.setError(getString(R.string.errore_mittente_destinatario_uguali));
                return;
            }

            String pulitoDa = Partecipante.pulisciNome(paganteNome);
            String pulitoA = Partecipante.pulisciNome(destinatarioNome);
            String titolo = "Rimborso: da " + (pulitoDa.isEmpty() ? "Membro" : pulitoDa) + " a " + (pulitoA.isEmpty() ? "Membro" : pulitoA);

            String desc = binding.campoDescrizione.getText() != null ? binding.campoDescrizione.getText().toString().trim() : null;
            if (desc != null && desc.isEmpty()) desc = null;

            String valuta = binding.campoValuta.getText() != null && !binding.campoValuta.getText().toString().trim().isEmpty()
                    ? binding.campoValuta.getText().toString().trim()
                    : (valutaGruppo != null ? valutaGruppo : "EUR");

            Spesa spesaAggiornata = new Spesa(
                    spesaId,
                    schedaId,
                    titolo,
                    importo,
                    valuta,
                    dataSelezionataTimestamp,
                    "Pareggio",
                    daId,
                    desc,
                    SyncStatus.PENDING_UPDATE
            );

            List<SpesaPartecipante> nuoveQuote = new ArrayList<>();
            nuoveQuote.add(new SpesaPartecipante(
                    spesaId,
                    aId,
                    importo,
                    SyncStatus.PENDING_UPDATE
            ));

            viewModel.aggiornaSpesaConQuote(spesaAggiornata, nuoveQuote);
            HapticUtil.confirm(binding.azioneSalva);

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
