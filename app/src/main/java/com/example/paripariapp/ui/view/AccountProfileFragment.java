package com.example.paripariapp.ui.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.paripariapp.R;
import com.example.paripariapp.databinding.FragmentAccountProfileBinding;
import com.example.paripariapp.ui.viewmodel.AccountViewModel;
import com.example.paripariapp.util.AppSnackbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseUser;

/**
 * Child fragment dedicato alla gestione del profilo dell'utente autenticato.
 * Include avatar, modifica nome/email, stato e rinnovo verifica email, e logout.
 */
public class AccountProfileFragment extends Fragment {

    private FragmentAccountProfileBinding binding;
    private AccountViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAccountProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(AccountViewModel.class);

        setupObservers();
        setupListeners();
    }

    private void setupObservers() {
        // Aggiorna nome ed email dell'utente autenticato
        viewModel.getUserLiveData().observe(getViewLifecycleOwner(), user -> {
            if (binding == null) return;
            if (user != null) {
                String nome = user.getDisplayName();
                String displayNome = !TextUtils.isEmpty(nome) ? nome : getString(R.string.default_nome_utente);
                binding.tvNomeUtente.setText(displayNome);
                if (!user.isAnonymous()) {
                    binding.tvEmailUtente.setText(user.getEmail());
                }
                if (user.getPhotoUrl() != null) {
                    com.example.paripariapp.util.ImageLoaderUtil.caricaImmagine(user.getPhotoUrl().toString(), binding.ivAvatar, R.drawable.ic_account);
                }
            }
        });

        // Osserva lo stato di verifica dell'email
        viewModel.getIsEmailVerifiedLive().observe(getViewLifecycleOwner(), verified -> {
            if (binding == null) return;
            Context context = getContext();
            if (context == null) return;
            boolean isVerified = Boolean.TRUE.equals(verified);
            if (isVerified) {
                viewModel.stopEmailVerificationPolling();
                binding.cardBadgeEmail.setCardBackgroundColor(ContextCompat.getColor(context, R.color.credit_green_bg));
                binding.ivBadgeEmailIcon.setImageResource(R.drawable.ic_check_circle);
                binding.ivBadgeEmailIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.credit_green)));
                binding.tvBadgeEmailTesto.setText(R.string.badge_verificata);
                binding.tvBadgeEmailTesto.setTextColor(ContextCompat.getColor(context, R.color.credit_green));
            } else {
                binding.cardBadgeEmail.setCardBackgroundColor(ContextCompat.getColor(context, R.color.warning_orange_bg));
                binding.ivBadgeEmailIcon.setImageResource(R.drawable.ic_warning_amber);
                binding.ivBadgeEmailIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.warning_orange)));
                binding.tvBadgeEmailTesto.setText(R.string.badge_non_verificata);
                binding.tvBadgeEmailTesto.setTextColor(ContextCompat.getColor(context, R.color.warning_orange));
                if (isResumed()) {
                    viewModel.startEmailVerificationPolling();
                }
            }
        });

        // Messaggi di errore
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (!TextUtils.isEmpty(error) && binding != null) {
                AppSnackbar.showLong(binding.getRoot(), error);
                viewModel.clearErrorMessage();
            }
        });

        // Messaggi di successo
        viewModel.getSuccessMessage().observe(getViewLifecycleOwner(), msg -> {
            if (!TextUtils.isEmpty(msg) && binding != null) {
                AppSnackbar.show(binding.getRoot(), msg);
                viewModel.clearSuccessMessage();
            }
        });
    }

    private void setupListeners() {
        // Personalizza Avatar
        binding.ivAvatar.setOnClickListener(v -> BottomSheetAvatarUtente.newInstance().show(getChildFragmentManager(), "BottomSheetAvatarUtente"));

        // Modifica nome profilo
        binding.containerNomeUtente.setOnClickListener(v -> mostraDialogModificaNomeProfilo());
        binding.tvNomeUtente.setOnClickListener(v -> mostraDialogModificaNomeProfilo());

        // Modifica email utente
        binding.containerEmailUtente.setOnClickListener(v -> showDialogModificaEmail());

        // Logout
        binding.btnLogout.setOnClickListener(v -> viewModel.logout());

        // Verifica email (reinvia se non verificata)
        binding.rowVerificaEmail.setOnClickListener(v -> {
            if (binding == null) return;
            Boolean verified = viewModel.getIsEmailVerifiedLive().getValue();
            if (Boolean.TRUE.equals(verified)) {
                AppSnackbar.show(binding.getRoot(), R.string.msg_email_gia_verificata);
                return;
            }
            viewModel.reinviaEmailVerifica();
        });
    }

    private void mostraDialogModificaNomeProfilo() {
        FirebaseUser currentUser = viewModel.getUserLiveData().getValue();
        String currentName = (currentUser != null && !TextUtils.isEmpty(currentUser.getDisplayName()))
                ? currentUser.getDisplayName()
                : (binding != null && binding.tvNomeUtente != null && binding.tvNomeUtente.getText() != null
                   ? binding.tvNomeUtente.getText().toString().trim() : "");

        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setHint(R.string.hint_nome_partecipante);
        if (!currentName.equalsIgnoreCase(getString(R.string.default_nome_utente))) {
            input.setText(currentName);
            if (!currentName.isEmpty()) {
                input.setSelection(currentName.length());
            }
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
                    if (!nuovoNome.isEmpty()) {
                        viewModel.aggiornaNomeProfilo(nuovoNome);
                    }
                })
                .setNegativeButton(R.string.btn_annulla, null)
                .show();
    }

    private void showDialogModificaEmail() {
        FirebaseUser user = viewModel.getUserLiveData().getValue();
        if (user == null || user.isAnonymous()) {
            return;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        com.example.paripariapp.databinding.DialogModificaEmailBinding dialogBinding =
                com.example.paripariapp.databinding.DialogModificaEmailBinding.inflate(getLayoutInflater());
        dialog.setContentView(dialogBinding.getRoot());

        dialogBinding.btnDialogAnnulla.setOnClickListener(v -> dialog.dismiss());

        dialogBinding.btnDialogConferma.setOnClickListener(v -> {
            String nuovaEmail = dialogBinding.etNuovaEmail.getText() != null
                    ? dialogBinding.etNuovaEmail.getText().toString().trim() : "";
            String password = dialogBinding.etPasswordAttuale.getText() != null
                    ? dialogBinding.etPasswordAttuale.getText().toString().trim() : "";

            boolean valid = true;
            dialogBinding.tilNuovaEmail.setError(null);
            dialogBinding.tilPasswordAttuale.setError(null);

            if (TextUtils.isEmpty(nuovaEmail) || !Patterns.EMAIL_ADDRESS.matcher(nuovaEmail).matches()) {
                dialogBinding.tilNuovaEmail.setError(getString(R.string.error_email_valida));
                valid = false;
            } else if (user.getEmail() != null && user.getEmail().equalsIgnoreCase(nuovaEmail)) {
                dialogBinding.tilNuovaEmail.setError(getString(R.string.error_email_uguale));
                valid = false;
            }

            if (TextUtils.isEmpty(password)) {
                dialogBinding.tilPasswordAttuale.setError(getString(R.string.error_password_vuota));
                valid = false;
            }

            if (valid) {
                viewModel.modificaEmail(nuovaEmail, password);
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) {
            viewModel.reloadUser();
            Boolean verified = viewModel.getIsEmailVerifiedLive().getValue();
            if (!Boolean.TRUE.equals(verified)) {
                viewModel.startEmailVerificationPolling();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (viewModel != null) {
            viewModel.stopEmailVerificationPolling();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (viewModel != null) {
            viewModel.stopEmailVerificationPolling();
        }
        binding = null;
    }
}
