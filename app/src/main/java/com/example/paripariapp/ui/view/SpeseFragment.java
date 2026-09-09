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
import com.example.paripariapp.databinding.FragmentSpeseBinding;
import com.example.paripariapp.ui.viewmodel.SpeseViewModel;

/**
 * Fragment della pagina "Schede Spese".
 * Mostra la lista delle schede spese, l'empty state se vuota,
 * e apre il BottomSheet per la creazione rapida di una nuova scheda.
 */
public class SpeseFragment extends Fragment {

    private FragmentSpeseBinding binding;
    private SpeseViewModel viewModel;
    private SchedaAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSpeseBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(SpeseViewModel.class);

        setupRecyclerView();
        setupObservers();
        setupListeners();
        setupEmptyState();
    }

    private void setupRecyclerView() {
        adapter = new SchedaAdapter(scheda -> {
            if (scheda != null && getParentFragmentManager() != null) {
                getParentFragmentManager().beginTransaction()
                        .setCustomAnimations(
                                android.R.anim.fade_in,
                                android.R.anim.fade_out,
                                android.R.anim.fade_in,
                                android.R.anim.fade_out
                        )
                        .replace(R.id.fragment_container, DettaglioSchedaFragment.newInstance(
                                scheda.getId(),
                                scheda.getTitolo(),
                                scheda.getValutaPredefinita()
                        ))
                        .addToBackStack("dettaglio_scheda")
                        .commit();
            }
        });
        binding.recyclerSchede.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerSchede.setAdapter(adapter);
    }

    private void setupEmptyState() {
        binding.layoutEmptyState.tvEmptyTitle.setText(R.string.empty_schede_titolo);
        binding.layoutEmptyState.tvEmptyDesc.setText(R.string.empty_schede_desc);
    }

    private void setupObservers() {
        viewModel.getSchede().observe(getViewLifecycleOwner(), schede -> {
            if (schede == null || schede.isEmpty()) {
                binding.layoutEmptyState.getRoot().setVisibility(View.VISIBLE);
                binding.recyclerSchede.setVisibility(View.GONE);
            } else {
                binding.layoutEmptyState.getRoot().setVisibility(View.GONE);
                binding.recyclerSchede.setVisibility(View.VISIBLE);
                adapter.submitList(schede);
            }
        });
    }

    private void setupListeners() {
        binding.fabNuovaScheda.setOnClickListener(v -> {
            NuovaSchedaBottomSheet.newInstance().show(getChildFragmentManager(), "NuovaSchedaBottomSheet");
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Evita memory leak
    }
}
