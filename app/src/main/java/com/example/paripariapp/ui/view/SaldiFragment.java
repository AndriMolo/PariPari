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
import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.Scheda;
import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.data.model.SpesaPartecipante;
import com.example.paripariapp.data.model.TrasferimentoSaldo;
import com.example.paripariapp.databinding.FragmentSaldiBinding;
import com.example.paripariapp.ui.viewmodel.SpeseViewModel;
import com.example.paripariapp.util.CalcolatoreSaldi;

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

        caricaSaldiDaTutteLeSchede();
    }

    private void caricaSaldiDaTutteLeSchede() {
        viewModel.getTutteLeSchede().observe(getViewLifecycleOwner(), schede -> {
            if (schede == null || schede.isEmpty()) {
                aggiornaUi(0, 0, new ArrayList<>());
                return;
            }

            tuttiIBilanci.clear();
            double[] totaleRicevere = {0.0};
            double[] totaleDare = {0.0};

            for (Scheda scheda : schede) {
                String idScheda = scheda.getId();
                String titoloScheda = scheda.getTitolo();
                String valuta = scheda.getValutaPredefinita() != null ? scheda.getValutaPredefinita() : "EUR";

                viewModel.getPartecipanti(idScheda).observe(getViewLifecycleOwner(), parti -> {
                    viewModel.getSpese(idScheda).observe(getViewLifecycleOwner(), spese -> {
                        viewModel.getQuoteDellaScheda(idScheda).observe(getViewLifecycleOwner(), quote -> {
                            if (parti == null || spese == null || quote == null) return;

                            List<TrasferimentoSaldo> trasferimenti = CalcolatoreSaldi.calcolaTrasferimenti(parti, spese, quote, valuta);

                            String mioId = null;
                            for (Partecipante p : parti) {
                                String n = p.getNome().trim().toLowerCase();
                                if (n.equals("io") || n.equals("me")) {
                                    mioId = p.getId();
                                    break;
                                }
                            }

                            if (mioId != null) {
                                for (TrasferimentoSaldo t : trasferimenti) {
                                    if (t.getDaId().equals(mioId)) {
                                        totaleDare[0] += t.getImporto();
                                        tuttiIBilanci.add(new BilancioPersonaItem(t.getANome(), titoloScheda, -t.getImporto(), valuta));
                                    } else if (t.getAId().equals(mioId)) {
                                        totaleRicevere[0] += t.getImporto();
                                        tuttiIBilanci.add(new BilancioPersonaItem(t.getDaNome(), titoloScheda, t.getImporto(), valuta));
                                    }
                                }
                            }
                            aggiornaUi(totaleRicevere[0], totaleDare[0], tuttiIBilanci);
                        });
                    });
                });
            }
        });
    }

    private void aggiornaUi(double ricevere, double dare, List<BilancioPersonaItem> lista) {
        if (binding == null) return;
        binding.tvTotaleDaRicevere.setText(String.format(Locale.getDefault(), "%.2f €", ricevere));
        binding.tvTotaleDaDare.setText(String.format(Locale.getDefault(), "%.2f €", dare));
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