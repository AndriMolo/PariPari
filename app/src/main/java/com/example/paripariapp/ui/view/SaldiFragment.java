package com.example.paripariapp.ui.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.paripariapp.databinding.FragmentSaldiBinding;

/**
 * Fragment della pagina "Saldi".
 * Mostra il saldo netto dell'utente, chi gli deve soldi e chi deve a lui.
 */
public class SaldiFragment extends Fragment {

    private FragmentSaldiBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSaldiBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // TODO: collegare ViewModel per calcolare e mostrare i saldi in tempo reale
        // binding.tvSaldoNetto.setText("...");
        // binding.recyclerDevoRicevere.setAdapter(...);
        // binding.recyclerDevoPagare.setAdapter(...);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Evita memory leak
    }
}
