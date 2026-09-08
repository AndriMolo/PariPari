package com.example.paripariapp.ui.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.paripariapp.databinding.FragmentAccountBinding;

/**
 * Fragment della pagina "Account".
 * Mostra il profilo dell'utente con nome, email e statistiche sull'utilizzo.
 */
public class AccountFragment extends Fragment {

    private FragmentAccountBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAccountBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // TODO: caricare dati utente da ViewModel/Repository
        // binding.tvNomeUtente.setText(utente.getNome());
        // binding.tvEmailUtente.setText(utente.getEmail());

        binding.btnModificaProfilo.setOnClickListener(v -> {
            // TODO: aprire schermata di modifica profilo
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
