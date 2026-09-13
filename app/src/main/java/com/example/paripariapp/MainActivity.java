package com.example.paripariapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.example.paripariapp.data.repository.PariPariRepository;
import com.example.paripariapp.databinding.ActivityMainBinding;
import com.example.paripariapp.ui.view.AccountFragment;
import com.example.paripariapp.ui.view.DettaglioSchedaActivity;
import com.example.paripariapp.ui.view.SaldiFragment;
import com.example.paripariapp.ui.view.SpeseFragment;
import com.example.paripariapp.ui.view.ValutaFragment;
import com.example.paripariapp.ui.viewmodel.SpeseViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Activity principale dell'app PariPari.
 * Gestisce la navigazione tra le 4 pagine tramite la BottomNavigationView.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });


        if (savedInstanceState == null) {
            loadFragment(new SpeseFragment());
        }

        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.nav_spese) {
                selectedFragment = new SpeseFragment();
            } else if (itemId == R.id.nav_saldi) {
                selectedFragment = new SaldiFragment();
            } else if (itemId == R.id.nav_valuta) {
                selectedFragment = new ValutaFragment();
            } else if (itemId == R.id.nav_account) {
                selectedFragment = new AccountFragment();
            }

            return loadFragment(selectedFragment);
        });

        binding.bottomNavigation.setOnItemReselectedListener(item -> {
            // No-op
        });

        gestisciDeepLink(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && !user.isAnonymous()) {
            user.reload();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        gestisciDeepLink(intent);
    }

    private void gestisciDeepLink(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        Uri uri = intent.getData();
        String scheme = uri.getScheme();
        String host = uri.getHost();

        if (("paripari".equalsIgnoreCase(scheme) && "join".equalsIgnoreCase(host)) ||
            ("https".equalsIgnoreCase(scheme) && "paripari.app".equalsIgnoreCase(host))) {
            String code = uri.getQueryParameter("code");
            if (code != null && !code.trim().isEmpty()) {
                mostraDialogConfermaJoin(code.trim().toUpperCase());
            }
        }
    }

    private void mostraDialogConfermaJoin(String code) {
        com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        String defaultName = (currentUser != null && currentUser.getDisplayName() != null && !android.text.TextUtils.isEmpty(currentUser.getDisplayName()))
                ? currentUser.getDisplayName().trim() : "Io";

        android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setHint(R.string.label_tuo_nome_nel_gruppo);
        input.setText(defaultName);
        if (!defaultName.isEmpty()) {
            input.setSelection(defaultName.length());
        }

        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding / 2, padding, 0);
        container.addView(input);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_titolo_conferma_unione)
                .setMessage(getString(R.string.dialog_msg_conferma_unione, code))
                .setView(container)
                .setPositiveButton(R.string.btn_unisciti, (dialog, which) -> {
                    String nomeScelto = input.getText().toString().trim();
                    if (nomeScelto.isEmpty()) nomeScelto = defaultName;

                    Toast.makeText(this, R.string.msg_ricerca_gruppo, Toast.LENGTH_SHORT).show();
                    SpeseViewModel viewModel = new ViewModelProvider(this).get(SpeseViewModel.class);
                    viewModel.uniscitiASchedaConNome(code, nomeScelto, new PariPariRepository.OnJoinSchedaCallback() {
                        @Override
                        public void onSuccess(String schedaId, String titolo) {
                            Toast.makeText(MainActivity.this, getString(R.string.msg_unione_successo, titolo), Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(MainActivity.this, DettaglioSchedaActivity.class);
                            intent.putExtra(DettaglioSchedaActivity.EXTRA_SCHEDA_ID, schedaId);
                            intent.putExtra(DettaglioSchedaActivity.EXTRA_TITOLO, titolo);
                            startActivity(intent);
                        }

                        @Override
                        public void onError(String errore) {
                            Toast.makeText(MainActivity.this, errore, Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton(R.string.action_annulla, null)
                .show();
    }

    private boolean loadFragment(Fragment fragment) {
        if (fragment == null) return false;
        // Pulisce l'eventuale backstack residuo quando si passa da un tab principale all'altro
        getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
        return true;
    }

    public void navigaVersoAccount() {
        binding.bottomNavigation.setSelectedItemId(R.id.nav_account);
    }
}