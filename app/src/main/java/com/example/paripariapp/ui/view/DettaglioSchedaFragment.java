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
import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.databinding.FragmentDettaglioSchedaBinding;
import com.example.paripariapp.ui.viewmodel.DettaglioSchedaViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fragment per il dettaglio della scheda spese:
 * visualizza il totale aggregato, la lista delle spese e permette l'aggiunta di nuove spese.
 */
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
    }

    private void setupRecyclerView() {
        adapter = new SpesaAdapter();
        binding.recyclerSpese.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerSpese.setAdapter(adapter);
    }

    private void setupObservers() {
        // Spese
        viewModel.getSpese(schedaId).observe(getViewLifecycleOwner(), spese -> {
            if (binding == null) return;
            boolean hasSpese = spese != null && !spese.isEmpty();
            binding.layoutEmptySpese.getRoot().setVisibility(hasSpese ? View.GONE : View.VISIBLE);
            binding.recyclerSpese.setVisibility(hasSpese ? View.VISIBLE : View.GONE);
            adapter.submitList(spese);
        });

        // Totale Spese
        viewModel.getTotaleSpese(schedaId).observe(getViewLifecycleOwner(), totale -> {
            if (binding == null) return;
            double amount = totale != null ? totale : 0.0;
            binding.tvTotaleScheda.setText(String.format(Locale.getDefault(), "%.2f %s", amount, valuta != null ? valuta : "EUR"));
        });

        // Partecipanti
        viewModel.getPartecipanti(schedaId).observe(getViewLifecycleOwner(), partecipanti -> {
            if (binding == null || partecipanti == null) return;
            adapter.setPartecipanti(partecipanti);

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
            NuovaSpesaBottomSheet bottomSheet = NuovaSpesaBottomSheet.newInstance(schedaId, valuta);
            bottomSheet.show(getChildFragmentManager(), "NuovaSpesaBottomSheet");
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
