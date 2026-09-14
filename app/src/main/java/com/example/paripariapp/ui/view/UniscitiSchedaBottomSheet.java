package com.example.paripariapp.ui.view;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.MembroGruppoPreview;
import com.example.paripariapp.data.remote.FirestoreSyncManager;
import com.example.paripariapp.data.repository.PariPariRepository;
import com.example.paripariapp.databinding.BottomSheetUniscitiSchedaBinding;
import com.example.paripariapp.ui.viewmodel.SpeseViewModel;
import com.example.paripariapp.util.CodiceInvitoUtil;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * BottomSheet interattivo per unirsi a una scheda spese tramite codice invito.
 * Mostra in modo pulito e diretto l'elenco dei membri della scheda:
 * - I membri autenticati sono disabilitati in grigio (posti già occupati).
 * - I membri anonimi/offline sono evidenziati e cliccabili con un tocco.
 * - In coda è sempre presente l'opzione per aggiungersi come nuovo membro.
 */
public class UniscitiSchedaBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_CODICE = "arg_codice_invito";

    private BottomSheetUniscitiSchedaBinding binding;
    private SpeseViewModel viewModel;
    private MembriUnioneAdapter adapter;

    private String codiceCorrente;
    private FirestoreSyncManager.GruppoPreview previewGruppo;

    private MembroGruppoPreview membroSelezionatoPerSubentro = null;
    private boolean isNuovoMembroSelezionato = false;

    public static UniscitiSchedaBottomSheet newInstance() {
        return new UniscitiSchedaBottomSheet();
    }

    public static UniscitiSchedaBottomSheet newInstance(String codicePredefinito) {
        UniscitiSchedaBottomSheet sheet = new UniscitiSchedaBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_CODICE, codicePredefinito);
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetUniscitiSchedaBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(SpeseViewModel.class);

        setupDefaults();
        setupRecyclerView();
        setupListeners();

        // Se un codice è stato passato come argomento (es. da deep link), avvia direttamente la ricerca
        if (getArguments() != null) {
            String codiceArg = getArguments().getString(ARG_CODICE);
            if (!TextUtils.isEmpty(codiceArg)) {
                binding.etCodice.setText(codiceArg);
                avviaRicercaGruppo(codiceArg);
            }
        }
    }

    private void setupDefaults() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String defaultNome = (user != null && !TextUtils.isEmpty(user.getDisplayName()))
                ? user.getDisplayName() : "Io";
        binding.etNomeNuovo.setText(defaultNome);
    }

    private void setupRecyclerView() {
        adapter = new MembriUnioneAdapter(membro -> {
            membroSelezionatoPerSubentro = membro;
            isNuovoMembroSelezionato = false;
            aggiornaStatoNuovoMembro(false);

            if (membro != null) {
                binding.btnConfermaUnione.setEnabled(true);
                binding.btnConfermaUnione.setText(getString(R.string.btn_entra_come, membro.getNome()));
            } else {
                binding.btnConfermaUnione.setEnabled(false);
                binding.btnConfermaUnione.setText(R.string.btn_seleziona_chi_sei);
            }
        });

        binding.rvMembriGruppo.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvMembriGruppo.setAdapter(adapter);
    }

    private void setupListeners() {
        binding.btnAnnullaStep1.setOnClickListener(v -> dismiss());
        binding.btnAnnullaUnione.setOnClickListener(v -> dismiss());

        binding.btnCercaScheda.setOnClickListener(v -> {
            String codice = binding.etCodice.getText() != null
                    ? binding.etCodice.getText().toString().trim() : "";
            avviaRicercaGruppo(codice);
        });

        // Click sull'opzione "Aggiungiti come nuovo membro"
        binding.cardNuovoMembro.setOnClickListener(v -> {
            adapter.clearSelection();
            membroSelezionatoPerSubentro = null;
            isNuovoMembroSelezionato = true;
            aggiornaStatoNuovoMembro(true);

            binding.btnConfermaUnione.setEnabled(true);
            binding.btnConfermaUnione.setText(R.string.btn_aggiungiti_gruppo);
        });

        binding.btnConfermaUnione.setOnClickListener(v -> eseguiUnione());
    }

    private void aggiornaStatoNuovoMembro(boolean selezionato) {
        binding.rbNuovoMembro.setChecked(selezionato);
        binding.tilNomeNuovo.setVisibility(selezionato ? View.VISIBLE : View.GONE);

        int colorPrimary = MaterialColors.getColor(binding.getRoot(), androidx.appcompat.R.attr.colorPrimary);
        int colorOutlineVariant = MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorOutlineVariant);
        int colorSurface = MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorSurface);
        int colorSurfaceVariant = MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorSurfaceVariant);

        int strokeWidth = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                selezionato ? 2 : 1,
                getResources().getDisplayMetrics()
        );

        binding.cardNuovoMembro.setStrokeWidth(strokeWidth);
        binding.cardNuovoMembro.setStrokeColor(selezionato ? colorPrimary : colorOutlineVariant);
        binding.cardNuovoMembro.setCardBackgroundColor(selezionato ? colorSurfaceVariant : colorSurface);
    }

    private void avviaRicercaGruppo(String codiceInput) {
        String cleanCode = CodiceInvitoUtil.normalizzaCodice(codiceInput);
        if (cleanCode == null || cleanCode.length() != 6) {
            binding.tilCodice.setError(getString(R.string.error_codice_non_valido));
            return;
        }

        nascondiTastiera();
        binding.tilCodice.setError(null);
        binding.progressCaricamento.setVisibility(View.VISIBLE);
        binding.btnCercaScheda.setEnabled(false);

        codiceCorrente = cleanCode;

        viewModel.recuperaAnteprimaGruppo(cleanCode, new FirestoreSyncManager.OnPreviewGruppoCallback() {
            @Override
            public void onPreviewLoaded(FirestoreSyncManager.GruppoPreview preview) {
                if (binding == null) return;
                binding.progressCaricamento.setVisibility(View.GONE);
                binding.btnCercaScheda.setEnabled(true);

                previewGruppo = preview;
                mostraStepSceltaMembro(preview);
            }

            @Override
            public void onError(String errore) {
                if (binding == null) return;
                binding.progressCaricamento.setVisibility(View.GONE);
                binding.btnCercaScheda.setEnabled(true);

                Snackbar.make(binding.getRoot(), errore, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void mostraStepSceltaMembro(FirestoreSyncManager.GruppoPreview preview) {
        binding.containerStepCodice.setVisibility(View.GONE);
        binding.containerStepScelta.setVisibility(View.VISIBLE);
        binding.btnConfermaUnione.setVisibility(View.VISIBLE);

        binding.tvTitoloSchedaTrovata.setText(preview.getTitolo());
        String infoMembri = "Valuta: " + preview.getValutaPredefinita();
        binding.tvValutaEMembri.setText(infoMembri);

        adapter.setMembri(preview.getMembri());

        if (preview.getMembriSostituibili().isEmpty()) {
            // Nessun membro sostituibile: preseleziona direttamente "Nuovo membro"
            binding.cardNuovoMembro.performClick();
        } else {
            // Reset iniziale: l'utente deve selezionare chi è
            membroSelezionatoPerSubentro = null;
            isNuovoMembroSelezionato = false;
            aggiornaStatoNuovoMembro(false);
            binding.btnConfermaUnione.setEnabled(false);
            binding.btnConfermaUnione.setText(R.string.btn_seleziona_chi_sei);
        }
    }

    private void eseguiUnione() {
        if (previewGruppo == null || codiceCorrente == null) return;

        String claimedPartecipanteId = null;
        String nomeScelto = null;

        if (membroSelezionatoPerSubentro != null) {
            // Subentro in un membro anonimo
            claimedPartecipanteId = membroSelezionatoPerSubentro.getId();
            nomeScelto = membroSelezionatoPerSubentro.getNome();
        } else if (isNuovoMembroSelezionato) {
            // Aggiunta nuovo membro
            nomeScelto = binding.etNomeNuovo.getText() != null
                    ? binding.etNomeNuovo.getText().toString().trim() : "";
            if (TextUtils.isEmpty(nomeScelto)) {
                binding.tilNomeNuovo.setError(getString(R.string.error_nome_obbligatorio));
                return;
            }
            binding.tilNomeNuovo.setError(null);
        } else {
            return;
        }

        binding.progressCaricamento.setVisibility(View.VISIBLE);
        binding.btnConfermaUnione.setEnabled(false);
        binding.btnAnnullaUnione.setEnabled(false);

        viewModel.uniscitiASchedaConClaim(codiceCorrente, claimedPartecipanteId, nomeScelto, new PariPariRepository.OnJoinSchedaCallback() {
            @Override
            public void onSuccess(String schedaId, String titolo) {
                if (!isAdded() || getContext() == null) return;
                Toast.makeText(requireContext(), getString(R.string.msg_unione_successo, titolo), Toast.LENGTH_SHORT).show();

                Intent intent = new Intent(requireContext(), DettaglioSchedaActivity.class);
                intent.putExtra(DettaglioSchedaActivity.EXTRA_SCHEDA_ID, schedaId);
                intent.putExtra(DettaglioSchedaActivity.EXTRA_TITOLO, titolo);
                startActivity(intent);

                dismiss();
            }

            @Override
            public void onError(String errore) {
                if (binding == null) return;
                binding.progressCaricamento.setVisibility(View.GONE);
                binding.btnConfermaUnione.setEnabled(true);
                binding.btnAnnullaUnione.setEnabled(true);
                Snackbar.make(binding.getRoot(), errore, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void nascondiTastiera() {
        if (getView() != null) {
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
