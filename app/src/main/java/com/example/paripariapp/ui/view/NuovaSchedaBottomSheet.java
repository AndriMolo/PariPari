package com.example.paripariapp.ui.view;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.paripariapp.R;
import com.example.paripariapp.databinding.BottomSheetNuovaSchedaBinding;
import com.example.paripariapp.ui.viewmodel.SpeseViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

/**
 * BottomSheet per la creazione rapida ed essenziale di una Scheda Spese.
 * Include automaticamente "Io" come primo partecipante e permette di aggiungere
 * gli amici al volo tramite Chip rimovibili.
 */
public class NuovaSchedaBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetNuovaSchedaBinding binding;
    private SpeseViewModel viewModel;

    public static NuovaSchedaBottomSheet newInstance() {
        return new NuovaSchedaBottomSheet();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetNuovaSchedaBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(SpeseViewModel.class);

        setupInitialChip();
        setupListeners();
    }

    /**
     * Inserisce il chip predefinito "Io", non rimovibile.
     */
    private void setupInitialChip() {
        Chip chipIo = new Chip(requireContext());
        chipIo.setText(R.string.partecipante_io);
        chipIo.setCheckable(false);
        chipIo.setClickable(false);
        chipIo.setCloseIconVisible(false);
        binding.chipGroupPartecipanti.addView(chipIo);
    }

    private void setupListeners() {
        // Tasto "+" nell'input del nuovo partecipante
        binding.tilNuovoPartecipante.setEndIconOnClickListener(v -> aggiungiAmicoDaInput());

        // Tasto Invio/Fine sulla tastiera mentre si digita il nome dell'amico
        binding.etNuovoPartecipante.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                aggiungiAmicoDaInput();
                return true;
            }
            return false;
        });

        // Pulsante "Crea Scheda"
        binding.btnCreaScheda.setOnClickListener(v -> creaScheda());
    }

    private void aggiungiAmicoDaInput() {
        if (binding.etNuovoPartecipante.getText() == null) return;
        String nome = binding.etNuovoPartecipante.getText().toString().trim();
        if (TextUtils.isEmpty(nome)) return;

        // Evita duplicati visivi
        int count = binding.chipGroupPartecipanti.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = binding.chipGroupPartecipanti.getChildAt(i);
            if (child instanceof Chip) {
                Chip existing = (Chip) child;
                if (nome.equalsIgnoreCase(existing.getText().toString())) {
                    binding.etNuovoPartecipante.setText("");
                    return;
                }
            }
        }

        // Crea chip rimovibile per l'amico
        Chip chipAmico = new Chip(requireContext());
        chipAmico.setText(nome);
        chipAmico.setCloseIconVisible(true);
        chipAmico.setOnCloseIconClickListener(v -> binding.chipGroupPartecipanti.removeView(chipAmico));

        binding.chipGroupPartecipanti.addView(chipAmico);
        binding.etNuovoPartecipante.setText("");
    }

    private void creaScheda() {
        binding.tilNomeScheda.setError(null);

        String nomeScheda = binding.etNomeScheda.getText() != null
                ? binding.etNomeScheda.getText().toString().trim()
                : "";

        if (TextUtils.isEmpty(nomeScheda)) {
            binding.tilNomeScheda.setError(getString(R.string.error_nome_scheda_obbligatorio));
            return;
        }

        // Se l'utente ha digitato un nome nel campo partecipante senza premere '+' prima di creare, lo aggiungiamo
        if (binding.etNuovoPartecipante.getText() != null) {
            String pendingName = binding.etNuovoPartecipante.getText().toString().trim();
            if (!TextUtils.isEmpty(pendingName)) {
                aggiungiAmicoDaInput();
            }
        }

        // Raccoglie tutti i partecipanti inseriti nei chip
        List<String> nomiPartecipanti = new ArrayList<>();
        int count = binding.chipGroupPartecipanti.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = binding.chipGroupPartecipanti.getChildAt(i);
            if (child instanceof Chip) {
                nomiPartecipanti.add(((Chip) child).getText().toString().trim());
            }
        }

        viewModel.creaScheda(nomeScheda, viewModel.getDefaultCurrency(), nomiPartecipanti);
        dismiss();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Prevenzione memory leak
    }
}
