package com.example.paripariapp.ui.view;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
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
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseUser;

/**
 * Fragment della schermata "Account".
 * Gestisce sia lo stato di "Account ospite" (con form per Nome, Email e Password)
 * sia lo stato autenticato (con visualizzazione profilo, statistiche e logout).
 */
public class AccountFragment extends Fragment {

    private FragmentAccountBinding binding;
    private AccountViewModel viewModel;

    // true = modalità Registrazione (con Nome), false = modalità Accesso (solo Email e Password)
    private boolean isRegisterMode = true;

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

        viewModel = new ViewModelProvider(this).get(AccountViewModel.class);

        setupObservers();
        setupListeners();
        setupCurrencySelector();
        setupLanguageSelector();
        updateFormModeUI();
    }

    private void setupObservers() {
        // Osserva se siamo in modalità Ospite o Autenticato
        viewModel.getIsGuestMode().observe(getViewLifecycleOwner(), isGuest -> {
            if (Boolean.TRUE.equals(isGuest)) {
                binding.layoutGuest.setVisibility(View.VISIBLE);
                binding.layoutLoggedIn.setVisibility(View.GONE);
            } else {
                binding.layoutGuest.setVisibility(View.GONE);
                binding.layoutLoggedIn.setVisibility(View.VISIBLE);
            }
        });

        // Osserva l'utente autenticato per aggiornare nome ed email
        viewModel.getUserLiveData().observe(getViewLifecycleOwner(), user -> {
            if (user != null && !user.isAnonymous()) {
                String nome = user.getDisplayName();
                binding.tvNomeUtente.setText(!TextUtils.isEmpty(nome) ? nome : getString(R.string.label_account_attivo));
                binding.tvEmailUtente.setText(user.getEmail());
            }
        });

        // Stato di caricamento (ProgressBar)
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loading -> {
            binding.progressBarAuth.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE);
            binding.btnSubmitAuth.setEnabled(!Boolean.TRUE.equals(loading));
            binding.btnSwitchAuthMode.setEnabled(!Boolean.TRUE.equals(loading));
        });

        // Osserva la valuta predefinita
        viewModel.getDefaultCurrencyLive().observe(getViewLifecycleOwner(), currencyCode -> {
            if (binding != null && currencyCode != null) {
                binding.tvValutaPredefinitaValore.setText(currencyCode);
            }
        });

        // Osserva la lingua dell'applicazione
        viewModel.getAppLanguageLive().observe(getViewLifecycleOwner(), langCode -> {
            if (binding != null && langCode != null) {
                String display = UserPreferencesRepository.getDisplayLanguageForCode(langCode);
                binding.tvLinguaAppValore.setText(display);
            }
        });

        // Messaggi di errore
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (!TextUtils.isEmpty(error)) {
                Snackbar.make(binding.getRoot(), error, Snackbar.LENGTH_LONG).show();
            }
        });

        // Messaggi di successo
        viewModel.getSuccessMessage().observe(getViewLifecycleOwner(), msg -> {
            if (!TextUtils.isEmpty(msg)) {
                Snackbar.make(binding.getRoot(), msg, Snackbar.LENGTH_SHORT).show();
                pulisciCampi();
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

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
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

    private void setupListeners() {
        // Toggle tra Registrati e Accedi
        binding.btnSwitchAuthMode.setOnClickListener(v -> {
            isRegisterMode = !isRegisterMode;
            updateFormModeUI();
        });

        // Invio form (Crea Account o Accedi)
        binding.btnSubmitAuth.setOnClickListener(v -> {
            if (validaForm()) {
                String email = getTesto(binding.etEmail);
                String password = getTesto(binding.etPassword);

                if (isRegisterMode) {
                    String nome = getTesto(binding.etNome);
                    viewModel.register(nome, email, password);
                } else {
                    viewModel.login(email, password);
                }
            }
        });

        // Logout
        binding.btnLogout.setOnClickListener(v -> viewModel.logout());
    }

    private void updateFormModeUI() {
        if (isRegisterMode) {
            binding.tilNome.setVisibility(View.VISIBLE);
            binding.btnSubmitAuth.setText(R.string.btn_crea_account);
            binding.btnSwitchAuthMode.setText(R.string.switch_to_login);
        } else {
            binding.tilNome.setVisibility(View.GONE);
            binding.btnSubmitAuth.setText(R.string.btn_accedi);
            binding.btnSwitchAuthMode.setText(R.string.switch_to_register);
        }
    }

    private boolean validaForm() {
        boolean isValid = true;
        binding.tilNome.setError(null);
        binding.tilEmail.setError(null);
        binding.tilPassword.setError(null);

        // Validazione Nome (solo in registrazione)
        if (isRegisterMode) {
            String nome = getTesto(binding.etNome);
            if (TextUtils.isEmpty(nome)) {
                binding.tilNome.setError(getString(R.string.error_nome_obbligatorio));
                isValid = false;
            }
        }

        // Validazione Email
        String email = getTesto(binding.etEmail);
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.setError(getString(R.string.error_email_valida));
            isValid = false;
        }

        // Validazione Password (minimo 6 caratteri per Firebase)
        String password = getTesto(binding.etPassword);
        if (TextUtils.isEmpty(password) || password.length() < 6) {
            binding.tilPassword.setError(getString(R.string.error_password_minima));
            isValid = false;
        }

        return isValid;
    }

    private String getTesto(android.widget.EditText editText) {
        return editText != null && editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    private void pulisciCampi() {
        if (binding.etNome != null) binding.etNome.setText("");
        if (binding.etEmail != null) binding.etEmail.setText("");
        if (binding.etPassword != null) binding.etPassword.setText("");
        binding.tilNome.setError(null);
        binding.tilEmail.setError(null);
        binding.tilPassword.setError(null);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
