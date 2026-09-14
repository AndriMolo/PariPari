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

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.Scheda;
import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.data.model.SpesaPartecipante;
import com.example.paripariapp.databinding.FragmentStoricoSaldiBinding;
import com.example.paripariapp.ui.viewmodel.SpeseViewModel;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fragment per visualizzare lo storico dei saldi e pareggi effettuati.
 * Supporta filtri per scheda, blocco dell'annullamento se un membro è uscito, e propagazione su DB.
 */
public class StoricoSaldiFragment extends Fragment {

    private static final String ARG_SCHEDA_ID = "arg_scheda_id";

    private FragmentStoricoSaldiBinding binding;
    private StoricoSaldiAdapter adapter;
    private String schedaId;
    private SpeseViewModel viewModel;

    private List<Scheda> schedeCache = new ArrayList<>();
    private List<Spesa> saldiCache = new ArrayList<>();
    private final Map<String, List<Partecipante>> partecipantiPerScheda = new HashMap<>();
    private final Map<String, List<SpesaPartecipante>> quotePerSpesaAll = new HashMap<>();
    private String schedaFiltroId = null; // null = Tutti i gruppi

    public static StoricoSaldiFragment newInstance(@Nullable String schedaId) {
        StoricoSaldiFragment fragment = new StoricoSaldiFragment();
        if (schedaId != null) {
            Bundle args = new Bundle();
            args.putString(ARG_SCHEDA_ID, schedaId);
            fragment.setArguments(args);
        }
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            schedaId = getArguments().getString(ARG_SCHEDA_ID);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentStoricoSaldiBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(SpeseViewModel.class);

        binding.toolbarStoricoSaldi.setNavigationOnClickListener(v -> {
            if (getParentFragmentManager() != null) {
                getParentFragmentManager().popBackStack();
            }
        });

        adapter = new StoricoSaldiAdapter();
        binding.recyclerStoricoSaldi.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerStoricoSaldi.setAdapter(adapter);

        adapter.setOnItemClickListener(item -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.titolo_storico_saldi)
                    .setMessage(R.string.msg_saldi_rimozione_solo_spese)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        });

        setupFiltriScheda();
        setupObservers();
    }

    private void setupFiltriScheda() {
        if (schedaId == null) {
            binding.scrollFiltriScheda.setVisibility(View.VISIBLE);
            binding.chipGroupSchede.setOnCheckedStateChangeListener((group, checkedIds) -> {
                int checkedId = (checkedIds != null && !checkedIds.isEmpty()) ? checkedIds.get(0) : View.NO_ID;
                if (checkedId == View.NO_ID || checkedId == R.id.chip_tutti_gruppi) {
                    schedaFiltroId = null;
                } else {
                    Chip chip = group.findViewById(checkedId);
                    if (chip != null && chip.getTag() instanceof String) {
                        schedaFiltroId = (String) chip.getTag();
                    }
                }
                aggiornaLista();
            });
        } else {
            binding.scrollFiltriScheda.setVisibility(View.GONE);
        }
    }

    private void setupObservers() {
        viewModel.getSchede().observe(getViewLifecycleOwner(), schede -> {
            schedeCache = schede != null ? schede : new ArrayList<>();
            
            if (schedaId == null && binding != null) {
                View tuttiChip = binding.chipGroupSchede.findViewById(R.id.chip_tutti_gruppi);
                binding.chipGroupSchede.removeAllViews();
                if (tuttiChip != null) {
                    binding.chipGroupSchede.addView(tuttiChip);
                }

                for (Scheda sch : schedeCache) {
                    Chip chip = new Chip(requireContext());
                    chip.setText(sch.getTitolo());
                    chip.setCheckable(true);
                    chip.setTag(sch.getId());
                    binding.chipGroupSchede.addView(chip);
                }
            }

            for (Scheda s : schedeCache) {
                viewModel.getPartecipanti(s.getId()).observe(getViewLifecycleOwner(), partecipanti -> {
                    if (partecipanti != null) {
                        partecipantiPerScheda.put(s.getId(), partecipanti);
                        aggiornaLista();
                    }
                });
                viewModel.getQuoteDellaScheda(s.getId()).observe(getViewLifecycleOwner(), quote -> {
                    if (quote != null) {
                        for (SpesaPartecipante q : quote) {
                            List<SpesaPartecipante> list = quotePerSpesaAll.computeIfAbsent(q.getSpesaId(), k -> new ArrayList<>());
                            if (!list.contains(q)) {
                                list.add(q);
                            }
                        }
                    }
                });
            }
            aggiornaLista();
        });

        viewModel.getStoricoSaldi(schedaId).observe(getViewLifecycleOwner(), saldiList -> {
            saldiCache = saldiList != null ? saldiList : new ArrayList<>();
            aggiornaLista();
        });
    }

    private void aggiornaLista() {
        if (binding == null) return;

        List<Spesa> listaFiltrata = new ArrayList<>();
        for (Spesa s : saldiCache) {
            if (schedaFiltroId == null || s.getSchedaId().equals(schedaFiltroId)) {
                listaFiltrata.add(s);
            }
        }

        if (listaFiltrata.isEmpty()) {
            binding.layoutEmptyStorico.setVisibility(View.VISIBLE);
            binding.recyclerStoricoSaldi.setVisibility(View.GONE);
            return;
        }

        binding.layoutEmptyStorico.setVisibility(View.GONE);
        binding.recyclerStoricoSaldi.setVisibility(View.VISIBLE);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        List<StoricoSaldiAdapter.StoricoItem> items = new ArrayList<>();
        boolean showGroupTitle = (schedaId == null);

        for (Spesa s : listaFiltrata) {
            String nomeScheda = "Gruppo";
            List<Partecipante> partecipantiScheda = partecipantiPerScheda.get(s.getSchedaId());
            for (Scheda sch : schedeCache) {
                if (sch.getId().equals(s.getSchedaId())) {
                    nomeScheda = sch.getTitolo();
                    break;
                }
            }

            String mittenteNome = "Membro";
            if (partecipantiScheda != null) {
                for (Partecipante p : partecipantiScheda) {
                    if (p.getId().equals(s.getPagatoDaId())) {
                        mittenteNome = Partecipante.pulisciNome(p.getNome());
                        break;
                    }
                }
            }

            boolean isRicevuto;
            String testoDescrizione;

            String myId = null;
            if (partecipantiScheda != null) {
                com.example.paripariapp.data.repository.UserPreferencesRepository prefs =
                        com.example.paripariapp.data.repository.UserPreferencesRepository.getInstance(requireContext());
                myId = Partecipante.findCurrentUserId(partecipantiScheda, currentUser, prefs, s.getSchedaId());
            }

            if (myId != null) {
                if (s.getPagatoDaId().equals(myId)) {
                    isRicevuto = false;
                    String dest = estraiDestinatarioDalTitolo(s.getTitolo());
                    testoDescrizione = getString(R.string.storico_pagamento_inviato, dest.toLowerCase(Locale.getDefault()));
                } else {
                    isRicevuto = true;
                    testoDescrizione = getString(R.string.storico_pagamento_ricevuto, mittenteNome.toLowerCase(Locale.getDefault()));
                }
            } else {
                isRicevuto = false;
                testoDescrizione = s.getTitolo();
            }

            items.add(new StoricoSaldiAdapter.StoricoItem(s, nomeScheda, testoDescrizione, isRicevuto, showGroupTitle));
        }

        adapter.submitList(items);
    }

    private String estraiDestinatarioDalTitolo(String titolo) {
        if (titolo == null) return "Membro";
        String marker = " a ";
        int idx = titolo.lastIndexOf(marker);
        if (idx != -1 && idx + marker.length() < titolo.length()) {
            return titolo.substring(idx + marker.length()).trim();
        }
        return "Membro";
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
