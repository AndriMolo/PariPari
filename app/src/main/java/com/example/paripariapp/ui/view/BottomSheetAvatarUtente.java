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
import com.example.paripariapp.databinding.BottomSheetAvatarUtenteBinding;
import com.example.paripariapp.ui.viewmodel.AccountViewModel;
import com.example.paripariapp.util.AppSnackbar;
import com.example.paripariapp.util.AvatarVisualUtil;
import com.example.paripariapp.util.ImageLoaderUtil;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;
import java.util.Locale;

/**
 * BottomSheet per la personalizzazione istantanea dell'Avatar a costo storage 0 e banda 0.
 * Permette di selezionare:
 * 1) Emoji con colore di sfondo
 * 2) Iniziale stilizzata con colore di sfondo
 * 3) Foto profilo Google (gratuita e senza upload se l'account è collegato)
 * 4) Ripristino dell'avatar predefinito
 */
public class BottomSheetAvatarUtente extends BottomSheetDialogFragment {

    private BottomSheetAvatarUtenteBinding binding;
    private AccountViewModel viewModel;

    private enum Mode { EMOJI, INIZIALE, GOOGLE }
    private Mode currentMode = Mode.EMOJI;

    private String selectedColor = AvatarVisualUtil.COLOR_PALETTE[4]; // Default Indigo
    private String selectedEmoji = "😎";
    private String userName = "Io";
    private String googlePhotoUrl = null;

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
        viewModel = new ViewModelProvider(requireActivity()).get(AccountViewModel.class);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.isAnonymous()) {
            AppSnackbar.show(requireActivity().findViewById(android.R.id.content), "Registrati o accedi per personalizzare il tuo avatar");
            dismiss();
            return;
        }

        if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
            userName = user.getDisplayName().trim();
        }

        // Rileva foto profilo Google (ospitata gratuitamente su server Google)
        if (user.getPhotoUrl() != null && (user.getPhotoUrl().toString().startsWith("http://") || user.getPhotoUrl().toString().startsWith("https://"))) {
            googlePhotoUrl = user.getPhotoUrl().toString();
        }
        for (com.google.firebase.auth.UserInfo info : user.getProviderData()) {
            if ("google.com".equalsIgnoreCase(info.getProviderId()) && info.getPhotoUrl() != null) {
                googlePhotoUrl = info.getPhotoUrl().toString();
                break;
            }
        }

        if (googlePhotoUrl != null) {
            binding.chipTipoGoogle.setVisibility(View.VISIBLE);
        }

        String currentAvatar = viewModel.getCustomAvatar();
        if (currentAvatar == null && user.getPhotoUrl() != null) {
            currentAvatar = user.getPhotoUrl().toString();
        }
        if (currentAvatar != null) {
            if (currentAvatar.startsWith("emoji:")) {
                currentMode = Mode.EMOJI;
                String[] parts = currentAvatar.split(":");
                if (parts.length >= 2) selectedEmoji = parts[1];
                if (parts.length >= 3) selectedColor = parts[2];
                binding.chipTipoEmoji.setChecked(true);
            } else if (currentAvatar.startsWith("initial:")) {
                currentMode = Mode.INIZIALE;
                String[] parts = currentAvatar.split(":");
                if (parts.length >= 3) selectedColor = parts[2];
                binding.chipTipoIniziale.setChecked(true);
                binding.layoutSezioneEmoji.setVisibility(View.GONE);
            } else if (currentAvatar.startsWith("http")) {
                currentMode = Mode.GOOGLE;
                binding.chipTipoGoogle.setChecked(true);
                binding.layoutSezioneEmoji.setVisibility(View.GONE);
                binding.tvLabelColore.setVisibility(View.GONE);
                binding.scrollColori.setVisibility(View.GONE);
            }
        }

        setupColorPalette();
        setupEmojiCategories();
        caricaEmojiCategoria(AvatarVisualUtil.EMOJI_PERSONE);
        setupListeners();
        aggiornaAnteprima();
    }

    private void setupColorPalette() {
        if (binding == null || getContext() == null) return;
        binding.containerColoriPalette.removeAllViews();
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
            binding.containerColoriPalette.addView(card);
        }
    }

    private void selezionaColore(String hex) {
        selectedColor = hex;
        if (binding == null || getContext() == null) return;
        int strokeW = (int) (3 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < binding.containerColoriPalette.getChildCount(); i++) {
            View child = binding.containerColoriPalette.getChildAt(i);
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
        binding.chipCatPersone.setOnClickListener(v -> caricaEmojiCategoria(AvatarVisualUtil.EMOJI_PERSONE));
        binding.chipCatCibo.setOnClickListener(v -> caricaEmojiCategoria(AvatarVisualUtil.EMOJI_CIBO));
        binding.chipCatViaggi.setOnClickListener(v -> caricaEmojiCategoria(AvatarVisualUtil.EMOJI_VIAGGI));
        binding.chipCatSvago.setOnClickListener(v -> caricaEmojiCategoria(AvatarVisualUtil.EMOJI_SVAGO));
        binding.chipCatCasa.setOnClickListener(v -> caricaEmojiCategoria(AvatarVisualUtil.EMOJI_CASA));
    }

    private void caricaEmojiCategoria(List<String> emojis) {
        if (binding == null) return;
        binding.containerEmojiOpzioni.removeAllViews();

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

            binding.containerEmojiOpzioni.addView(tv);
        }
    }

    private void setupListeners() {
        binding.chipTipoEmoji.setOnClickListener(v -> {
            currentMode = Mode.EMOJI;
            binding.layoutSezioneEmoji.setVisibility(View.VISIBLE);
            binding.tvLabelColore.setVisibility(View.VISIBLE);
            binding.scrollColori.setVisibility(View.VISIBLE);
            aggiornaAnteprima();
        });

        binding.chipTipoIniziale.setOnClickListener(v -> {
            currentMode = Mode.INIZIALE;
            binding.layoutSezioneEmoji.setVisibility(View.GONE);
            binding.tvLabelColore.setVisibility(View.VISIBLE);
            binding.scrollColori.setVisibility(View.VISIBLE);
            aggiornaAnteprima();
        });

        binding.chipTipoGoogle.setOnClickListener(v -> {
            currentMode = Mode.GOOGLE;
            binding.layoutSezioneEmoji.setVisibility(View.GONE);
            binding.tvLabelColore.setVisibility(View.GONE);
            binding.scrollColori.setVisibility(View.GONE);
            aggiornaAnteprima();
        });

        binding.btnSalvaAvatar.setOnClickListener(v -> salvaAvatar());

        binding.btnRipristinaAvatar.setOnClickListener(v -> {
            String defaultAvatar = (googlePhotoUrl != null && !googlePhotoUrl.trim().isEmpty()) ? googlePhotoUrl : null;
            viewModel.aggiornaAvatarUtente(defaultAvatar);
            viewModel.setSuccessMessage(googlePhotoUrl != null
                    ? getString(R.string.msg_avatar_ripristinato_google)
                    : getString(R.string.msg_avatar_ripristinato));
            dismiss();
        });
    }

    private void aggiornaAnteprima() {
        if (binding == null) return;

        if (currentMode == Mode.GOOGLE && googlePhotoUrl != null) {
            binding.tvAnteprimaEmoji.setVisibility(View.GONE);
            binding.ivAnteprimaFoto.setVisibility(View.VISIBLE);
            binding.cardAnteprimaAvatar.setCardBackgroundColor(Color.TRANSPARENT);
            ImageLoaderUtil.caricaImmagine(googlePhotoUrl, binding.ivAnteprimaFoto, R.drawable.ic_account);
            binding.tvAnteprimaEtichetta.setText("Foto da account Google (0 byte)");
            return;
        }

        binding.ivAnteprimaFoto.setVisibility(View.GONE);
        binding.tvAnteprimaEmoji.setVisibility(View.VISIBLE);
        int parsedColor = AvatarVisualUtil.parseColorSafe(selectedColor, Color.parseColor("#3F51B5"));
        binding.cardAnteprimaAvatar.setCardBackgroundColor(parsedColor);

        if (currentMode == Mode.EMOJI) {
            binding.tvAnteprimaEmoji.setText(selectedEmoji);
            binding.tvAnteprimaEmoji.setTextColor(Color.WHITE);
            binding.tvAnteprimaEmoji.setTextSize(36);
            binding.tvAnteprimaEtichetta.setText("Emoji con sfondo colorato (0 byte)");
        } else {
            String initial = !userName.isEmpty() ? String.valueOf(userName.charAt(0)).toUpperCase(Locale.getDefault()) : "A";
            binding.tvAnteprimaEmoji.setText(initial);
            binding.tvAnteprimaEmoji.setTextColor(Color.WHITE);
            binding.tvAnteprimaEmoji.setTextSize(32);
            binding.tvAnteprimaEtichetta.setText("Iniziale con sfondo colorato (0 byte)");
        }
    }

    private void salvaAvatar() {
        String avatarData;
        if (currentMode == Mode.GOOGLE && googlePhotoUrl != null) {
            avatarData = googlePhotoUrl;
        } else if (currentMode == Mode.EMOJI) {
            avatarData = "emoji:" + selectedEmoji + ":" + selectedColor;
        } else {
            String initial = !userName.isEmpty() ? String.valueOf(userName.charAt(0)).toUpperCase(Locale.getDefault()) : "A";
            avatarData = "initial:" + initial + ":" + selectedColor;
        }

        viewModel.aggiornaAvatarUtente(avatarData);
        viewModel.setSuccessMessage(getString(R.string.msg_avatar_aggiornato));
        dismiss();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
