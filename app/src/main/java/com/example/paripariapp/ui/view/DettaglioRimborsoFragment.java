package com.example.paripariapp.ui.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.data.model.SpesaConDettagli;
import com.example.paripariapp.data.model.SpesaPartecipante;
import com.example.paripariapp.databinding.FragmentDettaglioRimborsoBinding;
import com.example.paripariapp.ui.viewmodel.DettaglioSchedaViewModel;
import com.example.paripariapp.util.ImportoUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.transition.MaterialSharedAxis;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Fragment per visualizzare in dettaglio un rimborso / pareggio saldi.
 * Mostra mittente, destinatario, importo formattato, data e nota opzionale.
 */
public class DettaglioRimborsoFragment extends Fragment {

    private static final String ARG_SPESA_ID = "arg_spesa_id";
    private static final String ARG_SCHEDA_ID = "arg_scheda_id";
    private static final String ARG_VALUTA = "arg_valuta";

    private FragmentDettaglioRimborsoBinding binding;
    private DettaglioSchedaViewModel viewModel;

    private String spesaId;
    private String schedaId;
    private String valutaGruppo;

    private List<Partecipante> partecipantiCache = new ArrayList<>();
    private List<SpesaPartecipante> quoteCache = new ArrayList<>();
    private SpesaConDettagli spesaCorrente;

    public static DettaglioRimborsoFragment newInstance(String spesaId, String schedaId, String valuta) {
        DettaglioRimborsoFragment fragment = new DettaglioRimborsoFragment();
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
        binding = FragmentDettaglioRimborsoBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(DettaglioSchedaViewModel.class);

        binding.toolbarDettaglioRimborso.setNavigationOnClickListener(v -> {
            if (getParentFragmentManager() != null) {
                getParentFragmentManager().popBackStack();
            }
        });

        binding.toolbarDettaglioRimborso.setOnMenuItemClickListener(item -> {
            if (spesaCorrente == null || spesaCorrente.getSpesa() == null) return false;
            Spesa spesa = spesaCorrente.getSpesa();

            // Verifica che tutti i partecipanti coinvolti siano ancora nel gruppo
            Set<String> activeIds = new HashSet<>();
            if (partecipantiCache != null) {
                for (Partecipante p : partecipantiCache) {
                    activeIds.add(p.getId());
                }
            }

            boolean haMembroAssente = false;
            if (spesa.getPagatoDaId() != null && !activeIds.contains(spesa.getPagatoDaId())) {
                haMembroAssente = true;
            }
            if (!haMembroAssente && quoteCache != null) {
                for (SpesaPartecipante q : quoteCache) {
                    if (q.getSpesaId().equals(spesa.getId())) {
                        if (q.getPartecipanteId() != null && !activeIds.contains(q.getPartecipanteId())) {
                            haMembroAssente = true;
                            break;
                        }
                    }
                }
            }

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
                ModificaRimborsoFragment fragment = ModificaRimborsoFragment.newInstance(
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
                        .setTitle(R.string.dialog_titolo_elimina_rimborso)
                        .setMessage(R.string.dialog_msg_elimina_rimborso)
                        .setPositiveButton(R.string.btn_elimina_rimborso, (dialog, which) -> {
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
                partecipantiCache = (partecipanti != null) ? partecipanti : new ArrayList<>();
                aggiornaInterfaccia();
            });

            viewModel.getQuoteDellaScheda(schedaId).observe(getViewLifecycleOwner(), quote -> {
                quoteCache = (quote != null) ? quote : new ArrayList<>();
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

        // 1. Badge Categoria
        binding.tvCategoriaDettaglio.setText(getString(R.string.format_categoria_rimborso, getString(R.string.cat_rimborsi)));

        // 2. Titolo
        binding.tvTitoloDettaglio.setText(spesa.getTitolo());

        // 3. Data
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault());
        String dataFmt = formatter.format(
                Instant.ofEpochMilli(spesa.getDataSpesa()).atZone(ZoneId.systemDefault())
        );
        binding.tvDataDettaglio.setText(dataFmt);

        // 4. Importo
        binding.tvImportoDettaglio.setText(ImportoUtil.formatta(spesa.getImporto(), spesa.getValuta()));

        // 5. Da chi pagato (Mittente)
        String nomePagatore = getString(R.string.nome_sconosciuto);
        for (Partecipante p : partecipantiCache) {
            if (p.getId().equals(spesa.getPagatoDaId())) {
                nomePagatore = p.getNome();
                break;
            }
        }
        binding.tvDaChiPagato.setText(getString(R.string.format_da_chi_pagato, nomePagatore));

        // 6. Chi li riceve (Destinatario dalle quote)
        String nomeDestinatario = getString(R.string.nome_sconosciuto);
        for (SpesaPartecipante q : quoteCache) {
            if (q.getSpesaId().equals(spesa.getId())) {
                for (Partecipante p : partecipantiCache) {
                    if (p.getId().equals(q.getPartecipanteId())) {
                        nomeDestinatario = p.getNome();
                        break;
                    }
                }
                break;
            }
        }
        binding.tvChiRiceve.setText(getString(R.string.format_chi_riceve, nomeDestinatario));

        // 7. Descrizione Opzionale (in scontrinoUrl)
        String desc = spesa.getScontrinoUrl();
        if (desc != null && !desc.trim().isEmpty()) {
            binding.tvDescrizioneDettaglio.setText(desc.trim());
            binding.tvDescrizioneDettaglio.setAlpha(1.0f);
        } else {
            binding.tvDescrizioneDettaglio.setText(R.string.label_nessuna_descrizione);
            binding.tvDescrizioneDettaglio.setAlpha(0.6f);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
