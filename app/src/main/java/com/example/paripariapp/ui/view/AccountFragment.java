package com.example.paripariapp.ui.view;

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
import com.example.paripariapp.databinding.FragmentAccountBinding;
import com.example.paripariapp.ui.viewmodel.AccountViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

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
                String display = UserPreferencesRepository.getDisplayLanguageForCode(langCode);
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
                binding.tvPaypalValore.setText("@" + handle.trim());
            } else {
                binding.tvPaypalValore.setText(R.string.badge_non_configurato);
            }
        });

        // Tag di pagamento Revolut
        viewModel.getRevolutHandleLive().observe(getViewLifecycleOwner(), handle -> {
            if (binding == null) return;
            if (handle != null && !handle.trim().isEmpty()) {
                binding.tvRevolutValore.setText("@" + handle.trim());
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
            Snackbar.make(binding.getRoot(), getString(R.string.msg_valuta_aggiornata, code), Snackbar.LENGTH_SHORT).show();
        });
        sheet.show(getParentFragmentManager(), "selettore_valuta_account");
    }

    private void setupLanguageSelector() {
        binding.rowLinguaApp.setOnClickListener(v -> showLanguageSelectionDialog());
    }

    private void showLanguageSelectionDialog() {
        String currentCode = viewModel.getAppLanguage();
        int selectedIndex = UserPreferencesRepository.getIndexOfLanguageCode(currentCode);
        String[] items = UserPreferencesRepository.SUPPORTED_LANGUAGES.toArray(new String[0]);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.label_lingua_app)
                .setSingleChoiceItems(items, selectedIndex, (dialog, which) -> {
                    String selected = items[which];
                    String code = UserPreferencesRepository.extractLanguageCode(selected);
                    viewModel.setAppLanguage(code);
                    String display = UserPreferencesRepository.getDisplayLanguageForCode(code);
                    Snackbar.make(binding.getRoot(), getString(R.string.msg_lingua_aggiornata, display), Snackbar.LENGTH_SHORT).show();
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
                    viewModel.setAppTheme(code);
                    Snackbar.make(binding.getRoot(), R.string.msg_tema_aggiornato, Snackbar.LENGTH_SHORT).show();
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

    private void setupPaymentMethodsSelectors() {
        binding.rowPaypal.setOnClickListener(v -> mostraDialogModificaPaypal());
        binding.rowRevolut.setOnClickListener(v -> mostraDialogModificaRevolut());
    }

    private void mostraDialogModificaPaypal() {
        String currentHandle = viewModel.getPaypalHandle();

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_modifica_payment_handle, null);
        TextInputLayout til = dialogView.findViewById(R.id.til_handle);
        TextInputEditText et = dialogView.findViewById(R.id.et_handle);

        til.setHint(getString(R.string.hint_paypal_tag));
        til.setPrefixText("paypal.me/");
        til.setHelperText(getString(R.string.helper_paypal));

        if (!TextUtils.isEmpty(currentHandle)) {
            et.setText(currentHandle);
            et.setSelection(currentHandle.length());
        }

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setPositiveButton(R.string.btn_salva, (d, which) -> {
                    String raw = et.getText() != null ? et.getText().toString().trim() : "";
                    viewModel.setPaypalHandle(raw);
                    Snackbar.make(binding.getRoot(), R.string.msg_paypal_salvato, Snackbar.LENGTH_SHORT).show();
                })
                .setNeutralButton(R.string.btn_elimina, (d, which) -> {
                    viewModel.setPaypalHandle("");
                    Snackbar.make(binding.getRoot(), R.string.msg_paypal_salvato, Snackbar.LENGTH_SHORT).show();
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

    private void mostraDialogModificaRevolut() {
        String currentHandle = viewModel.getRevolutHandle();

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_modifica_payment_handle, null);
        TextInputLayout til = dialogView.findViewById(R.id.til_handle);
        TextInputEditText et = dialogView.findViewById(R.id.et_handle);

        til.setHint(getString(R.string.hint_revolut_tag));
        til.setPrefixText("revolut.me/");
        til.setHelperText(getString(R.string.helper_revolut));

        if (!TextUtils.isEmpty(currentHandle)) {
            et.setText(currentHandle);
            et.setSelection(currentHandle.length());
        }

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setPositiveButton(R.string.btn_salva, (d, which) -> {
                    String raw = et.getText() != null ? et.getText().toString().trim() : "";
                    viewModel.setRevolutHandle(raw);
                    Snackbar.make(binding.getRoot(), R.string.msg_revolut_salvato, Snackbar.LENGTH_SHORT).show();
                })
                .setNeutralButton(R.string.btn_elimina, (d, which) -> {
                    viewModel.setRevolutHandle("");
                    Snackbar.make(binding.getRoot(), R.string.msg_revolut_salvato, Snackbar.LENGTH_SHORT).show();
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
