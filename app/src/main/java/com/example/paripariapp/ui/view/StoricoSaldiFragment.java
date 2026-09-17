package com.example.paripariapp.ui.view;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
    private final Map<String, String> spesaDestinatarioCache = new HashMap<>();
    private final Set<String> observedSchedeIds = new HashSet<>();
    private String schedaFiltroId = null; // null = Tutti i gruppi

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;

    private void schedulaAggiornamentoLista() {
        if (updateRunnable != null) {
            mainHandler.removeCallbacks(updateRunnable);
        }
        updateRunnable = () -> {
            if (isAdded() && getContext() != null && binding != null) {
                aggiornaLista();
            }
        };
        mainHandler.post(updateRunnable);
    }

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
            if (item == null || item.getSpesa() == null) return;
            Spesa spesa = item.getSpesa();
            if (isSettleUp(spesa)) {
                List<Partecipante> partecipanti = partecipantiPerScheda.get(spesa.getSchedaId());
                List<SpesaPartecipante> quote = quotePerSpesaAll.get(spesa.getId());
                if (SpesaUiHelper.haPartecipantiAssenti(spesa, quote, partecipanti)) {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.dialog_titolo_spesa_non_modificabile)
                            .setMessage(R.string.dialog_msg_spesa_membro_assente)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                    return;
                }
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.titolo_annulla_saldo)
                        .setMessage(R.string.msg_conferma_annulla_saldo)
                        .setPositiveButton(R.string.btn_annulla_saldo, (dialog, which) -> {
                            viewModel.eliminaSpesa(spesa.getId(), spesa.getSchedaId());
                        })
                        .setNegativeButton(R.string.btn_annulla, null)
                        .show();
            } else {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.titolo_storico_saldi)
                        .setMessage(R.string.msg_saldi_rimozione_solo_spese)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        });

        setupFiltriScheda();
        setupObservers();
        setupWindowInsets();
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int navBarBottom = systemBars.bottom;
            float density = getResources().getDisplayMetrics().density;

            int basePadding = (int) (16 * density);
            binding.recyclerStoricoSaldi.setPadding(
                    binding.recyclerStoricoSaldi.getPaddingLeft(),
                    binding.recyclerStoricoSaldi.getPaddingTop(),
                    binding.recyclerStoricoSaldi.getPaddingRight(),
                    basePadding + navBarBottom
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.getRoot());
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
                if (observedSchedeIds.add(s.getId())) {
                    viewModel.getPartecipanti(s.getId()).observe(getViewLifecycleOwner(), partecipanti -> {
                        if (partecipanti != null) {
                            partecipantiPerScheda.put(s.getId(), partecipanti);
                            schedulaAggiornamentoLista();
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
                            schedulaAggiornamentoLista();
                        }
                    });
                }
            }
            schedulaAggiornamentoLista();
        });

        viewModel.getStoricoSaldi(schedaId).observe(getViewLifecycleOwner(), saldiList -> {
            saldiCache = saldiList != null ? saldiList : new ArrayList<>();
            schedulaAggiornamentoLista();
        });
    }

    private String trovaDestinatarioId(Spesa s, @Nullable List<Partecipante> partecipantiScheda) {
        if (s == null) return null;
        
        String cached = spesaDestinatarioCache.get(s.getId());
        if (cached != null) {
            return cached;
        }

        List<SpesaPartecipante> quote = quotePerSpesaAll.get(s.getId());
        if (quote != null && !quote.isEmpty()) {
            for (SpesaPartecipante q : quote) {
                if (q.getPartecipanteId() != null && !q.getPartecipanteId().equals(s.getPagatoDaId())) {
                    spesaDestinatarioCache.put(s.getId(), q.getPartecipanteId());
                    return q.getPartecipanteId();
                }
            }
        }
        if (partecipantiScheda != null && !partecipantiScheda.isEmpty()) {
            String destNome = estraiDestinatarioDalTitolo(s.getTitolo());
            if (!destNome.isEmpty() && !"Membro".equalsIgnoreCase(destNome)) {
                for (Partecipante p : partecipantiScheda) {
                    String pNome = Partecipante.pulisciNome(p.getNome());
                    if (pNome.equalsIgnoreCase(destNome) && !p.getId().equals(s.getPagatoDaId())) {
                        spesaDestinatarioCache.put(s.getId(), p.getId());
                        return p.getId();
                    }
                }
            }
        }
        return null;
    }

    private void aggiornaLista() {
        if (!isAdded() || getContext() == null || binding == null) return;
        Context context = getContext();

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        com.example.paripariapp.data.repository.UserPreferencesRepository prefs =
                com.example.paripariapp.data.repository.UserPreferencesRepository.getInstance(context);

        List<Spesa> listaFiltrata = new ArrayList<>();
        for (Spesa s : saldiCache) {
            if (schedaFiltroId != null && !s.getSchedaId().equals(schedaFiltroId)) {
                continue;
            }

            // Se siamo nello storico saldi generale (schedaId == null), mostrare SOLO i saldi in cui IO sono coinvolto
            if (schedaId == null) {
                List<Partecipante> partecipantiScheda = partecipantiPerScheda.get(s.getSchedaId());
                String myId = (partecipantiScheda != null) ? Partecipante.findCurrentUserId(partecipantiScheda, currentUser, prefs, s.getSchedaId()) : null;

                if (myId != null) {
                    String destId = trovaDestinatarioId(s, partecipantiScheda);
                    boolean isMittente = s.getPagatoDaId() != null && s.getPagatoDaId().equals(myId);
                    boolean isDestinatario = destId != null && destId.equals(myId);

                    if (!isMittente && !isDestinatario) {
                        continue; // Esclude i saldi tra terzi dallo storico generale personale
                    }
                }
            }

            listaFiltrata.add(s);
        }

        if (listaFiltrata.isEmpty()) {
            binding.layoutEmptyStorico.setVisibility(View.VISIBLE);
            binding.recyclerStoricoSaldi.setVisibility(View.GONE);
            return;
        }

        binding.layoutEmptyStorico.setVisibility(View.GONE);
        binding.recyclerStoricoSaldi.setVisibility(View.VISIBLE);

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

            String destId = trovaDestinatarioId(s, partecipantiScheda);
            String destinatarioNome = null;
            if (destId != null && partecipantiScheda != null) {
                for (Partecipante p : partecipantiScheda) {
                    if (p.getId().equals(destId)) {
                        destinatarioNome = Partecipante.pulisciNome(p.getNome());
                        break;
                    }
                }
            }
            if (destinatarioNome == null) {
                destinatarioNome = estraiDestinatarioDalTitolo(s.getTitolo());
            }

            String myId = (partecipantiScheda != null) ? Partecipante.findCurrentUserId(partecipantiScheda, currentUser, prefs, s.getSchedaId()) : null;

            boolean isRicevuto = false;
            boolean isTerzo = false;
            String testoDescrizione;

            if (myId != null && s.getPagatoDaId() != null && s.getPagatoDaId().equals(myId)) {
                isRicevuto = false;
                isTerzo = false;
                testoDescrizione = getString(R.string.storico_pagamento_inviato, destinatarioNome.toLowerCase(Locale.getDefault()));
            } else if (myId != null && destId != null && destId.equals(myId)) {
                isRicevuto = true;
                isTerzo = false;
                testoDescrizione = getString(R.string.storico_pagamento_ricevuto, mittenteNome.toLowerCase(Locale.getDefault()));
            } else {
                isRicevuto = false;
                isTerzo = true;
                testoDescrizione = mittenteNome + " → " + destinatarioNome;
            }

            items.add(new StoricoSaldiAdapter.StoricoItem(s, nomeScheda, testoDescrizione, isRicevuto, showGroupTitle, isTerzo));
        }

        adapter.submitList(items);
    }

    private boolean isSettleUp(@Nullable Spesa spesa) {
        if (spesa == null) return false;
        String cat = spesa.getCategoria() != null ? spesa.getCategoria().trim().toLowerCase(Locale.ROOT) : "";
        String desc = spesa.getScontrinoUrl() != null ? spesa.getScontrinoUrl().trim() : "";
        if ("SETTLE_UP".equalsIgnoreCase(desc)) {
            return true;
        }
        return cat.equals("pareggio") || cat.equals("saldo") || cat.equals("saldi");
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
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof com.example.paripariapp.MainActivity) {
            ((com.example.paripariapp.MainActivity) getActivity()).impostaVisibilitaBottomNav(false);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (getActivity() instanceof com.example.paripariapp.MainActivity) {
            ((com.example.paripariapp.MainActivity) getActivity()).impostaVisibilitaBottomNav(true);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
