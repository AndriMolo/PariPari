package com.example.paripariapp.ui.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.paripariapp.databinding.FragmentSpeseBinding;

/**
 * Fragment della pagina "Schede Spese".
 * Mostra la lista delle schede spese e permette di crearne di nuove.
 */
public class SpeseFragment extends Fragment {

    private FragmentSpeseBinding binding;

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

        // TODO: collegare RecyclerView + Adapter (prossimo step)

        binding.fabNuovaScheda.setOnClickListener(v -> {
            // TODO: aprire dialog/activity per creare una nuova scheda spese
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Evita memory leak
    }
}
