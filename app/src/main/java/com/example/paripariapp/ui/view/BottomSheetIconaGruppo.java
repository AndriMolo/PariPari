package com.example.paripariapp.ui.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.paripariapp.R;
import com.example.paripariapp.data.repository.PariPariRepository;
import com.example.paripariapp.databinding.BottomSheetIconaGruppoBinding;
import com.example.paripariapp.ui.viewmodel.SpeseViewModel;
import com.example.paripariapp.util.AppSnackbar;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class BottomSheetIconaGruppo extends BottomSheetDialogFragment {

    private static final String ARG_SCHEDA_ID = "scheda_id";
    private String schedaId;
    private BottomSheetIconaGruppoBinding binding;
    private SpeseViewModel viewModel;

    private static final String[] EMOJI_LIST = new String[] {
            "🏕️", "🍕", "✈️", "⛷️", "🏖️", "🏠", "⚽", "🍺", "🎉", "🛒", "🍔", "🚗", "☕", "🍿", "🎁"
    };

    private final ActivityResultLauncher<PickVisualMediaRequest> pickMediaLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    caricaFotoDaUri(uri);
                }
            });

    public static BottomSheetIconaGruppo newInstance(String schedaId) {
        BottomSheetIconaGruppo fragment = new BottomSheetIconaGruppo();
        Bundle args = new Bundle();
        args.putString(ARG_SCHEDA_ID, schedaId);
        fragment.setArguments(args);
        return fragment;
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
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetIconaGruppoBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(SpeseViewModel.class);

        setupEmojiGrid();
        setupListeners();
    }

    private void setupEmojiGrid() {
        if (binding == null) return;
        binding.containerEmojiGrid.removeAllViews();

        for (String emoji : EMOJI_LIST) {
            TextView tv = new TextView(requireContext());
            tv.setText(emoji);
            tv.setTextSize(28);
            tv.setPadding(20, 16, 20, 16);
            tv.setBackgroundResource(R.drawable.bg_badge_valuta);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(10, 0, 10, 0);
            tv.setLayoutParams(params);

            tv.setOnClickListener(v -> {
                viewModel.getRepository().aggiornaIconaScheda(schedaId, "emoji:" + emoji);
                dismiss();
            });

            binding.containerEmojiGrid.addView(tv);
        }
    }

    private void setupListeners() {
        binding.cardCaricaFoto.setOnClickListener(v -> {
            pickMediaLauncher.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });

        binding.btnRipristinaIcona.setOnClickListener(v -> {
            viewModel.getRepository().aggiornaIconaScheda(schedaId, null);
            dismiss();
        });
    }

    private void caricaFotoDaUri(android.net.Uri uri) {
        if (binding == null) return;
        binding.containerCaricamentoFoto.setVisibility(View.VISIBLE);
        binding.cardCaricaFoto.setEnabled(false);

        viewModel.getRepository().uploadIconaScheda(requireContext().getApplicationContext(), uri, schedaId, new PariPariRepository.OnUploadCallback() {
            @Override
            public void onSuccess(String url) {
                if (isAdded() && binding != null) {
                    binding.containerCaricamentoFoto.setVisibility(View.GONE);
                    dismiss();
                }
            }

            @Override
            public void onError(String errore) {
                if (isAdded() && binding != null) {
                    binding.containerCaricamentoFoto.setVisibility(View.GONE);
                    binding.cardCaricaFoto.setEnabled(true);
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
