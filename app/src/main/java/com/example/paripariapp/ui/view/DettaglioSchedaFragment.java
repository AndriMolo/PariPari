package com.example.paripariapp.ui.view;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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
import com.example.paripariapp.data.model.SpesaListItem;
import com.example.paripariapp.data.model.SpesaPartecipante;
import com.example.paripariapp.data.model.SyncStatus;
import com.example.paripariapp.data.model.TrasferimentoSaldo;
import com.example.paripariapp.databinding.FragmentDettaglioSchedaBinding;
import com.example.paripariapp.ui.viewmodel.DettaglioSchedaViewModel;
import com.example.paripariapp.util.CalcolatoreSaldi;
import com.example.paripariapp.util.EsportatoreDati;
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

    private String schedaId;
    private String titolo;
    private String valuta;

    private List<Partecipante> partecipantiCache = new ArrayList<>();
    private List<SpesaPartecipante> quoteCache = new ArrayList<>();
    private final List<Spesa> speseCache = new ArrayList<>();
    private List<SpesaConDettagli> tutteSpeseRaw = new ArrayList<>();
    private MembroAdapter membroAdapter;

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
    }

    private void aggiornaSaldi() {
        if (saldoAdapter != null) {
            List<TrasferimentoSaldo> trasferimenti = CalcolatoreSaldi.calcolaTrasferimenti(
                    partecipantiCache,
                    speseCache,
                    quoteCache,
                    valuta != null ? valuta : "EUR"
            );
            saldoAdapter.submitList(trasferimenti);
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
            } else if (itemId == R.id.action_invita) {
                condividiCodiceInvito();
                return true;
            } else if (itemId == R.id.action_modifica_titolo) {
                mostraDialogModificaNome();
                return true;
            } else if (itemId == R.id.action_elimina_scheda) {
                mostraDialogEliminaScheda();
                return true;
            }
            return false;
        });
    }

    private void setupRecyclerView() {
        adapter = new SpesaAdapter();
        binding.recyclerSpese.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerSpese.setAdapter(adapter);
    }

    private void setupRicercaEFiltri() {
        binding.inputRicercaSpese.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                queryFiltroTesto = s != null ? s.toString().trim().toLowerCase() : "";
                applicaFiltriERaggruppa();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Listener compatibile sia con versioni Material vecchie che recenti
        binding.chipGroupCategorie.setOnCheckedChangeListener((group, checkedId) -> {
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

            applicaFiltriERaggruppa();
            aggiornaSaldi();
        });

        viewModel.getTotaleSpese(schedaId).observe(getViewLifecycleOwner(), totale -> {
            if (binding == null) return;
            double amount = (totale != null) ? totale : 0.0;
            binding.tvTotaleScheda.setText(String.format(Locale.getDefault(), "%.2f %s", amount, valuta != null ? valuta : "EUR"));
        });

        viewModel.getPartecipanti(schedaId).observe(getViewLifecycleOwner(), partecipanti -> {
            if (binding == null || partecipanti == null) return;
            this.partecipantiCache = partecipanti;

            // Invia la lista aggiornata al RecyclerView della sezione Membri
            if (membroAdapter != null) {
                membroAdapter.submitList(partecipanti);
            }

            aggiornaSaldi();
        });
    }

    private void applicaFiltriERaggruppa() {
        List<SpesaConDettagli> filtrate = new ArrayList<>();
        String labelTutte = getString(R.string.filtro_tutte);

        for (SpesaConDettagli scd : tutteSpeseRaw) {
            Spesa s = scd.getSpesa();
            boolean matchTesto = queryFiltroTesto.isEmpty() ||
                    s.getTitolo().toLowerCase().contains(queryFiltroTesto);

            boolean matchCat = categoriaSelezionata.equalsIgnoreCase(labelTutte) ||
                    categoriaSelezionata.equalsIgnoreCase(s.getCategoria());

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

    private void mostraDialogEliminaScheda() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.dialog_titolo_elimina_scheda))
                .setMessage(getString(R.string.dialog_msg_elimina_scheda))
                .setPositiveButton(getString(R.string.btn_elimina), (dialog, which) -> {
                    viewModel.eliminaScheda(schedaId);
                    if (getActivity() != null) {
                        getActivity().finish();
                    }
                })
                .setNegativeButton(getString(R.string.btn_annulla), null)
                .show();
    }

    public void condividiCodiceInvito() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_invito_gruppo, null);
        TextView tvCodice = dialogView.findViewById(R.id.tv_codice_gruppo);
        View btnCopia = dialogView.findViewById(R.id.btn_copia_codice);
        View btnWhatsApp = dialogView.findViewById(R.id.btn_invia_whatsapp);

        tvCodice.setText(schedaId);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.titolo_dialog_invito)
                .setView(dialogView)
                .setPositiveButton(R.string.btn_chiudi, null)
                .create();

        // 1. Azione Copia negli appunti
        btnCopia.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                    requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Codice Gruppo", schedaId);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(requireContext(), R.string.msg_codice_copiato, Toast.LENGTH_SHORT).show();
            }
        });

        // 2. Azione WhatsApp mirata
        btnWhatsApp.setOnClickListener(v -> {
            String messaggio = getString(R.string.msg_invito_whatsapp, titolo, schedaId);
            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.setType("text/plain");
            sendIntent.putExtra(Intent.EXTRA_TEXT, messaggio);
            sendIntent.setPackage("com.whatsapp");

            try {
                startActivity(sendIntent);
            } catch (android.content.ActivityNotFoundException ex) {
                // Fallback nel caso WhatsApp standard non sia installato (es. WhatsApp Business o browser)
                try {
                    sendIntent.setPackage(null);
                    startActivity(Intent.createChooser(sendIntent, getString(R.string.condividi_con)));
                } catch (Exception e) {
                    Toast.makeText(requireContext(), R.string.msg_whatsapp_non_installato, Toast.LENGTH_SHORT).show();
                }
            }
        });

        dialog.show();
    }

    public void mostraDialogGestioneMembri() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_gestione_membri, null);
        EditText inputNuovo = dialogView.findViewById(R.id.input_nome_nuovo_membro);
        View btnAggiungi = dialogView.findViewById(R.id.bottone_conferma_aggiungi);
        LinearLayout contenitoreMembri = dialogView.findViewById(R.id.contenitore_membri);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setPositiveButton(R.string.btn_salva, null)
                .create();

        riempiListaMembri(contenitoreMembri);

        btnAggiungi.setOnClickListener(v -> {
            String nome = inputNuovo.getText() != null ? inputNuovo.getText().toString().trim() : "";
            if (!nome.isEmpty()) {
                Partecipante nuovoP = new Partecipante(
                        UUID.randomUUID().toString(),
                        schedaId,
                        nome,
                        null,
                        SyncStatus.PENDING_INSERT
                );
                viewModel.aggiungiPartecipante(nuovoP);
                inputNuovo.setText("");
                partecipantiCache.add(nuovoP);
                riempiListaMembri(contenitoreMembri);
            }
        });

        dialog.show();
    }

    private void riempiListaMembri(LinearLayout contenitore) {
        contenitore.removeAllViews();

        for (Partecipante p : partecipantiCache) {
            View row = getLayoutInflater().inflate(R.layout.item_membro_gestione, contenitore, false);
            TextView tvIniziale = row.findViewById(R.id.avatar_iniziale);
            TextView tvNome = row.findViewById(R.id.nome_partecipante);
            View btnCestino = row.findViewById(R.id.bottone_elimina);

            String nome = p.getNome();
            tvNome.setText(nome);
            tvIniziale.setText(!nome.isEmpty() ? String.valueOf(nome.charAt(0)).toUpperCase() : "?");

            btnCestino.setOnClickListener(v -> {
                boolean haSpese = false;
                for (Spesa s : speseCache) {
                    if (p.getId().equals(s.getPagatoDaId())) {
                        haSpese = true;
                        break;
                    }
                }

                if (haSpese) {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.dialog_titolo_gestione_gruppo)
                            .setMessage(getString(R.string.msg_errore_rimozione_membro))
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                } else {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.btn_rimuovi)
                            .setMessage(getString(R.string.msg_conferma_rimuovi_membro, p.getNome()))
                            .setPositiveButton(R.string.btn_rimuovi, (d, which) -> {
                                viewModel.eliminaPartecipante(p.getId());
                                partecipantiCache.remove(p);
                                riempiListaMembri(contenitore);
                            })
                            .setNegativeButton(R.string.btn_annulla, null)
                            .show();
                }
            });

            contenitore.addView(row);
        }
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
                        Toast.makeText(requireContext(), R.string.msg_nessuna_spesa_export, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    try {
                        if (which == 0) {
                            File fileCsv = EsportatoreDati.generaCsv(requireContext(), titolo, valuta, speseCache, partecipantiCache);
                            EsportatoreDati.condividiFile(requireContext(), fileCsv, "text/csv", getString(R.string.btn_esporta));
                        } else {
                            File filePdf = EsportatoreDati.generaPdf(requireContext(), titolo, valuta, speseCache, partecipantiCache);
                            EsportatoreDati.condividiFile(requireContext(), filePdf, "application/pdf", getString(R.string.btn_esporta));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(requireContext(), R.string.msg_errore_esportazione, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(getString(R.string.btn_annulla), null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
    private void setupRecyclerMembri() {
        membroAdapter = new MembroAdapter();
        binding.recyclerMembri.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerMembri.setAdapter(membroAdapter);

        membroAdapter.setOnEliminaClickListener(p -> {
            boolean haSpese = false;
            for (Spesa s : speseCache) {
                if (p.getId().equals(s.getPagatoDaId())) {
                    haSpese = true;
                    break;
                }
            }

            if (haSpese) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.dialog_titolo_gestione_gruppo)
                        .setMessage(getString(R.string.msg_errore_rimozione_membro))
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            } else {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.btn_rimuovi)
                        .setMessage(getString(R.string.msg_conferma_rimuovi_membro, p.getNome()))
                        .setPositiveButton(R.string.btn_rimuovi, (d, which) -> {
                            viewModel.eliminaPartecipante(p.getId());
                        })
                        .setNegativeButton(R.string.btn_annulla, null)
                        .show();
            }
        });

        binding.btnAggiungiMembroTab.setOnClickListener(v -> mostraDialogAggiungiSingoloMembro());
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
}