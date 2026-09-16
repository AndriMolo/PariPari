package com.example.paripariapp.ui.view;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.Scheda;
import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.data.model.SpesaConDettagli;
import com.example.paripariapp.data.model.SpesaListItem;
import com.example.paripariapp.data.model.SpesaPartecipante;
import com.example.paripariapp.data.model.SyncStatus;
import com.example.paripariapp.data.model.TrasferimentoSaldo;
import com.example.paripariapp.databinding.FragmentDettaglioSchedaBinding;
import com.example.paripariapp.ui.viewmodel.DettaglioSchedaViewModel;
import com.example.paripariapp.util.AppSnackbar;
import com.example.paripariapp.util.CalcolatoreSaldi;
import com.example.paripariapp.util.EsportatoreDati;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class DettaglioSchedaFragment extends Fragment {

    private static final String ARG_SCHEDA_ID = "arg_scheda_id";
    private static final String ARG_TITOLO = "arg_titolo";
    private static final String ARG_VALUTA = "arg_valuta";

    private FragmentDettaglioSchedaBinding binding;
    private DettaglioSchedaViewModel viewModel;
    private SpesaAdapter adapter;
    private SaldoAdapter saldoAdapter;
    private BilancioMembroAdapter bilancioMembroAdapter;
    private MembroAdapter membroAdapter;
    private MembroAdapter exMembroAdapter;

    private String schedaId;
    private String titolo;
    private String valuta;
    private Scheda schedaCorrente;

    private List<Partecipante> partecipantiCache = new ArrayList<>();
    private List<SpesaPartecipante> quoteCache = new ArrayList<>();
    private final List<Spesa> speseCache = new ArrayList<>();
    private List<SpesaConDettagli> tutteSpeseRaw = new ArrayList<>();

    private String queryFiltroTesto = "";
    private String categoriaSelezionata = "";
    private final SimpleDateFormat dateFormatHeader = new SimpleDateFormat("d MMMM yyyy", Locale.getDefault());

    public static DettaglioSchedaFragment newInstance(String schedaId, String titolo, String valuta) {
        DettaglioSchedaFragment fragment = new DettaglioSchedaFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SCHEDA_ID, schedaId);
        args.putString(ARG_TITOLO, titolo);
        args.putString(ARG_VALUTA, valuta);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            schedaId = getArguments().getString(ARG_SCHEDA_ID);
            titolo = getArguments().getString(ARG_TITOLO);
            valuta = getArguments().getString(ARG_VALUTA);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDettaglioSchedaBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(DettaglioSchedaViewModel.class);

        categoriaSelezionata = getString(R.string.filtro_tutte);

        setupToolbar();
        setupRecyclerView();
        setupRecyclerSaldi();
        setupRicercaEFiltri();
        setupObservers();
        setupFab();
        setupBottomNavScheda();
        setupRecyclerMembri();

    }

    private void setupBottomNavScheda() {
        binding.bottomNavScheda.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            binding.sezioneSpese.setVisibility(id == R.id.nav_scheda_spese ? View.VISIBLE : View.GONE);
            binding.sezioneSaldi.setVisibility(id == R.id.nav_scheda_saldi ? View.VISIBLE : View.GONE);
            binding.sezioneMembri.setVisibility(id == R.id.nav_scheda_membri ? View.VISIBLE : View.GONE);

            MenuItem searchItem = binding.toolbarDettaglio.getMenu().findItem(R.id.action_cerca);
            if (searchItem != null) {
                if (id == R.id.nav_scheda_spese) {
                    searchItem.setVisible(true);
                } else {
                    if (searchItem.isActionViewExpanded()) {
                        searchItem.collapseActionView();
                    }
                    searchItem.setVisible(false);
                }
            }

            if (id == R.id.nav_scheda_spese) {
                binding.fabNuovaSpesa.show();
            } else {
                binding.fabNuovaSpesa.hide();
            }
            return true;
        });
    }

    private void setupRecyclerSaldi() {
        saldoAdapter = new SaldoAdapter();
        binding.recyclerSaldi.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerSaldi.setAdapter(saldoAdapter);

        bilancioMembroAdapter = new BilancioMembroAdapter();
        binding.recyclerBilanciMembri.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerBilanciMembri.setAdapter(bilancioMembroAdapter);

        binding.btnStoricoSaldiScheda.setOnClickListener(v -> {
            StoricoSaldiFragment fragment = StoricoSaldiFragment.newInstance(schedaId);
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
        });

        saldoAdapter.setOnItemClickListener(item -> {
            if (item == null) return;
            String creditorePaypal = "";
            String creditoreRevolut = "";
            com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            String myPartId = Partecipante.findCurrentUserId(partecipantiCache, currentUser);

            if (partecipantiCache != null) {
                com.example.paripariapp.data.repository.UserPreferencesRepository prefs = com.example.paripariapp.data.repository.UserPreferencesRepository.getInstance(requireContext().getApplicationContext());
                for (Partecipante p : partecipantiCache) {
                    if (p.getId().equals(item.getAPartecipanteId())) {
                        boolean isCreditoreMe = p.getId().equals(myPartId) || Partecipante.isCurrentUserParticipant(p, currentUser);

                        creditorePaypal = (p.getPaypalHandle() != null && !p.getPaypalHandle().trim().isEmpty())
                                ? p.getPaypalHandle().trim()
                                : (isCreditoreMe ? prefs.getPaypalHandle() : "");

                        creditoreRevolut = (p.getRevolutHandle() != null && !p.getRevolutHandle().trim().isEmpty())
                                ? p.getRevolutHandle().trim()
                                : (isCreditoreMe ? prefs.getRevolutHandle() : "");
                        break;
                    }
                }
            }
            InvioPagamentoBottomSheet sheet = InvioPagamentoBottomSheet.newInstance(
                    item, schedaId, creditorePaypal, creditoreRevolut, myPartId
            );
            sheet.show(getChildFragmentManager(), "invio_pagamento_dialog");
        });
    }

    private void aggiornaSaldi() {
        if (saldoAdapter != null) {
            List<TrasferimentoSaldo> trasferimenti = CalcolatoreSaldi.calcolaTrasferimenti(
                    partecipantiCache,
                    speseCache,
                    quoteCache,
                    valuta != null ? valuta : "EUR",
                    requireContext()
            );

            saldoAdapter.submitList(trasferimenti != null ? trasferimenti : new ArrayList<>());

            List<CalcolatoreSaldi.BilancioMembro> bilanciMembri = CalcolatoreSaldi.calcolaListaBilanciMembri(
                    partecipantiCache,
                    speseCache,
                    quoteCache,
                    valuta != null ? valuta : "EUR",
                    requireContext()
            );
            if (bilancioMembroAdapter != null) {
                bilancioMembroAdapter.submitList(bilanciMembri);
            }

            if (partecipantiCache.isEmpty()) {
                binding.layoutEmptySaldi.getRoot().setVisibility(View.VISIBLE);
                binding.layoutEmptySaldi.tvEmptyTitle.setText(R.string.empty_saldi_titolo);
                binding.layoutEmptySaldi.tvEmptyDesc.setText(R.string.empty_saldi_desc);
                binding.recyclerSaldi.setVisibility(View.GONE);
                binding.recyclerBilanciMembri.setVisibility(View.GONE);
            } else {
                binding.layoutEmptySaldi.getRoot().setVisibility(View.GONE);
                binding.recyclerSaldi.setVisibility(View.VISIBLE);
                binding.recyclerBilanciMembri.setVisibility(View.VISIBLE);
            }
        }
    }

    private void setupToolbar() {
        if (titolo != null) {
            binding.toolbarDettaglio.setTitle(titolo);
        }
        binding.toolbarDettaglio.setNavigationOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().finish();
            }
        });

        binding.toolbarDettaglio.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_esporta) {
                mostraSceltaEsportazione();
                return true;
            } else if (itemId == R.id.action_codice_gruppo) {
                mostraDialogCodiceGruppo();
                return true;
            } else if (itemId == R.id.action_modifica_titolo) {
                mostraDialogModificaNome();
                return true;
            } else if (itemId == R.id.action_personalizza_icona) {
                BottomSheetIconaGruppo.newInstance(schedaId).show(getChildFragmentManager(), "BottomSheetIconaGruppo");
                return true;
            } else if (itemId == R.id.action_lascia_scheda) {
                mostraDialogLasciaScheda();
                return true;
            }
            return false;
        });
    }

    private void setupRecyclerView() {
        adapter = new SpesaAdapter();
        adapter.setOnSpesaClickListener(item -> {
            if (item == null || item.getSpesa() == null) return;
            Spesa spesa = item.getSpesa();

            if (com.example.paripariapp.util.CategoriaUtil.isCategoriaSaldi(spesa.getCategoria())) {
                DettaglioRimborsoFragment fragment = DettaglioRimborsoFragment.newInstance(
                        spesa.getId(),
                        schedaId,
                        valuta
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
            } else {
                DettaglioSpesaFragment fragment = DettaglioSpesaFragment.newInstance(
                        spesa.getId(),
                        schedaId,
                        valuta
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
            }
        });

        adapter.setOnSpesaLongClickListener((item, view) -> {
            if (item == null || item.getSpesa() == null) return;
            Spesa spesa = item.getSpesa();

            java.util.Set<String> activeIds = new java.util.HashSet<>();
            if (partecipantiCache != null) {
                for (Partecipante p : partecipantiCache) {
                    if (p.isAttivo()) {
                        activeIds.add(p.getId());
                    }
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
                return;
            }

            android.widget.PopupMenu popup = new android.widget.PopupMenu(requireContext(), view);
            popup.getMenu().add(0, 1, 0, R.string.btn_modifica);
            popup.getMenu().add(0, 2, 1, R.string.btn_elimina);
            popup.setOnMenuItemClickListener(menuItem -> {
                if (menuItem.getItemId() == 1) {
                    if (com.example.paripariapp.util.CategoriaUtil.isCategoriaSaldi(spesa.getCategoria())) {
                        ModificaRimborsoFragment fragment = ModificaRimborsoFragment.newInstance(
                                spesa.getId(),
                                schedaId,
                                valuta
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
                    } else {
                        ModificaSpesaFragment fragment = ModificaSpesaFragment.newInstance(
                                spesa.getId(),
                                schedaId,
                                valuta
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
                    }
                    return true;
                } else if (menuItem.getItemId() == 2) {
                    boolean isRimborso = com.example.paripariapp.util.CategoriaUtil.isCategoriaSaldi(spesa.getCategoria());
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle(isRimborso ? R.string.dialog_titolo_elimina_rimborso : R.string.dialog_titolo_elimina_spesa)
                            .setMessage(isRimborso ? R.string.dialog_msg_elimina_rimborso : R.string.dialog_msg_elimina_spesa)
                            .setPositiveButton(isRimborso ? R.string.btn_elimina_rimborso : R.string.btn_elimina, (dialog, which) -> {
                                viewModel.eliminaSpesa(spesa.getId(), schedaId);
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                    return true;
                }
                return false;
            });
            popup.show();
        });

        binding.recyclerSpese.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerSpese.setAdapter(adapter);

        binding.recyclerSpese.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    nascondiTastiera();
                }
            }
        });
    }

    private void nascondiTastiera() {
        if (getView() != null && getContext() != null) {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);
            }
        }
    }

    private void setupRicercaEFiltri() {
        MenuItem searchItem = binding.toolbarDettaglio.getMenu().findItem(R.id.action_cerca);
        if (searchItem != null) {
            if (searchItem.getIcon() != null) {
                int colorOnSurface = com.google.android.material.color.MaterialColors.getColor(
                        binding.toolbarDettaglio, com.google.android.material.R.attr.colorOnSurface);
                android.graphics.drawable.Drawable tintedIcon = androidx.core.graphics.drawable.DrawableCompat.wrap(searchItem.getIcon().mutate());
                androidx.core.graphics.drawable.DrawableCompat.setTint(tintedIcon, colorOnSurface);
                searchItem.setIcon(tintedIcon);
            }
            SearchView searchView = (SearchView) searchItem.getActionView();
            if (searchView != null) {
                searchView.setQueryHint(getString(R.string.hint_ricerca_spese));
                searchView.setMaxWidth(Integer.MAX_VALUE);
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        queryFiltroTesto = query != null ? query.trim().toLowerCase(java.util.Locale.getDefault()) : "";
                        applicaFiltriERaggruppa();
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        queryFiltroTesto = newText != null ? newText.trim().toLowerCase(java.util.Locale.getDefault()) : "";
                        applicaFiltriERaggruppa();
                        return true;
                    }
                });

                searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                    @Override
                    public boolean onMenuItemActionExpand(MenuItem item) {
                        return true;
                    }

                    @Override
                    public boolean onMenuItemActionCollapse(MenuItem item) {
                        queryFiltroTesto = "";
                        applicaFiltriERaggruppa();
                        return true;
                    }
                });
            }
        }

        binding.chipGroupCategorie.setOnCheckedStateChangeListener((group, checkedIds) -> {
            int checkedId = (checkedIds != null && !checkedIds.isEmpty()) ? checkedIds.get(0) : View.NO_ID;
            if (checkedId == View.NO_ID || checkedId == R.id.chip_cat_tutte) {
                categoriaSelezionata = getString(R.string.filtro_tutte);
            } else {
                Chip chip = group.findViewById(checkedId);
                categoriaSelezionata = chip != null ? chip.getText().toString() : getString(R.string.filtro_tutte);
            }
            applicaFiltriERaggruppa();
        });
    }

    private void setupObservers() {
        viewModel.getQuoteDellaScheda(schedaId).observe(getViewLifecycleOwner(), quote -> {
            this.quoteCache = (quote != null) ? quote : new ArrayList<>();
            aggiornaSaldi();
        });

        viewModel.getSpeseConDettagli(schedaId).observe(getViewLifecycleOwner(), (List<SpesaConDettagli> speseConDettagli) -> {
            if (binding == null) return;

            this.tutteSpeseRaw = (speseConDettagli != null) ? speseConDettagli : new ArrayList<>();

            this.speseCache.clear();
            for (SpesaConDettagli item : this.tutteSpeseRaw) {
                this.speseCache.add(item.getSpesa());
            }

            com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            com.example.paripariapp.data.repository.UserPreferencesRepository prefs = com.example.paripariapp.data.repository.UserPreferencesRepository.getInstance(requireContext());
            String myId = Partecipante.findCurrentUserId(partecipantiCache, currentUser, prefs, schedaId);

            double mieSpeseTotale = 0.0;
            String valutaScheda = valuta != null ? valuta : "EUR";
            if (myId != null) {
                for (SpesaConDettagli scd : this.tutteSpeseRaw) {
                    if (scd.getSpesa() != null && !com.example.paripariapp.util.CategoriaUtil.isCategoriaSaldi(scd.getSpesa().getCategoria())) {
                        String spesaId = scd.getSpesa().getId();
                        String valutaSpesa = scd.getSpesa().getValuta() != null ? scd.getSpesa().getValuta() : valutaScheda;
                        if (quoteCache != null) {
                            for (SpesaPartecipante q : quoteCache) {
                                if (q.getSpesaId().equals(spesaId) && q.getPartecipanteId().equals(myId)) {
                                    double quotaConvertita = CalcolatoreSaldi.convertiValuta(q.getQuota(), valutaSpesa, valutaScheda, requireContext());
                                    mieSpeseTotale += quotaConvertita;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            String valutaCorrente = valuta != null ? valuta : "EUR";
            binding.tvMieSpese.setText(String.format(Locale.getDefault(), "%.2f %s", mieSpeseTotale, valutaCorrente));

            applicaFiltriERaggruppa();
            aggiornaSaldi();
        });

        viewModel.getTotaleSpese(schedaId).observe(getViewLifecycleOwner(), totale -> {
            if (binding == null) return;
            double amount = (totale != null) ? totale : 0.0;
            binding.tvTotaleScheda.setText(com.example.paripariapp.util.ImportoUtil.formatta(amount, valuta));
        });

        viewModel.getPartecipanti(schedaId).observe(getViewLifecycleOwner(), partecipanti -> {
            if (binding == null || partecipanti == null) return;
            this.partecipantiCache = partecipanti;

            // Invia la lista aggiornata e ordinata con proprietario in cima al RecyclerView
            aggiornaMembriAdapter();

            if (partecipanti.isEmpty()) {
                binding.layoutEmptyMembri.getRoot().setVisibility(View.VISIBLE);
                binding.layoutEmptyMembri.tvEmptyTitle.setText(R.string.empty_membri_titolo);
                binding.layoutEmptyMembri.tvEmptyDesc.setText(R.string.empty_membri_desc);
                binding.recyclerMembri.setVisibility(View.GONE);
            } else {
                binding.layoutEmptyMembri.getRoot().setVisibility(View.GONE);
                binding.recyclerMembri.setVisibility(View.VISIBLE);
            }

            aggiornaSaldi();
        });

        viewModel.getSchedaById(schedaId).observe(getViewLifecycleOwner(), scheda -> {
            if (scheda != null) {
                this.schedaCorrente = scheda;
                if (scheda.getCodiceInvito() == null || scheda.getCodiceInvito().trim().isEmpty()) {
                    viewModel.assicuraCodiceInvito(scheda);
                }
                aggiornaMembriAdapter();
            } else {
                if (getActivity() != null && !getActivity().isFinishing() && isAdded()) {
                    if (binding != null) {
                        AppSnackbar.show(binding.getRoot(), R.string.msg_sei_stato_rimosso_dal_gruppo);
                        binding.getRoot().postDelayed(() -> {
                            if (getActivity() != null && !getActivity().isFinishing()) {
                                getActivity().finish();
                            }
                        }, 500);
                    } else {
                        getActivity().finish();
                    }
                }
            }
        });
    }

    private boolean mostraExMembri = false;

    private void aggiornaMembriAdapter() {
        if (membroAdapter == null || partecipantiCache == null) return;

        com.google.firebase.auth.FirebaseUser currentUser =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        Partecipante proprietario = Partecipante.trovaProprietario(schedaCorrente, partecipantiCache, currentUser);
        List<Partecipante> ordinati = Partecipante.ordinaConProprietarioInCima(schedaCorrente, partecipantiCache, currentUser);

        List<Partecipante> attivi = new ArrayList<>();
        List<Partecipante> exMembri = new ArrayList<>();

        for (Partecipante p : ordinati) {
            if (p.isAttivo()) {
                attivi.add(p);
            } else if (p.isExMembro()) {
                exMembri.add(p);
            }
        }

        if (binding != null) {
            if (exMembri.isEmpty()) {
                binding.btnToggleExMembri.setVisibility(View.GONE);
                binding.containerExMembri.setVisibility(View.GONE);
            } else {
                binding.btnToggleExMembri.setVisibility(View.VISIBLE);
                if (mostraExMembri) {
                    binding.btnToggleExMembri.setText(R.string.btn_nascondi_ex_membri);
                    binding.containerExMembri.setVisibility(View.VISIBLE);
                } else {
                    binding.btnToggleExMembri.setText(getString(R.string.btn_mostra_ex_membri, exMembri.size()));
                    binding.containerExMembri.setVisibility(View.GONE);
                }
            }
        }

        String currentSchedaId = (schedaCorrente != null) ? schedaCorrente.getId() : null;
        membroAdapter.setSchedaId(currentSchedaId);
        if (exMembroAdapter != null) {
            exMembroAdapter.setSchedaId(currentSchedaId);
        }

        com.example.paripariapp.data.repository.UserPreferencesRepository prefs =
                com.example.paripariapp.data.repository.UserPreferencesRepository.getInstance(requireContext());
        String myId = Partecipante.findCurrentUserId(partecipantiCache, currentUser, prefs, currentSchedaId);
        if (myId != null) {
            membroAdapter.setCurrentMyId(myId);
            if (exMembroAdapter != null) {
                exMembroAdapter.setCurrentMyId(myId);
            }
        }

        boolean isCapogruppo = (proprietario != null && isMe(proprietario));
        membroAdapter.setCapogruppo(isCapogruppo);
        if (exMembroAdapter != null) {
            exMembroAdapter.setCapogruppo(isCapogruppo);
        }

        membroAdapter.submitList(attivi);
        if (exMembroAdapter != null) {
            exMembroAdapter.submitList(exMembri);
        }
    }

    private boolean calcolaIsCapogruppo(Scheda scheda, List<Partecipante> partecipanti) {
        if (scheda == null || partecipanti == null || partecipanti.isEmpty()) return false;
        com.google.firebase.auth.FirebaseUser currentUser =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        Partecipante proprietario = Partecipante.trovaProprietario(scheda, partecipanti, currentUser);
        return proprietario != null && isMe(proprietario);
    }

    private void applicaFiltriERaggruppa() {
        List<SpesaConDettagli> filtrate = new ArrayList<>();
        String labelTutte = getString(R.string.filtro_tutte);
        String labelRimborsi = getString(R.string.cat_rimborsi);
        boolean isFiltroRimborsi = categoriaSelezionata.equalsIgnoreCase(labelRimborsi);

        for (SpesaConDettagli scd : tutteSpeseRaw) {
            Spesa s = scd.getSpesa();
            boolean isRimborso = com.example.paripariapp.util.CategoriaUtil.isCategoriaSaldi(s.getCategoria());

            boolean matchTesto = queryFiltroTesto.isEmpty() ||
                    s.getTitolo().toLowerCase(java.util.Locale.getDefault()).contains(queryFiltroTesto);

            boolean matchCat;
            if (isFiltroRimborsi) {
                matchCat = isRimborso;
            } else if (categoriaSelezionata.equalsIgnoreCase(labelTutte)) {
                matchCat = true;
            } else {
                String catLocalizzata = com.example.paripariapp.util.CategoriaUtil.getNomeLocalizzatoCategoria(requireContext(), s.getCategoria());
                matchCat = !isRimborso && (categoriaSelezionata.equalsIgnoreCase(s.getCategoria()) ||
                        (catLocalizzata != null && categoriaSelezionata.equalsIgnoreCase(catLocalizzata)));
            }

            if (matchTesto && matchCat) {
                filtrate.add(scd);
            }
        }

        filtrate.sort((a, b) -> Long.compare(b.getSpesa().getDataSpesa(), a.getSpesa().getDataSpesa()));

        List<SpesaListItem> itemsConHeader = new ArrayList<>();
        String ultimoHeader = "";

        for (SpesaConDettagli item : filtrate) {
            String dataHeader = dateFormatHeader.format(new Date(item.getSpesa().getDataSpesa()));
            if (!dataHeader.equals(ultimoHeader)) {
                ultimoHeader = dataHeader;
                itemsConHeader.add(new SpesaListItem(dataHeader));
            }
            itemsConHeader.add(new SpesaListItem(item));
        }

        adapter.submitList(itemsConHeader);

        if (itemsConHeader.isEmpty()) {
            binding.layoutEmptySpese.getRoot().setVisibility(View.VISIBLE);
            binding.layoutEmptySpese.tvEmptyTitle.setText(R.string.empty_spese_titolo);
            binding.layoutEmptySpese.tvEmptyDesc.setText(R.string.empty_spese_desc);
            binding.recyclerSpese.setVisibility(View.GONE);
        } else {
            binding.layoutEmptySpese.getRoot().setVisibility(View.GONE);
            binding.recyclerSpese.setVisibility(View.VISIBLE);
        }
    }

    private void setupFab() {
        binding.fabNuovaSpesa.setOnClickListener(v -> {
            NuovaSpesaFragment fragment = NuovaSpesaFragment.newInstance(schedaId, valuta);
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
        });
    }

    private void mostraDialogModificaNome() {
        final EditText input = new EditText(requireContext());
        input.setHint(getString(R.string.dialog_hint_nome_scheda));
        input.setText(titolo);
        input.setSelection(input.getText().length());
        input.setSingleLine(true);

        FrameLayout container = new FrameLayout(requireContext());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int margin = (int) (20 * getResources().getDisplayMetrics().density);
        params.leftMargin = margin;
        params.rightMargin = margin;
        input.setLayoutParams(params);
        container.addView(input);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.dialog_titolo_modifica_scheda))
                .setView(container)
                .setPositiveButton(getString(R.string.btn_salva), (dialog, which) -> {
                    String nuovoTitolo = input.getText().toString().trim();
                    if (!nuovoTitolo.isEmpty()) {
                        titolo = nuovoTitolo;
                        binding.toolbarDettaglio.setTitle(nuovoTitolo);
                        viewModel.aggiornaTitoloScheda(schedaId, nuovoTitolo);
                    }
                })
                .setNegativeButton(getString(R.string.btn_annulla), null)
                .show();
    }

    private void mostraDialogLasciaScheda() {
        com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        String mioId = Partecipante.findCurrentUserId(partecipantiCache, currentUser);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.dialog_titolo_lascia_scheda))
                .setMessage(getString(R.string.dialog_msg_lascia_scheda))
                .setPositiveButton(getString(R.string.btn_lascia), (dialog, which) -> {
                    if (mioId != null) {
                        viewModel.esciDalGruppo(schedaId, mioId);
                    } else {
                        viewModel.eliminaSchedaLocale(schedaId);
                    }
                    if (getActivity() != null) {
                        getActivity().finish();
                    }
                })
                .setNegativeButton(getString(R.string.btn_annulla), null)
                .show();
    }


    private boolean haSpeseODebiti(Partecipante p) {
        if (p == null) return false;
        List<TrasferimentoSaldo> trasferimenti = CalcolatoreSaldi.calcolaTrasferimenti(
                partecipantiCache,
                speseCache,
                quoteCache,
                valuta != null ? valuta : "EUR"
        );
        for (TrasferimentoSaldo t : trasferimenti) {
            if ((t.getDaPartecipanteId().equals(p.getId()) || t.getAPartecipanteId().equals(p.getId()))
                    && t.getImporto() > 0.01) {
                return true; // Ha debiti o crediti netti aperti non ancora saldati
            }
        }
        return false; // Saldo netto = € 0,00, rimozione consentita
    }

    private boolean isMe(Partecipante p) {
        if (p == null) return false;
        com.example.paripariapp.data.repository.UserPreferencesRepository prefs =
                com.example.paripariapp.data.repository.UserPreferencesRepository.getInstance(requireContext());
        String currentSchedaId = (schedaCorrente != null) ? schedaCorrente.getId() : null;
        if (currentSchedaId != null) {
            String mySavedId = prefs.getMyParticipantId(currentSchedaId);
            if (mySavedId != null) {
                return mySavedId.equals(p.getId());
            }
        }
        com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        return Partecipante.isCurrentUserParticipant(p, currentUser);
    }

    private void gestisciRimozioneMembro(Partecipante p, @Nullable androidx.appcompat.app.AlertDialog dialogToDismiss, @Nullable Runnable onMemberRemovedLocally) {
        if (p == null) return;
        boolean isMe = isMe(p);

        if (haSpeseODebiti(p)) {
            int msgRes = isMe ? R.string.msg_errore_uscita_debiti : R.string.msg_errore_rimozione_membro;
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.dialog_titolo_gestione_gruppo)
                    .setMessage(getString(msgRes))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        int titleRes = isMe ? R.string.titolo_esci_gruppo : R.string.btn_rimuovi;
        String message = isMe ? getString(R.string.msg_conferma_esci_gruppo)
                : getString(R.string.msg_conferma_rimuovi_membro, p.getNome());
        int btnPositiveRes = isMe ? R.string.btn_esci : R.string.btn_rimuovi;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(titleRes)
                .setMessage(message)
                .setPositiveButton(btnPositiveRes, (d, which) -> {
                    if (isMe) {
                        viewModel.esciDalGruppo(schedaId, p.getId());
                        if (dialogToDismiss != null && dialogToDismiss.isShowing()) {
                            dialogToDismiss.dismiss();
                        }
                        if (binding != null) {
                            AppSnackbar.show(binding.getRoot(), R.string.msg_sei_uscito_dal_gruppo);
                            binding.getRoot().postDelayed(() -> {
                                if (getActivity() != null && !getActivity().isFinishing()) {
                                    getActivity().finish();
                                }
                            }, 400);
                        } else if (getActivity() != null && !getActivity().isFinishing()) {
                            getActivity().finish();
                        }
                    } else {
                        viewModel.esciDalGruppo(schedaId, p.getId());
                        if (binding != null) {
                            AppSnackbar.show(binding.getRoot(), getString(R.string.msg_membro_rimosso, p.getNome()));
                        }
                        if (onMemberRemovedLocally != null) {
                            onMemberRemovedLocally.run();
                        }
                    }
                })
                .setNegativeButton(R.string.btn_annulla, null)
                .show();
    }




    public void mostraSceltaEsportazione() {
        String[] opzioni = {
                getString(R.string.opzione_esporta_csv),
                getString(R.string.opzione_esporta_pdf)
        };

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.titolo_dialog_esporta))
                .setItems(opzioni, (dialog, which) -> {
                    if (speseCache.isEmpty()) {
                        if (binding != null) {
                            AppSnackbar.show(binding.getRoot(), R.string.msg_nessuna_spesa_export);
                        }
                        return;
                    }

                    try {
                        if (which == 0) {
                            File fileCsv = EsportatoreDati.generaCsv(requireContext(), titolo, valuta, speseCache, partecipantiCache, quoteCache);
                            EsportatoreDati.condividiFile(requireContext(), fileCsv, "text/csv", getString(R.string.btn_esporta));
                        } else {
                            File filePdf = EsportatoreDati.generaPdf(requireContext(), titolo, valuta, speseCache, partecipantiCache, quoteCache);
                            EsportatoreDati.condividiFile(requireContext(), filePdf, "application/pdf", getString(R.string.btn_esporta));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        if (binding != null) {
                            AppSnackbar.show(binding.getRoot(), R.string.msg_errore_esportazione);
                        }
                    }
                })
                .setNegativeButton(getString(R.string.btn_annulla), null)
                .show();
    }

    @Override
    public void onDestroyView() {
        if (binding != null) {
            binding.recyclerSpese.setAdapter(null);
            binding.recyclerSaldi.setAdapter(null);
            binding.recyclerMembri.setAdapter(null);
            binding.recyclerExMembri.setAdapter(null);
        }
        super.onDestroyView();
        binding = null;
    }

    private void gestisciEliminazioneDefinitivaExMembro(Partecipante p) {
        if (p == null) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.titolo_elimina_definitivamente)
                .setMessage(getString(R.string.msg_conferma_elimina_definitivamente_ex_membro, p.getNome()))
                .setPositiveButton(R.string.btn_elimina_definitivamente, (dialog, which) -> {
                    viewModel.eliminaPartecipanteDefinitivamente(schedaId, p.getId());
                    if (binding != null) {
                        AppSnackbar.show(binding.getRoot(), getString(R.string.msg_ex_membro_eliminato, p.getNome()));
                    }
                })
                .setNegativeButton(R.string.btn_annulla, null)
                .show();
    }

    private void setupRecyclerMembri() {
        membroAdapter = new MembroAdapter();
        binding.recyclerMembri.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerMembri.setAdapter(membroAdapter);

        exMembroAdapter = new MembroAdapter();
        binding.recyclerExMembri.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerExMembri.setAdapter(exMembroAdapter);

        membroAdapter.setOnNomeModificatoListener((p, nuovoNome) -> {
            boolean isMe = isMe(p);
            boolean isCapo = calcolaIsCapogruppo(schedaCorrente, partecipantiCache);
            if (!isMe && !isCapo) {
                if (binding != null) {
                    AppSnackbar.show(binding.getRoot(), R.string.msg_permesso_negato_modifica_nome_altri);
                }
                return;
            }
            viewModel.aggiornaNomePartecipante(p.getId(), nuovoNome);
        });

        membroAdapter.setOnEliminaClickListener(p -> {
            if (p == null) return;
            gestisciRimozioneMembro(p, null, null);
        });

        exMembroAdapter.setOnEliminaClickListener(p -> {
            if (p == null) return;
            gestisciEliminazioneDefinitivaExMembro(p);
        });

        exMembroAdapter.setOnEliminaDefinitivamenteClickListener(p -> {
            if (p == null) return;
            gestisciEliminazioneDefinitivaExMembro(p);
        });

        membroAdapter.setOnRiattivaClickListener(p -> {
            viewModel.riattivaMembro(schedaId, p.getId());
            if (binding != null) {
                AppSnackbar.show(binding.getRoot(), getString(R.string.msg_membro_riattivato, p.getNome()));
            }
        });

        binding.btnToggleExMembri.setOnClickListener(v -> {
            mostraExMembri = !mostraExMembri;
            aggiornaMembriAdapter();
        });

        binding.btnAggiungiMembroTab.setOnClickListener(v -> mostraDialogOpzioniAggiungiMembro());
    }

    private void mostraDialogOpzioniAggiungiMembro() {
        String[] opzioni = new String[]{
                getString(R.string.btn_condividi_codice),
                getString(R.string.btn_aggiungi_partecipante_locale)
        };
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.titolo_aggiungi_membro)
                .setItems(opzioni, (dialog, which) -> {
                    if (which == 0) {
                        mostraDialogCodiceGruppo();
                    } else {
                        mostraDialogAggiungiSingoloMembro();
                    }
                })
                .setNegativeButton(R.string.btn_annulla, null)
                .show();
    }

    private void mostraDialogAggiungiSingoloMembro() {
        final EditText input = new EditText(requireContext());
        input.setHint(R.string.hint_nome_partecipante);
        input.setSingleLine(true);

        FrameLayout container = new FrameLayout(requireContext());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int margin = (int) (20 * getResources().getDisplayMetrics().density);
        params.leftMargin = margin;
        params.rightMargin = margin;
        input.setLayoutParams(params);
        container.addView(input);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.titolo_aggiungi_membro)
                .setView(container)
                .setPositiveButton(R.string.btn_salva, (dialog, which) -> {
                    String nome = input.getText().toString().trim();
                    if (!nome.isEmpty()) {
                        Partecipante nuovoP = new Partecipante(
                                UUID.randomUUID().toString(),
                                schedaId,
                                nome,
                                null,
                                SyncStatus.PENDING_INSERT
                        );
                        viewModel.aggiungiPartecipante(nuovoP);
                    }
                })
                .setNegativeButton(R.string.btn_annulla, null)
                .show();
    }

    private void mostraDialogCodiceGruppo() {
        if (getContext() == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        com.example.paripariapp.databinding.DialogCodiceGruppoBinding sheetBinding =
                com.example.paripariapp.databinding.DialogCodiceGruppoBinding.inflate(getLayoutInflater());
        dialog.setContentView(sheetBinding.getRoot());

        String codice = (schedaCorrente != null && schedaCorrente.getCodiceInvito() != null && !schedaCorrente.getCodiceInvito().isEmpty())
                ? schedaCorrente.getCodiceInvito() : "CARICO";

        if ("CARICO".equals(codice) && schedaCorrente != null) {
            viewModel.assicuraCodiceInvito(schedaCorrente);
        }
        sheetBinding.tvCodiceGruppo.setText(codice);

        // Genera asincronamente il QR Code con il logo PariPari al centro
        String linkInvito = com.example.paripariapp.util.CodiceInvitoUtil.generaLinkInvito(codice);
        sheetBinding.progressQr.setVisibility(View.VISIBLE);
        sheetBinding.ivQrCode.setVisibility(View.INVISIBLE);

        com.example.paripariapp.data.local.AppDatabase.databaseWriteExecutor.execute(() -> {
            if (getContext() == null) return;
            android.graphics.Bitmap qrBitmap = com.example.paripariapp.util.QrCodeUtil.generaQrCodeConLogo(
                    requireContext().getApplicationContext(), linkInvito, 600);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (isAdded() && getContext() != null && dialog.isShowing()) {
                    sheetBinding.progressQr.setVisibility(View.GONE);
                    if (qrBitmap != null) {
                        sheetBinding.ivQrCode.setImageBitmap(qrBitmap);
                        sheetBinding.ivQrCode.setVisibility(View.VISIBLE);
                    }
                }
            });
        });

        sheetBinding.btnCopiaCodice.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Codice gruppo", codice);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                AppSnackbar.show(sheetBinding.getRoot(), R.string.msg_codice_copiato);
            }
        });

        sheetBinding.btnCondividiLink.setOnClickListener(v -> {
            String titoloGruppo = (titolo != null && !titolo.isEmpty()) ? titolo : (schedaCorrente != null ? schedaCorrente.getTitolo() : "Gruppo");
            String messaggio = getString(R.string.msg_invito_condivisione, titoloGruppo, linkInvito, codice);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, titoloGruppo);
            shareIntent.putExtra(Intent.EXTRA_TEXT, messaggio);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.btn_condividi_link)));
        });

        sheetBinding.btnChiudiDialogCodice.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}