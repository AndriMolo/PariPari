package com.example.paripariapp.ui.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.paripariapp.data.repository.PariPariRepository;
import com.example.paripariapp.databinding.BottomSheetAvatarUtenteBinding;
import com.example.paripariapp.ui.viewmodel.SpeseViewModel;
import com.example.paripariapp.util.AppSnackbar;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class BottomSheetAvatarUtente extends BottomSheetDialogFragment {

    private BottomSheetAvatarUtenteBinding binding;
    private SpeseViewModel viewModel;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickMediaLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    caricaFotoDaUri(uri);
                }
            });

    public static BottomSheetAvatarUtente newInstance() {
        return new BottomSheetAvatarUtente();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetAvatarUtenteBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(SpeseViewModel.class);

        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.isAnonymous()) {
            AppSnackbar.show(requireActivity().findViewById(android.R.id.content), "Registrati o accedi per personalizzare la tua foto profilo");
            dismiss();
            return;
        }

        setupListeners();
    }

    private void setupListeners() {
        binding.cardCaricaAvatar.setOnClickListener(v -> {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Consenso Foto Profilo")
                    .setMessage("PariPari richiede il consenso per accedere alla galleria al fine di selezionare l'immagine del tuo profilo.")
                    .setPositiveButton("Consenti", (dialog, which) -> {
                        pickMediaLauncher.launch(new PickVisualMediaRequest.Builder()
                                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                                .build());
                    })
                    .setNegativeButton("Annulla", null)
                    .show();
        });

        binding.btnRimuoviAvatar.setOnClickListener(v -> {
            viewModel.getRepository().aggiornaAvatarUtente(null);
            dismiss();
        });
    }

    private void caricaFotoDaUri(android.net.Uri uri) {
        if (binding == null) return;
        binding.containerCaricamentoAvatar.setVisibility(View.VISIBLE);
        binding.cardCaricaAvatar.setEnabled(false);

        viewModel.getRepository().uploadAvatarUtente(requireContext().getApplicationContext(), uri, new PariPariRepository.OnUploadCallback() {
            @Override
            public void onSuccess(String url) {
                if (isAdded() && binding != null) {
                    binding.containerCaricamentoAvatar.setVisibility(View.GONE);
                    dismiss();
                }
            }

            @Override
            public void onError(String errore) {
                if (isAdded() && binding != null) {
                    binding.containerCaricamentoAvatar.setVisibility(View.GONE);
                    binding.cardCaricaAvatar.setEnabled(true);
                    AppSnackbar.show(binding.getRoot(), "Errore upload: " + errore);
                }
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
