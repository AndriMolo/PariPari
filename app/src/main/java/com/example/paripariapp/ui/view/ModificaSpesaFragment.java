package com.example.paripariapp.ui.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.data.model.SpesaPartecipante;
import com.example.paripariapp.data.model.SyncStatus;
import com.example.paripariapp.databinding.FragmentModificaSpesaBinding;
import com.example.paripariapp.ui.viewmodel.DettaglioSchedaViewModel;
import com.example.paripariapp.util.AppSnackbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Schermata per la modifica di una spesa esistente, perfettamente allineata all'interfaccia
 * e alla logica di creazione. Estende BaseSpesaFragment per condividere la logica di divisione,
 * auto-bilanciamento in tempo reale e lifecycle cleanup.
 */
public class ModificaSpesaFragment extends BaseSpesaFragment {

    private static final String ARG_SPESA_ID = "arg_spesa_id";

    private FragmentModificaSpesaBinding binding;
    private DettaglioSchedaViewModel viewModel;

    private String spesaId;
    private Spesa spesaCorrente;
    private List<SpesaPartecipante> quoteEsistenti = new ArrayList<>();

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

        initCommonViews(
                binding.toolbarModificaSpesa,
                binding.campoDescrizione,
                binding.campoImporto,
                binding.campoValuta,
                binding.menuPagante,
                binding.menuCategoria,
                binding.toggleGruppoDivisione,
                binding.layoutElencoQuote,
                binding.azioneSalva
        );

        setupAzioneElimina();
        setupObservers();
        setupSalva();
    }

    private void setupAzioneElimina() {
        binding.azioneElimina.setOnClickListener(v -> {
            if (isReadOnly) {
                AppSnackbar.show(binding.getRoot(), R.string.msg_spesa_non_modificabile_membro_assente);
                return;
            }
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.dialog_titolo_elimina_spesa)
                    .setMessage(R.string.dialog_msg_elimina_spesa)
                    .setPositiveButton(R.string.btn_elimina, (dialog, which) -> {
                        viewModel.eliminaSpesa(spesaId, schedaId);
                        AppSnackbar.showFromFragment(this, R.string.msg_spesa_eliminata);
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

            final List<String> nomi = new ArrayList<>();
            for (Partecipante p : lista) {
                nomi.add(p.getNome());
            }
            ArrayAdapter<String> adapter = SpesaUiHelper.creaDropdownAdapter(requireContext(), nomi);
            binding.menuPagante.setAdapter(adapter);
            binding.menuPagante.setOnClickListener(v -> binding.menuPagante.showDropDown());

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
        this.valuta = spesaCorrente.getValuta() != null ? spesaCorrente.getValuta() : getValutaEffettiva();
        binding.campoValuta.setText(this.valuta);

        // Verifica presenza di partecipanti assenti
        Set<String> activeParticipantIds = new HashSet<>();
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

        if (spesaCorrente.getCategoria() != null) {
            binding.menuCategoria.setText(spesaCorrente.getCategoria(), false);
        }

        // Mappa delle quote esistenti e dei partecipanti inclusi
        Map<String, Double> quoteMap = new HashMap<>();
        Set<String> initiallyIncludedIds = new HashSet<>();
        if (quoteEsistenti != null) {
            for (SpesaPartecipante q : quoteEsistenti) {
                quoteMap.put(q.getPartecipanteId(), q.getQuota());
                if (q.getQuota() >= 0) {
                    initiallyIncludedIds.add(q.getPartecipanteId());
                }
            }
        }

        double importoTotale = spesaCorrente.getImporto();
        boolean haQuotePersonalizzate = false;
        if (importoTotale > 0 && quoteEsistenti != null && quoteEsistenti.size() > 1) {
            double quotaStandard = importoTotale / quoteEsistenti.size();
            for (SpesaPartecipante q : quoteEsistenti) {
                if (Math.abs(q.getQuota() - quotaStandard) > 0.05) {
                    haQuotePersonalizzate = true;
                    break;
                }
            }
        }

        popolaRighePartecipantiComuni(partecipanti, initiallyIncludedIds, quoteMap, importoTotale);

        if (haQuotePersonalizzate) {
            binding.toggleGruppoDivisione.check(R.id.btn_divisione_parti);
            cambiaTipoDivisione(SpesaUiHelper.TipoDivisione.PER_PARTI);
        } else {
            binding.toggleGruppoDivisione.check(R.id.btn_divisione_equa);
            cambiaTipoDivisione(SpesaUiHelper.TipoDivisione.EQUA);
        }

        if (isReadOnly) {
            applicaModalitaSolaLettura();
        }
    }

    private void applicaModalitaSolaLettura() {
        if (binding == null) return;

        binding.cardBannerReadonly.setVisibility(View.VISIBLE);
        AppSnackbar.showLong(binding.getRoot(), R.string.msg_spesa_non_modificabile_membro_assente);

        binding.azioneSalva.setVisibility(View.GONE);
        binding.azioneElimina.setVisibility(View.GONE);

        binding.campoDescrizione.setEnabled(false);
        binding.campoImporto.setEnabled(false);
        binding.campoValuta.setEnabled(false);
        binding.menuPagante.setEnabled(false);
        binding.menuCategoria.setEnabled(false);

        binding.toggleGruppoDivisione.setEnabled(false);
        binding.btnDivisioneEqua.setEnabled(false);
        binding.btnDivisionePercentuale.setEnabled(false);
        binding.btnDivisioneParti.setEnabled(false);

        for (CheckBox cb : checkMap.values()) {
            if (cb != null) cb.setEnabled(false);
        }
        for (EditText et : quotaInputMap.values()) {
            if (et != null) et.setEnabled(false);
        }
    }

    private void setupSalva() {
        binding.azioneSalva.setOnClickListener(v -> {
            if (isReadOnly) {
                AppSnackbar.show(binding.getRoot(), R.string.msg_spesa_non_modificabile_membro_assente);
                return;
            }

            DatiFormValidi dati = validaEdEstraiDatiForm(spesaId, SyncStatus.PENDING_UPDATE);
            if (dati == null) return;

            // Preserva eventuale quotaPagata preesistente se già registrata
            Map<String, Double> quotaPagataPreesistenteMap = new HashMap<>();
            if (quoteEsistenti != null) {
                for (SpesaPartecipante q : quoteEsistenti) {
                    quotaPagataPreesistenteMap.put(q.getPartecipanteId(), q.getQuotaPagata());
                }
            }

            List<SpesaPartecipante> nuoveQuote = new ArrayList<>();
            for (SpesaPartecipante q : dati.quoteCalcolate) {
                Double qPagata = quotaPagataPreesistenteMap.get(q.getPartecipanteId());
                q.setQuotaPagata(qPagata != null ? qPagata : 0.0);
                nuoveQuote.add(q);
            }

            Spesa spesaAggiornata = new Spesa(
                    spesaId,
                    schedaId,
                    dati.titolo,
                    dati.importo,
                    getValutaEffettiva(),
                    spesaCorrente != null ? spesaCorrente.getDataSpesa() : System.currentTimeMillis(),
                    dati.categoria,
                    dati.pagatoreId,
                    spesaCorrente != null ? spesaCorrente.getScontrinoUrl() : null,
                    SyncStatus.PENDING_UPDATE
            );

            viewModel.aggiornaSpesaConQuote(spesaAggiornata, nuoveQuote);
            AppSnackbar.showFromFragment(this, R.string.msg_spesa_aggiornata);

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
