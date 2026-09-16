package com.example.paripariapp.ui.view;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.paripariapp.R;
import com.example.paripariapp.data.repository.UserPreferencesRepository;
import com.example.paripariapp.databinding.DialogModificaPaymentHandleBinding;
import com.example.paripariapp.databinding.FragmentAccountBinding;
import com.example.paripariapp.ui.viewmodel.AccountViewModel;
import com.example.paripariapp.util.AppSnackbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Fragment principale della schermata "Account".
 * Coordina lo stato di autenticazione delegando la vista a child fragments specializzati
 * ({@link AccountGuestFragment} per gli ospiti e {@link AccountProfileFragment} per gli utenti registrati),
 * e gestisce le impostazioni condivise dell'app (valuta, lingua, tema, metodi di pagamento).
 */
public class AccountFragment extends Fragment {

    private FragmentAccountBinding binding;
    private AccountViewModel viewModel;

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

        viewModel = new ViewModelProvider(requireActivity()).get(AccountViewModel.class);

        setupChildFragmentObserver();
        setupPreferenceObservers();
        setupCurrencySelector();
        setupLanguageSelector();
        setupThemeSelector();
        setupPaymentMethodsSelectors();
    }

    /**
     * Alterna dinamicamente tra AccountGuestFragment e AccountProfileFragment
     * in base allo stato di sessione dell'utente.
     */
    private void setupChildFragmentObserver() {
        viewModel.getIsGuestMode().observe(getViewLifecycleOwner(), isGuest -> {
            if (binding == null) return;
            boolean isGuestMode = Boolean.TRUE.equals(isGuest);

            Fragment currentChild = getChildFragmentManager().findFragmentById(R.id.account_status_fragment_container);
            if (isGuestMode) {
                if (!(currentChild instanceof AccountGuestFragment)) {
                    getChildFragmentManager().beginTransaction()
                            .replace(R.id.account_status_fragment_container, new AccountGuestFragment())
                            .commit();
                }
            } else {
                if (!(currentChild instanceof AccountProfileFragment)) {
                    getChildFragmentManager().beginTransaction()
                            .replace(R.id.account_status_fragment_container, new AccountProfileFragment())
                            .commit();
                }
            }
        });
    }

    private void setupPreferenceObservers() {
        // Valuta predefinita
        viewModel.getDefaultCurrencyLive().observe(getViewLifecycleOwner(), currencyCode -> {
            if (binding != null && currencyCode != null) {
                binding.tvValutaPredefinitaValore.setText(currencyCode);
            }
        });

        // Lingua dell'applicazione
        viewModel.getAppLanguageLive().observe(getViewLifecycleOwner(), langCode -> {
            if (binding != null && langCode != null) {
                String display = getDisplayLanguageForCode(langCode);
                binding.tvLinguaAppValore.setText(display);
            }
        });

        // Tema dell'applicazione
        viewModel.getAppThemeLive().observe(getViewLifecycleOwner(), themeCode -> {
            if (binding != null && themeCode != null) {
                String display = getDisplayThemeForCode(themeCode);
                binding.tvTemaAppValore.setText(display);
            }
        });

        // Tag di pagamento PayPal
        viewModel.getPaypalHandleLive().observe(getViewLifecycleOwner(), handle -> {
            if (binding == null) return;
            if (handle != null && !handle.trim().isEmpty()) {
                binding.tvPaypalValore.setText(getString(R.string.handle_format, handle.trim()));
            } else {
                binding.tvPaypalValore.setText(R.string.badge_non_configurato);
            }
        });

        // Tag di pagamento Revolut
        viewModel.getRevolutHandleLive().observe(getViewLifecycleOwner(), handle -> {
            if (binding == null) return;
            if (handle != null && !handle.trim().isEmpty()) {
                binding.tvRevolutValore.setText(getString(R.string.handle_format, handle.trim()));
            } else {
                binding.tvRevolutValore.setText(R.string.badge_non_configurato);
            }
        });
    }

    private void setupCurrencySelector() {
        binding.rowValutaPredefinita.setOnClickListener(v -> showCurrencySelectionDialog());
    }

    private void showCurrencySelectionDialog() {
        String currentCode = viewModel.getDefaultCurrency();
        String currentDisplay = UserPreferencesRepository.getDisplayItemForCode(currentCode);
        SelettoreValutaBottomSheet sheet = SelettoreValutaBottomSheet.newInstance(currentDisplay);
        sheet.setOnCurrencySelectedListener(currencyFull -> {
            String code = UserPreferencesRepository.extractCurrencyCode(currencyFull);
            viewModel.setDefaultCurrency(code);
        });
        sheet.show(getParentFragmentManager(), "selettore_valuta_account");
    }

    private void setupLanguageSelector() {
        binding.rowLinguaApp.setOnClickListener(v -> showLanguageSelectionDialog());
    }

    private void showLanguageSelectionDialog() {
        String[] languageCodes = new String[]{
                UserPreferencesRepository.LANGUAGE_SYSTEM,
                "it",
                "en",
                "es",
                "fr",
                "de"
        };
        String[] items = new String[]{
                getString(R.string.lingua_sistema),
                "Italiano",
                "English",
                "Español",
                "Français",
                "Deutsch"
        };
        String currentCode = viewModel.getAppLanguage();
        int selectedIndex = 0;
        for (int i = 0; i < languageCodes.length; i++) {
            if (languageCodes[i].equalsIgnoreCase(currentCode)) {
                selectedIndex = i;
                break;
            }
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.label_lingua_app)
                .setSingleChoiceItems(items, selectedIndex, (dialog, which) -> {
                    String code = languageCodes[which];
                    if (!code.equalsIgnoreCase(currentCode)) {
                        viewModel.setAppLanguage(code);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.btn_annulla, null)
                .show();
    }

    private void setupThemeSelector() {
        binding.rowTemaApp.setOnClickListener(v -> showThemeSelectionDialog());
    }

    private void showThemeSelectionDialog() {
        String[] items = new String[]{
                getString(R.string.tema_sistema),
                getString(R.string.tema_chiaro),
                getString(R.string.tema_scuro)
        };
        String currentCode = viewModel.getAppTheme();
        int selectedIndex = 0;
        if (UserPreferencesRepository.THEME_LIGHT.equalsIgnoreCase(currentCode)) {
            selectedIndex = 1;
        } else if (UserPreferencesRepository.THEME_DARK.equalsIgnoreCase(currentCode)) {
            selectedIndex = 2;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.label_tema_app)
                .setSingleChoiceItems(items, selectedIndex, (dialog, which) -> {
                    String code = UserPreferencesRepository.THEME_SYSTEM;
                    if (which == 1) {
                        code = UserPreferencesRepository.THEME_LIGHT;
                    } else if (which == 2) {
                        code = UserPreferencesRepository.THEME_DARK;
                    }
                    if (!code.equalsIgnoreCase(currentCode)) {
                        viewModel.setAppTheme(code);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.btn_annulla, null)
                .show();
    }

    private String getDisplayThemeForCode(String code) {
        if (UserPreferencesRepository.THEME_LIGHT.equalsIgnoreCase(code)) {
            return getString(R.string.tema_chiaro);
        } else if (UserPreferencesRepository.THEME_DARK.equalsIgnoreCase(code)) {
            return getString(R.string.tema_scuro);
        } else {
            return getString(R.string.tema_sistema);
        }
    }

    private String getDisplayLanguageForCode(String code) {
        if (code == null || UserPreferencesRepository.LANGUAGE_SYSTEM.equalsIgnoreCase(code)) {
            return getString(R.string.lingua_sistema);
        }
        return UserPreferencesRepository.getDisplayLanguageForCode(requireContext(), code);
    }

    private void setupPaymentMethodsSelectors() {
        binding.rowPaypal.setOnClickListener(v -> mostraDialogModificaPaymentHandle(true));
        binding.rowRevolut.setOnClickListener(v -> mostraDialogModificaPaymentHandle(false));
    }

    private void mostraDialogModificaPaymentHandle(boolean isPaypal) {
        String currentHandle = isPaypal ? viewModel.getPaypalHandle() : viewModel.getRevolutHandle();
        DialogModificaPaymentHandleBinding dialogBinding = DialogModificaPaymentHandleBinding.inflate(getLayoutInflater());

        dialogBinding.tilHandle.setHint(getString(isPaypal ? R.string.hint_paypal_tag : R.string.hint_revolut_tag));
        dialogBinding.tilHandle.setPrefixText(isPaypal ? "paypal.me/" : "revolut.me/");
        dialogBinding.tilHandle.setHelperText(getString(isPaypal ? R.string.helper_paypal : R.string.helper_revolut));

        if (!TextUtils.isEmpty(currentHandle)) {
            dialogBinding.etHandle.setText(currentHandle);
            dialogBinding.etHandle.setSelection(currentHandle.length());
        }



        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogBinding.getRoot())
                .setPositiveButton(R.string.btn_salva, (d, which) -> {
                    String raw = dialogBinding.etHandle.getText() != null ? dialogBinding.etHandle.getText().toString().trim() : "";
                    if (isPaypal) {
                        viewModel.setPaypalHandle(raw);
                    } else {
                        viewModel.setRevolutHandle(raw);
                    }
                })
                .setNeutralButton(R.string.btn_elimina, (d, which) -> {
                    if (isPaypal) {
                        viewModel.setPaypalHandle("");
                    } else {
                        viewModel.setRevolutHandle("");
                    }
                })
                .setNegativeButton(R.string.btn_annulla, null)
                .create();

        dialog.setOnShowListener(d -> {
            android.widget.Button posBtn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            if (posBtn != null) {
                posBtn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_check, 0, 0, 0);
                posBtn.setCompoundDrawablePadding((int) (6 * getResources().getDisplayMetrics().density));
            }
        });

        dialog.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
