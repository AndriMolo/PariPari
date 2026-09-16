package com.example.paripariapp.ui.view;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.paripariapp.R;
import com.example.paripariapp.databinding.BottomSheetNuovaSchedaBinding;
import com.example.paripariapp.ui.viewmodel.SpeseViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * BottomSheet per la creazione rapida ed essenziale di una Scheda Spese.
 * Include automaticamente il nome utente con "(io)" come primo partecipante e permette di aggiungere
 * gli amici al volo tramite Chip rimovibili.
 */
public class NuovaSchedaBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetNuovaSchedaBinding binding;
    private SpeseViewModel viewModel;
    private String selectedCurrencyCode;
    private String selectedEmoji = "🏖️";
    private String selectedColor = com.example.paripariapp.util.AvatarVisualUtil.COLOR_PALETTE[5];

    public static NuovaSchedaBottomSheet newInstance() {
        return new NuovaSchedaBottomSheet();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetNuovaSchedaBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(SpeseViewModel.class);
        selectedCurrencyCode = viewModel.getDefaultCurrency();

        setupEmojiSelector();
        aggiornaValutaUI();
        setupInitialChip();
        setupListeners();

        com.example.paripariapp.util.KeyboardUtil.showKeyboard(binding.etNomeScheda);
    }

    private void setupEmojiSelector() {
        if (binding == null) return;
        aggiornaIconaAnteprima();

        String[] quickEmojis = new String[] { "🏖️", "🍕", "✈️", "🏠", "⚽", "🍻", "🎉", "🛒", "🚗", "☕", "⛷️", "🍿", "🏕️", "🍔", "🎮" };
        binding.containerEmojiNuovaScheda.removeAllViews();

        for (int i = 0; i < quickEmojis.length; i++) {
            final String emoji = quickEmojis[i];
            final String color = com.example.paripariapp.util.AvatarVisualUtil.COLOR_PALETTE[i % com.example.paripariapp.util.AvatarVisualUtil.COLOR_PALETTE.length];

            TextView tv = new TextView(requireContext());
            tv.setText(emoji);
            tv.setTextSize(20);
            tv.setPadding(14, 10, 14, 10);
            tv.setBackgroundResource(R.drawable.bg_badge_valuta);

            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(6, 2, 6, 2);
            tv.setLayoutParams(lp);

            tv.setOnClickListener(v -> {
                selectedEmoji = emoji;
                selectedColor = color;
                aggiornaIconaAnteprima();
                com.example.paripariapp.util.HapticUtil.tick(tv);
            });

            binding.containerEmojiNuovaScheda.addView(tv);
        }

        binding.cardIconaNuovaScheda.setOnClickListener(v -> {
            int nextIdx = (java.util.Arrays.asList(com.example.paripariapp.util.AvatarVisualUtil.COLOR_PALETTE).indexOf(selectedColor) + 1)
                    % com.example.paripariapp.util.AvatarVisualUtil.COLOR_PALETTE.length;
            selectedColor = com.example.paripariapp.util.AvatarVisualUtil.COLOR_PALETTE[nextIdx];
            aggiornaIconaAnteprima();
            com.example.paripariapp.util.HapticUtil.tick(binding.cardIconaNuovaScheda);
        });
    }

    private void aggiornaIconaAnteprima() {
        if (binding == null) return;
        int colorInt = com.example.paripariapp.util.AvatarVisualUtil.parseColorSafe(selectedColor, android.graphics.Color.parseColor("#1976D2"));
        binding.cardIconaNuovaScheda.setCardBackgroundColor(colorInt);
        binding.tvEmojiNuovaScheda.setText(selectedEmoji);
    }

    private String getNomeCreatoreFormat() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String nome = null;
        if (user != null && user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
            nome = user.getDisplayName().trim();
        }
        if (TextUtils.isEmpty(nome)) {
            nome = getString(R.string.default_nome_utente);
        }
        String pulito = com.example.paripariapp.data.model.Partecipante.pulisciNome(nome);
        return getString(R.string.formato_nome_con_io, pulito, getString(R.string.etichetta_io));
    }

    /**
     * Inserisce il chip predefinito "Nome Utente (io)". Non modificabile alla creazione del gruppo.
     */
    private void setupInitialChip() {
        Chip chipIo = new Chip(requireContext());
        chipIo.setText(getNomeCreatoreFormat());
        chipIo.setCheckable(false);
        chipIo.setClickable(false);
        chipIo.setCloseIconVisible(false);

        binding.chipGroupPartecipanti.addView(chipIo);
    }

    private void aggiornaValutaUI() {
        if (binding == null || selectedCurrencyCode == null) return;
        String flag = com.example.paripariapp.data.repository.UserPreferencesRepository.getCurrencyFlag(selectedCurrencyCode);
        String displayItem = com.example.paripariapp.data.repository.UserPreferencesRepository.getDisplayItemForCode(selectedCurrencyCode);
        binding.etValutaScheda.setText(getString(R.string.format_categoria_con_emoji, flag, displayItem));
    }

    private void mostraSelettoreValuta() {
        String currentDisplay = com.example.paripariapp.data.repository.UserPreferencesRepository.getDisplayItemForCode(selectedCurrencyCode);
        SelettoreValutaBottomSheet sheet = SelettoreValutaBottomSheet.newInstance(currentDisplay);
        sheet.setOnCurrencySelectedListener(currencyFull -> {
            selectedCurrencyCode = com.example.paripariapp.data.repository.UserPreferencesRepository.extractCurrencyCode(currencyFull);
            aggiornaValutaUI();
        });
        sheet.show(getChildFragmentManager(), "selettore_valuta_nuova_scheda");
    }

    private void setupListeners() {
        View.OnClickListener listenerValuta = v -> mostraSelettoreValuta();
        binding.etValutaScheda.setOnClickListener(listenerValuta);
        binding.tilValutaScheda.setOnClickListener(listenerValuta);

        // Tasto "+" nell'input del nuovo partecipante
        binding.tilNuovoPartecipante.setEndIconOnClickListener(v -> aggiungiAmicoDaInput());

        // Tasto Invio/Fine sulla tastiera mentre si digita il nome dell'amico
        binding.etNuovoPartecipante.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                aggiungiAmicoDaInput();
                return true;
            }
            return false;
        });

        // Pulsante "Crea Scheda"
        binding.btnCreaScheda.setOnClickListener(v -> creaScheda());
    }

    private void aggiungiAmicoDaInput() {
        if (binding.etNuovoPartecipante.getText() == null) return;
        String nome = binding.etNuovoPartecipante.getText().toString().trim();
        if (TextUtils.isEmpty(nome)) return;

        // Evita duplicati visivi
        int count = binding.chipGroupPartecipanti.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = binding.chipGroupPartecipanti.getChildAt(i);
            if (child instanceof Chip) {
                Chip existing = (Chip) child;
                if (nome.equalsIgnoreCase(existing.getText().toString())) {
                    binding.etNuovoPartecipante.setText("");
                    return;
                }
            }
        }

        // Crea chip rimovibile per l'amico
        Chip chipAmico = new Chip(requireContext());
        chipAmico.setText(nome);
        chipAmico.setCloseIconVisible(true);
        chipAmico.setOnCloseIconClickListener(v -> binding.chipGroupPartecipanti.removeView(chipAmico));

        binding.chipGroupPartecipanti.addView(chipAmico);
        com.example.paripariapp.util.HapticUtil.tick(binding.chipGroupPartecipanti);
    }

    private void creaScheda() {
        binding.tilNomeScheda.setError(null);

        String nomeScheda = binding.etNomeScheda.getText() != null
                ? binding.etNomeScheda.getText().toString().trim()
                : "";

        if (TextUtils.isEmpty(nomeScheda)) {
            binding.tilNomeScheda.setError(getString(R.string.error_nome_scheda_obbligatorio));
            return;
        }

        // Se l'utente ha digitato un nome nel campo partecipante senza premere '+' prima di creare, lo aggiungiamo
        if (binding.etNuovoPartecipante.getText() != null) {
            String pendingName = binding.etNuovoPartecipante.getText().toString().trim();
            if (!TextUtils.isEmpty(pendingName)) {
                aggiungiAmicoDaInput();
            }
        }

        // Raccoglie tutti i partecipanti inseriti nei chip
        List<String> nomiPartecipanti = new ArrayList<>();
        int count = binding.chipGroupPartecipanti.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = binding.chipGroupPartecipanti.getChildAt(i);
            if (child instanceof Chip) {
                String nome = ((Chip) child).getText().toString();
                String nomePulito = com.example.paripariapp.data.model.Partecipante.pulisciNome(nome);
                if (!TextUtils.isEmpty(nomePulito)) {
                    nomiPartecipanti.add(nomePulito);
                }
            }
        }

        String valutaFinale = selectedCurrencyCode != null ? selectedCurrencyCode : viewModel.getDefaultCurrency();
        com.example.paripariapp.util.HapticUtil.confirm(binding.btnCreaScheda);
        String iconaUrl = "emoji:" + selectedEmoji + ":" + selectedColor;
        viewModel.creaScheda(nomeScheda, valutaFinale, iconaUrl, nomiPartecipanti);
        dismiss();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Prevenzione memory leak
    }
}
