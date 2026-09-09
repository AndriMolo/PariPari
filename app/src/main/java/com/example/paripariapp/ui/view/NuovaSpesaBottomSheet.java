package com.example.paripariapp.ui.view;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.databinding.BottomSheetNuovaSpesaBinding;
import com.example.paripariapp.ui.viewmodel.DettaglioSchedaViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

/**
 * BottomSheet modale per la creazione rapida di una nuova spesa.
 */
public class NuovaSpesaBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_SCHEDA_ID = "arg_scheda_id";
    private static final String ARG_VALUTA = "arg_valuta";

    private BottomSheetNuovaSpesaBinding binding;
    private DettaglioSchedaViewModel viewModel;

    private String schedaId;
    private String valuta;
    private final List<Partecipante> partecipantiList = new ArrayList<>();

    public static NuovaSpesaBottomSheet newInstance(String schedaId, String valuta) {
        NuovaSpesaBottomSheet fragment = new NuovaSpesaBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_SCHEDA_ID, schedaId);
        args.putString(ARG_VALUTA, valuta);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            schedaId = getArguments().getString(ARG_SCHEDA_ID);
            valuta = getArguments().getString(ARG_VALUTA);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetNuovaSpesaBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(DettaglioSchedaViewModel.class);

        setupPartecipantiObserver();
        setupListeners();
    }

    private void setupPartecipantiObserver() {
        viewModel.getPartecipanti(schedaId).observe(getViewLifecycleOwner(), partecipanti -> {
            if (binding == null || partecipanti == null) return;
            partecipantiList.clear();
            partecipantiList.addAll(partecipanti);

            binding.chipGroupPagatore.removeAllViews();
            for (Partecipante p : partecipanti) {
                Chip chip = new Chip(requireContext());
                chip.setText(p.getNome());
                chip.setCheckable(true);
                chip.setTag(p.getId());

                // Di default seleziona "Io" o il primo
                if (getString(R.string.partecipante_io).equalsIgnoreCase(p.getNome()) || binding.chipGroupPagatore.getChildCount() == 0) {
                    chip.setChecked(true);
                }

                binding.chipGroupPagatore.addView(chip);
            }
        });
    }

    private void setupListeners() {
        binding.btnSalvaSpesa.setOnClickListener(v -> {
            String descrizione = binding.etDescrizioneSpesa.getText() != null
                    ? binding.etDescrizioneSpesa.getText().toString().trim() : "";
            String importoStr = binding.etImportoSpesa.getText() != null
                    ? binding.etImportoSpesa.getText().toString().trim().replace(',', '.') : "";

            binding.tilDescrizioneSpesa.setError(null);
            binding.tilImportoSpesa.setError(null);

            if (TextUtils.isEmpty(descrizione)) {
                binding.tilDescrizioneSpesa.setError(getString(R.string.error_descrizione_spesa));
                return;
            }

            double importo;
            try {
                importo = Double.parseDouble(importoStr);
                if (importo <= 0) {
                    binding.tilImportoSpesa.setError(getString(R.string.error_importo_spesa));
                    return;
                }
            } catch (NumberFormatException e) {
                binding.tilImportoSpesa.setError(getString(R.string.error_importo_spesa));
                return;
            }

            // Recupera ID pagatore selezionato
            int checkedId = binding.chipGroupPagatore.getCheckedChipId();
            if (checkedId == View.NO_ID) {
                Snackbar.make(binding.getRoot(), getString(R.string.error_nessun_pagatore), Snackbar.LENGTH_SHORT).show();
                return;
            }

            Chip checkedChip = binding.chipGroupPagatore.findViewById(checkedId);
            String pagatoDaId = checkedChip != null ? (String) checkedChip.getTag() : null;

            if (pagatoDaId == null && !partecipantiList.isEmpty()) {
                pagatoDaId = partecipantiList.get(0).getId();
            }

            // Inserimento spesa
            viewModel.aggiungiSpesa(schedaId, descrizione, importo, valuta, pagatoDaId, partecipantiList);

            if (getParentFragment() != null && getParentFragment().getView() != null) {
                Snackbar.make(getParentFragment().getView(), getString(R.string.msg_spesa_aggiunta), Snackbar.LENGTH_SHORT).show();
            }

            dismiss();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
