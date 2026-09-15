package com.example.paripariapp.ui.view;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.paripariapp.R;
import com.example.paripariapp.databinding.FragmentAccountGuestBinding;
import com.example.paripariapp.ui.viewmodel.AccountViewModel;
import com.example.paripariapp.util.AppSnackbar;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseUser;

/**
 * Child fragment dedicato alla gestione dell'utente non autenticato (Ospite).
 * Include la modifica del nome locale, il form di registrazione e di accesso,
 * il flusso di recupero password e l'accesso rapido con Google.
 */
public class AccountGuestFragment extends Fragment {

    private static final String TAG = "AccountGuestFragment";

    private FragmentAccountGuestBinding binding;
    private AccountViewModel viewModel;

    // true = registrazione (con Nome), false = accesso (solo Email e Password)
    private boolean isRegisterMode = true;
    private OnBackPressedCallback backCallback;

    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private GoogleSignInClient googleSignInClient;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Intent data = result.getData();
                    if (data != null) {
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                        try {
                            GoogleSignInAccount account = task.getResult(ApiException.class);
                            if (account != null && account.getIdToken() != null) {
                                viewModel.accediConGoogle(account.getIdToken());
                            } else {
                                AppSnackbar.show(binding != null ? binding.getRoot() : requireView(), getString(R.string.error_credenziali_google));
                            }
                        } catch (ApiException e) {
                            Log.w(TAG, "Accesso con Google fallito: code=" + e.getStatusCode(), e);
                            if (e.getStatusCode() != GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                                String msg = e.getLocalizedMessage() != null ? e.getLocalizedMessage() : "Errore Google Sign-In (Code: " + e.getStatusCode() + ")";
                                if (e.getStatusCode() == 10) {
                                    msg = "Errore di configurazione Google (Developer Error 10). Verifica impronta SHA-1 su Firebase Console.";
                                }
                                AppSnackbar.show(binding != null ? binding.getRoot() : requireView(), msg);
                            }
                        }
                    } else if (result.getResultCode() != Activity.RESULT_CANCELED) {
                        AppSnackbar.show(binding != null ? binding.getRoot() : requireView(), "Accesso con Google non riuscito");
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAccountGuestBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(AccountViewModel.class);

        setupGoogleSignIn();
        setupBackPressHandler();
        setupObservers();
        setupListeners();
        updateFormModeUI();
    }

    private void setupGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .requestProfile()
                .build();
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso);
    }

    private void avviaAccessoGoogle() {
        if (googleSignInClient == null) return;
        googleSignInClient.signOut().addOnCompleteListener(task -> {
            if (googleSignInLauncher != null) {
                googleSignInLauncher.launch(googleSignInClient.getSignInIntent());
            }
        });
    }

    private void setupBackPressHandler() {
        backCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                chiudiFormAuth();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backCallback);
    }

    private void setupObservers() {
        // Aggiorna il nome dell'ospite visualizzato
        viewModel.getUserLiveData().observe(getViewLifecycleOwner(), user -> {
            if (binding == null) return;
            if (user != null) {
                String nome = user.getDisplayName();
                String displayNome = !TextUtils.isEmpty(nome) ? nome : getString(R.string.default_nome_utente);
                binding.tvNomeOspiteValore.setText(displayNome);
            }
        });

        // Stato di caricamento (ProgressBar & pulsanti)
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loading -> {
            if (binding == null) return;
            boolean isLoading = Boolean.TRUE.equals(loading);
            binding.progressBarAuth.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.btnSubmitAuth.setEnabled(!isLoading);
            binding.btnSwitchAuthMode.setEnabled(!isLoading);
            binding.btnChiudiAuth.setEnabled(!isLoading);
            binding.btnMostraRegistrazione.setEnabled(!isLoading);
            binding.btnMostraLogin.setEnabled(!isLoading);
            binding.btnGoogleSigninInitial.setEnabled(!isLoading);
            binding.btnGoogleSigninForm.setEnabled(!isLoading);
        });

        // Messaggi di errore
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (!TextUtils.isEmpty(error) && binding != null) {
                AppSnackbar.showLong(binding.getRoot(), error);
                viewModel.clearErrorMessage();
            }
        });

        // Messaggi di success
        viewModel.getSuccessMessage().observe(getViewLifecycleOwner(), msg -> {
            if (!TextUtils.isEmpty(msg) && binding != null) {
                AppSnackbar.show(binding.getRoot(), msg);
                chiudiFormAuth();
                viewModel.clearSuccessMessage();
            }
        });
    }

    private void setupListeners() {
        // Modifica nome ospite
        binding.cardNomeOspite.setOnClickListener(v -> mostraDialogModificaNomeProfilo());

        // Apertura form da pulsanti iniziali
        binding.btnMostraRegistrazione.setOnClickListener(v -> apriFormAuth(true));
        binding.btnMostraLogin.setOnClickListener(v -> apriFormAuth(false));

        // Chiusura form
        binding.btnChiudiAuth.setOnClickListener(v -> chiudiFormAuth());

        // Accesso Google (sia iniziale sia dentro il form)
        binding.btnGoogleSigninInitial.setOnClickListener(v -> avviaAccessoGoogle());
        binding.btnGoogleSigninForm.setOnClickListener(v -> avviaAccessoGoogle());

        // Toggle Registrati / Accedi all'interno del form
        binding.btnSwitchAuthMode.setOnClickListener(v -> {
            isRegisterMode = !isRegisterMode;
            updateFormModeUI();
        });

        // Invio credenziali
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

        // Password dimenticata (visibile in modalità login)
        binding.tvPasswordDimenticata.setOnClickListener(v -> {
            String email = getTesto(binding.etEmail);
            showDialogRecuperoPassword(email);
        });
    }

    private void apriFormAuth(boolean registerMode) {
        if (binding == null) return;
        isRegisterMode = registerMode;
        updateFormModeUI();
        binding.containerAuthButtons.setVisibility(View.GONE);
        binding.cardAuthForm.setVisibility(View.VISIBLE);
        if (backCallback != null) {
            backCallback.setEnabled(true);
        }
        if (isRegisterMode && binding.etNome != null) {
            binding.etNome.requestFocus();
        } else if (binding.etEmail != null) {
            binding.etEmail.requestFocus();
        }
    }

    private void chiudiFormAuth() {
        if (binding == null) return;
        nascondiTastiera();
        binding.cardAuthForm.setVisibility(View.GONE);
        binding.containerAuthButtons.setVisibility(View.VISIBLE);
        if (backCallback != null) {
            backCallback.setEnabled(false);
        }
        pulisciCampi();
    }

    private void nascondiTastiera() {
        if (getActivity() == null) return;
        View currentFocus = getActivity().getCurrentFocus();
        if (currentFocus != null) {
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            }
        }
    }

    private void updateFormModeUI() {
        if (binding == null) return;
        if (isRegisterMode) {
            binding.tvTitoloFormAuth.setText(R.string.btn_crea_account);
            binding.tilNome.setVisibility(View.VISIBLE);
            binding.tvPasswordDimenticata.setVisibility(View.GONE);
            binding.btnSubmitAuth.setText(R.string.btn_crea_account);
            binding.btnSwitchAuthMode.setText(R.string.switch_to_login);
        } else {
            binding.tvTitoloFormAuth.setText(R.string.btn_accedi);
            binding.tilNome.setVisibility(View.GONE);
            binding.tvPasswordDimenticata.setVisibility(View.VISIBLE);
            binding.btnSubmitAuth.setText(R.string.btn_accedi);
            binding.btnSwitchAuthMode.setText(R.string.switch_to_register);
        }
    }

    private boolean validaForm() {
        if (binding == null) return false;
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

    private void mostraDialogModificaNomeProfilo() {
        FirebaseUser currentUser = viewModel.getUserLiveData().getValue();
        String currentName = (currentUser != null && !TextUtils.isEmpty(currentUser.getDisplayName()))
                ? currentUser.getDisplayName() : "";

        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setHint(R.string.hint_nome_partecipante);
        input.setText(currentName);
        if (!currentName.isEmpty()) {
            input.setSelection(currentName.length());
        }

        FrameLayout container = new FrameLayout(requireContext());
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding / 2, padding, 0);
        container.addView(input);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_titolo_modifica_nome_profilo)
                .setView(container)
                .setPositiveButton(R.string.btn_salva, (dialog, which) -> {
                    String nuovoNome = input.getText().toString().trim();
                    if (!nuovoNome.isEmpty() && !nuovoNome.equals(currentName)) {
                        viewModel.aggiornaNomeProfilo(nuovoNome);
                    }
                })
                .setNegativeButton(R.string.btn_annulla, null)
                .show();
    }

    private void showDialogRecuperoPassword(String emailPrecompilata) {
        if (!TextUtils.isEmpty(emailPrecompilata) && Patterns.EMAIL_ADDRESS.matcher(emailPrecompilata).matches()) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.dialog_titolo_reset_password)
                    .setMessage(getString(R.string.dialog_msg_conferma_reset, emailPrecompilata))
                    .setPositiveButton(R.string.btn_reimposta_password, (dialog, which) -> {
                        viewModel.inviaEmailRecuperoPassword(emailPrecompilata);
                    })
                    .setNegativeButton(R.string.btn_annulla, null)
                    .show();
        } else {
            final TextInputLayout til = new TextInputLayout(requireContext(), null, com.google.android.material.R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox);
            til.setHint(getString(R.string.hint_email));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int margin = (int) (20 * getResources().getDisplayMetrics().density);
            lp.setMargins(margin, margin / 2, margin, 0);
            til.setLayoutParams(lp);

            final TextInputEditText input = new TextInputEditText(til.getContext());
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            if (!TextUtils.isEmpty(emailPrecompilata)) {
                input.setText(emailPrecompilata);
            }
            til.addView(input);

            FrameLayout container = new FrameLayout(requireContext());
            container.addView(til);

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.dialog_titolo_reset_password)
                    .setMessage(R.string.dialog_msg_inserisci_email_reset)
                    .setView(container)
                    .setPositiveButton(R.string.btn_reimposta_password, (dialog, which) -> {
                        String email = input.getText() != null ? input.getText().toString().trim() : "";
                        if (!TextUtils.isEmpty(email) && Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                            viewModel.inviaEmailRecuperoPassword(email);
                        } else if (binding != null) {
                            AppSnackbar.show(binding.getRoot(), R.string.error_email_valida);
                        }
                    })
                    .setNegativeButton(R.string.btn_annulla, null)
                    .show();
        }
    }

    private String getTesto(EditText editText) {
        return editText != null && editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    private void pulisciCampi() {
        if (binding == null) return;
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
