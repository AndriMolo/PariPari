package com.example.paripariapp.ui.view;

import android.content.Intent;
import android.os.Bundle;
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

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.data.model.SpesaConDettagli;
import com.example.paripariapp.databinding.FragmentDettaglioSchedaBinding;
import com.example.paripariapp.ui.viewmodel.DettaglioSchedaViewModel;
import com.example.paripariapp.util.EsportatoreDati;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DettaglioSchedaFragment extends Fragment {

    private static final String ARG_SCHEDA_ID = "arg_scheda_id";
    private static final String ARG_TITOLO = "arg_titolo";
    private static final String ARG_VALUTA = "arg_valuta";

    private FragmentDettaglioSchedaBinding binding;
    private DettaglioSchedaViewModel viewModel;
    private SpesaAdapter adapter;

    private String schedaId;
    private String titolo;
    private String valuta;

    private List<Partecipante> partecipantiCache = new ArrayList<>();
    private List<Spesa> speseCache = new ArrayList<>();

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

        setupToolbar();
        setupActionButtons();
        setupRecyclerView();
        setupEmptyState();
        setupObservers();
        setupFab();
    }

    private void setupEmptyState() {
        binding.layoutEmptySpese.tvEmptyTitle.setText(R.string.empty_spese_titolo);
        binding.layoutEmptySpese.tvEmptyDesc.setText(R.string.empty_spese_desc);
    }

    private void setupToolbar() {
        if (titolo != null) {
            binding.toolbarDettaglio.setTitle(titolo);
        }
        binding.toolbarDettaglio.setNavigationOnClickListener(v -> {
            if (getParentFragmentManager() != null) {
                getParentFragmentManager().popBackStack();
            }
        });

        binding.toolbarDettaglio.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_modifica_titolo) {
                mostraDialogModificaNome();
                return true;
            } else if (itemId == R.id.action_elimina_scheda) {
                mostraDialogEliminaScheda();
                return true;
            }
            return false;
        });
    }

    private void setupActionButtons() {
        binding.btnInvita.setOnClickListener(v -> condividiCodiceInvito());
        binding.btnGestisciGruppo.setOnClickListener(v -> mostraDialogGestioneMembri());
        binding.btnEsporta.setOnClickListener(v -> mostraSceltaEsportazione());
    }

    private void setupRecyclerView() {
        adapter = new SpesaAdapter();
        binding.recyclerSpese.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerSpese.setAdapter(adapter);
    }

    private void setupObservers() {
        viewModel.getSpeseConDettagli(schedaId).observe(getViewLifecycleOwner(), (List<SpesaConDettagli> speseConDettagli) -> {
            if (binding == null) return;

            this.speseCache.clear();
            if (speseConDettagli != null) {
                for (SpesaConDettagli item : speseConDettagli) {
                    this.speseCache.add(item.getSpesa());
                }
            }

            boolean hasSpese = !this.speseCache.isEmpty();
            binding.layoutEmptySpese.getRoot().setVisibility(hasSpese ? View.GONE : View.VISIBLE);
            binding.recyclerSpese.setVisibility(hasSpese ? View.VISIBLE : View.GONE);

            adapter.submitList(speseConDettagli);
        });

        viewModel.getTotaleSpese(schedaId).observe(getViewLifecycleOwner(), totale -> {
            if (binding == null) return;
            double amount = (totale != null) ? totale : 0.0;
            binding.tvTotaleScheda.setText(String.format(Locale.getDefault(), "%.2f %s", amount, valuta != null ? valuta : "EUR"));
        });

        viewModel.getPartecipanti(schedaId).observe(getViewLifecycleOwner(), partecipanti -> {
            if (binding == null || partecipanti == null) return;
            this.partecipantiCache = partecipanti;

            List<String> nomi = new ArrayList<>();
            for (Partecipante p : partecipanti) {
                nomi.add(p.getNome());
            }
            String partecipantiStr = String.join(", ", nomi);
            binding.tvListaPartecipanti.setText(getString(R.string.label_partecipanti_formato, partecipantiStr));
        });
    }

    private void setupFab() {
        binding.fabNuovaSpesa.setOnClickListener(v -> {
            NuovaSpesaFragment fragment = NuovaSpesaFragment.newInstance(schedaId, valuta);
            getParentFragmentManager().beginTransaction()
                    .replace(((ViewGroup) requireView().getParent()).getId(), fragment)
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
                    if (getParentFragmentManager() != null) {
                        getParentFragmentManager().popBackStack();
                    }
                })
                .setNegativeButton(getString(R.string.btn_annulla), null)
                .show();
    }

    private void condividiCodiceInvito() {
        String testoMessaggio = getString(R.string.msg_codice_invito_body, titolo, schedaId);

        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.msg_codice_invito_titolo));
        sendIntent.putExtra(Intent.EXTRA_TEXT, testoMessaggio);

        startActivity(Intent.createChooser(sendIntent, getString(R.string.condividi_con)));
    }

    private void mostraDialogGestioneMembri() {
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
                        java.util.UUID.randomUUID().toString(),
                        schedaId,
                        nome,
                        null,
                        com.example.paripariapp.data.model.SyncStatus.PENDING_INSERT
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

    private void mostraSceltaEsportazione() {
        String[] opzioni = {
                getString(R.string.opzione_esporta_csv),
                getString(R.string.opzione_esporta_pdf)
        };

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.titolo_dialog_esporta))
                .setItems(opzioni, (dialog, which) -> {
                    if (speseCache == null || speseCache.isEmpty()) {
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
}