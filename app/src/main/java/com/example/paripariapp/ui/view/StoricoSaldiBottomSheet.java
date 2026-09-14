package com.example.paripariapp.ui.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.databinding.BottomSheetStoricoSaldiBinding;
import com.example.paripariapp.ui.viewmodel.SpeseViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * BottomSheet per visualizzare lo storico dei saldi e pareggi effettuati.
 * I registri di saldo sono non modificabili (mostrano un avviso di blocco in caso di tentativo di modifica).
 */
public class StoricoSaldiBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_SCHEDA_ID = "arg_scheda_id";

    private BottomSheetStoricoSaldiBinding binding;
    private StoricoSaldiAdapter adapter;
    private String schedaId;

    public static StoricoSaldiBottomSheet newInstance(@Nullable String schedaId) {
        StoricoSaldiBottomSheet sheet = new StoricoSaldiBottomSheet();
        if (schedaId != null) {
            Bundle args = new Bundle();
            args.putString(ARG_SCHEDA_ID, schedaId);
            sheet.setArguments(args);
        }
        return sheet;
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
        binding = BottomSheetStoricoSaldiBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new StoricoSaldiAdapter();
        binding.recyclerStoricoSaldi.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerStoricoSaldi.setAdapter(adapter);

        adapter.setOnItemClickListener(spesa -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.badge_saldato_effettuato)
                    .setMessage(R.string.msg_spesa_saldo_non_modificabile)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        });

        androidx.lifecycle.ViewModelProvider provider = new androidx.lifecycle.ViewModelProvider(this);
        SpeseViewModel viewModel = provider.get(SpeseViewModel.class);
        viewModel.getStoricoSaldi(schedaId).observe(getViewLifecycleOwner(), saldiList -> {
            if (binding == null) return;
            if (saldiList == null || saldiList.isEmpty()) {
                binding.layoutEmptyStorico.setVisibility(View.VISIBLE);
                binding.recyclerStoricoSaldi.setVisibility(View.GONE);
            } else {
                binding.layoutEmptyStorico.setVisibility(View.GONE);
                binding.recyclerStoricoSaldi.setVisibility(View.VISIBLE);
                adapter.submitList(saldiList);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
