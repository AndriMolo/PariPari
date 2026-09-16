package com.example.paripariapp.ui.view;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.paripariapp.R;
import com.example.paripariapp.databinding.BottomSheetIconaGruppoBinding;
import com.example.paripariapp.ui.viewmodel.SpeseViewModel;
import com.example.paripariapp.util.AppSnackbar;
import com.example.paripariapp.util.AvatarVisualUtil;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

/**
 * BottomSheet per la personalizzazione dell'icona della Scheda Spese a costo 0 storage.
 * Consente di abbinare un'emoji a un colore di sfondo armonioso.
 */
public class BottomSheetIconaGruppo extends BottomSheetDialogFragment {

    private static final String ARG_SCHEDA_ID = "scheda_id";
    private String schedaId;
    private BottomSheetIconaGruppoBinding binding;
    private SpeseViewModel viewModel;

    private String selectedColor = AvatarVisualUtil.COLOR_PALETTE[5]; // Default Blue
    private String selectedEmoji = "🏖️";

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

        setupColorPalette();
        setupEmojiCategories();
        caricaEmojiCategoria(AvatarVisualUtil.EMOJI_VIAGGI);
        setupListeners();
        aggiornaAnteprima();
    }

    private void setupColorPalette() {
        if (binding == null || getContext() == null) return;
        binding.containerColoriScheda.removeAllViews();
        int size = (int) (38 * getResources().getDisplayMetrics().density);
        int strokeW = (int) (3 * getResources().getDisplayMetrics().density);

        for (String hex : AvatarVisualUtil.COLOR_PALETTE) {
            MaterialCardView card = new MaterialCardView(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(10, 4, 10, 8);
            card.setLayoutParams(lp);
            card.setRadius(size / 2f);
            card.setCardBackgroundColor(Color.parseColor(hex));
            card.setClickable(true);
            card.setFocusable(true);
            card.setTag(hex);

            if (hex.equalsIgnoreCase(selectedColor)) {
                card.setStrokeWidth(strokeW);
                card.setStrokeColor(Color.WHITE);
            } else {
                card.setStrokeWidth(0);
            }

            card.setOnClickListener(v -> selezionaColore(hex));
            binding.containerColoriScheda.addView(card);
        }
    }

    private void selezionaColore(String hex) {
        selectedColor = hex;
        if (binding == null || getContext() == null) return;
        int strokeW = (int) (3 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < binding.containerColoriScheda.getChildCount(); i++) {
            View child = binding.containerColoriScheda.getChildAt(i);
            if (child instanceof MaterialCardView) {
                MaterialCardView card = (MaterialCardView) child;
                if (hex.equalsIgnoreCase((String) card.getTag())) {
                    card.setStrokeWidth(strokeW);
                    card.setStrokeColor(Color.WHITE);
                } else {
                    card.setStrokeWidth(0);
                }
            }
        }
        aggiornaAnteprima();
    }

    private void setupEmojiCategories() {
        binding.chipSchedaViaggi.setOnClickListener(v -> caricaEmojiCategoria(AvatarVisualUtil.EMOJI_VIAGGI));
        binding.chipSchedaCibo.setOnClickListener(v -> caricaEmojiCategoria(AvatarVisualUtil.EMOJI_CIBO));
        binding.chipSchedaCasa.setOnClickListener(v -> caricaEmojiCategoria(AvatarVisualUtil.EMOJI_CASA));
        binding.chipSchedaSvago.setOnClickListener(v -> caricaEmojiCategoria(AvatarVisualUtil.EMOJI_SVAGO));
        binding.chipSchedaPersone.setOnClickListener(v -> caricaEmojiCategoria(AvatarVisualUtil.EMOJI_PERSONE));
    }

    private void caricaEmojiCategoria(List<String> emojis) {
        if (binding == null) return;
        binding.containerEmojiScheda.removeAllViews();

        for (String emoji : emojis) {
            TextView tv = new TextView(requireContext());
            tv.setText(emoji);
            tv.setTextSize(26);
            tv.setPadding(18, 12, 18, 12);
            tv.setBackgroundResource(R.drawable.bg_badge_valuta);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(8, 4, 8, 4);
            tv.setLayoutParams(lp);

            tv.setOnClickListener(v -> {
                selectedEmoji = emoji;
                aggiornaAnteprima();
            });

            binding.containerEmojiScheda.addView(tv);
        }
    }

    private void setupListeners() {
        binding.btnSalvaIconaScheda.setOnClickListener(v -> {
            if (schedaId != null) {
                String iconData = "emoji:" + selectedEmoji + ":" + selectedColor;
                viewModel.getRepository().aggiornaIconaScheda(schedaId, iconData);
                AppSnackbar.show(requireActivity().findViewById(android.R.id.content), "Icona gruppo aggiornata");
            }
            dismiss();
        });

        binding.btnRipristinaIcona.setOnClickListener(v -> {
            if (schedaId != null) {
                viewModel.getRepository().aggiornaIconaScheda(schedaId, null);
                AppSnackbar.show(requireActivity().findViewById(android.R.id.content), "Icona ripristinata");
            }
            dismiss();
        });
    }

    private void aggiornaAnteprima() {
        if (binding == null) return;
        int parsedColor = AvatarVisualUtil.parseColorSafe(selectedColor, Color.parseColor("#1976D2"));
        binding.cardAnteprimaIcona.setCardBackgroundColor(parsedColor);
        binding.tvAnteprimaEmojiGruppo.setText(selectedEmoji);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
