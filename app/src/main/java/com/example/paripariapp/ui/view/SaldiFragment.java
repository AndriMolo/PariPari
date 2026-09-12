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
import com.example.paripariapp.data.model.BilancioPersonaItem;
import com.example.paripariapp.databinding.FragmentSaldiBinding;
import com.example.paripariapp.ui.viewmodel.SpeseViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SaldiFragment extends Fragment {

    private FragmentSaldiBinding binding;
    private SpeseViewModel viewModel;
    private BilancioPersonaAdapter adapter;

    private final List<BilancioPersonaItem> tuttiIBilanci = new ArrayList<>();
    private int filtroCorrente = 0; // 0 = Tutti, 1 = Da ricevere, 2 = Da dare

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSaldiBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(SpeseViewModel.class);

        adapter = new BilancioPersonaAdapter();
        binding.recyclerBilanci.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerBilanci.setAdapter(adapter);

        // Click Card Verde: mostra solo chi ti deve soldi
        binding.cardDaRicevere.setOnClickListener(v -> {
            filtroCorrente = (filtroCorrente == 1) ? 0 : 1;
            applicaFiltro();
        });

        // Click Card Rossa: mostra solo a chi devi dare soldi
        binding.cardDaDare.setOnClickListener(v -> {
            filtroCorrente = (filtroCorrente == 2) ? 0 : 2;
            applicaFiltro();
        });

        osservaSaldi();
    }

    private void osservaSaldi() {
        viewModel.getRisultatoSaldi().observe(getViewLifecycleOwner(), risultato -> {
            if (risultato == null) return;
            tuttiIBilanci.clear();
            tuttiIBilanci.addAll(risultato.getBilanci());
            aggiornaUi(risultato.getTotaleRicevere(), risultato.getTotaleDare());
        });
    }

    private void aggiornaUi(double ricevere, double dare) {
        if (binding == null) return;
        String valuta = viewModel.getDefaultCurrency();
        binding.tvTotaleDaRicevere.setText(String.format(Locale.getDefault(), "%.2f %s", ricevere, valuta));
        binding.tvTotaleDaDare.setText(String.format(Locale.getDefault(), "%.2f %s", dare, valuta));
        applicaFiltro();
    }

    private void applicaFiltro() {
        List<BilancioPersonaItem> filtrate = new ArrayList<>();
        for (BilancioPersonaItem item : tuttiIBilanci) {
            if (filtroCorrente == 1 && item.isCredito()) {
                filtrate.add(item);
            } else if (filtroCorrente == 2 && !item.isCredito()) {
                filtrate.add(item);
            } else if (filtroCorrente == 0) {
                filtrate.add(item);
            }
        }

        boolean vuoto = filtrate.isEmpty();
        if (vuoto) {
            binding.tvVuoto.setText(R.string.empty_saldi_titolo);
        }
        binding.tvVuoto.setVisibility(vuoto ? View.VISIBLE : View.GONE);
        binding.recyclerBilanci.setVisibility(vuoto ? View.GONE : View.VISIBLE);
        adapter.submitList(filtrate);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}