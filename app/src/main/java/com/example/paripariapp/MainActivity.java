package com.example.paripariapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.paripariapp.databinding.ActivityMainBinding;
import com.example.paripariapp.ui.view.AccountFragment;
import com.example.paripariapp.ui.view.DettaglioSchedaActivity;
import com.example.paripariapp.ui.view.SaldiFragment;
import com.example.paripariapp.ui.view.SpeseFragment;
import com.example.paripariapp.ui.view.ValutaFragment;

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
        String code = com.example.paripariapp.util.CodiceInvitoUtil.estraiCodiceDaUri(uri);
        if (code != null && !code.isEmpty()) {
            intent.setData(null); // Consuma il deep link per evitare ri-esecuzioni su rotazione schermo
            elaboraCodiceDeepLink(code);
        }
    }

    private void elaboraCodiceDeepLink(String code) {
        com.example.paripariapp.data.local.AppDatabase.databaseWriteExecutor.execute(() -> {
            com.example.paripariapp.data.model.Scheda schedaLocale =
                    com.example.paripariapp.data.local.AppDatabase.getInstance(this).schedaDao().getSchedaByCodiceInvito(code);

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (schedaLocale != null) {
                    // L'utente è già membro: apri direttamente la scheda senza mostrare dialog
                    DettaglioSchedaActivity.avvia(MainActivity.this, schedaLocale.getId(), schedaLocale.getTitolo());
                } else {
                    // L'utente non è nel gruppo: apri il bottom sheet pre-compilato con ricerca automatica
                    mostraDialogConfermaJoin(code);
                }
            });
        });
    }

    private void mostraDialogConfermaJoin(String code) {
        com.example.paripariapp.ui.view.UniscitiSchedaBottomSheet.newInstance(code)
                .show(getSupportFragmentManager(), "UniscitiSchedaBottomSheet");
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