package com.example.paripariapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.paripariapp.data.repository.UserPreferencesRepository;
import com.example.paripariapp.databinding.ActivityMainBinding;
import com.example.paripariapp.ui.view.AccountFragment;
import com.example.paripariapp.ui.view.DettaglioSchedaActivity;
import com.example.paripariapp.ui.view.SaldiFragment;
import com.example.paripariapp.ui.view.SpeseFragment;
import com.example.paripariapp.ui.view.ValutaFragment;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Activity principale dell'app PariPari.
 * Gestisce la navigazione tra le 4 pagine tramite la BottomNavigationView.
 */
public class MainActivity extends AppCompatActivity {

    private static final String KEY_SELECTED_TAB = "key_selected_tab_id";
    private static final String KEY_TAB_STACK = "key_tab_stack";

    private ActivityMainBinding binding;
    private Fragment currentFragment;
    private int currentSelectedTabId = R.id.nav_spese;
    private final Deque<Integer> tabBackStack = new ArrayDeque<>();
    private boolean isNavigatingBack = false;

    private final androidx.activity.result.ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), isGranted -> {
                // Permesso notifiche gestito
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.core.splashscreen.SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        richiediPermessoNotificheSeNecessario();
        aggiornaFcmTokenSeLoggato();

        UserPreferencesRepository prefs = UserPreferencesRepository.getInstance(this);

        if (savedInstanceState != null) {
            currentSelectedTabId = savedInstanceState.getInt(KEY_SELECTED_TAB, R.id.nav_spese);
            ArrayList<Integer> savedStack = savedInstanceState.getIntegerArrayList(KEY_TAB_STACK);
            if (savedStack != null) {
                tabBackStack.clear();
                tabBackStack.addAll(savedStack);
            }
        } else if (prefs.isPendingConfigChange()) {
            prefs.setPendingConfigChange(false);
            currentSelectedTabId = prefs.getLastActiveTab(R.id.nav_spese);
            List<Integer> savedStack = prefs.getSavedTabStack();
            tabBackStack.clear();
            tabBackStack.addAll(savedStack);
        } else {
            currentSelectedTabId = R.id.nav_spese;
            tabBackStack.clear();
            prefs.setLastActiveTab(R.id.nav_spese);
            prefs.setSavedTabStack(tabBackStack);
        }

        binding.bottomNavigation.setSelectedItemId(currentSelectedTabId);
        mostraFragmentTab(currentSelectedTabId);

        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == currentSelectedTabId) {
                return true;
            }
            if (!isNavigatingBack) {
                tabBackStack.remove(itemId);
                tabBackStack.addLast(currentSelectedTabId);
                prefs.setSavedTabStack(tabBackStack);
            }
            currentSelectedTabId = itemId;
            prefs.setLastActiveTab(itemId);
            return mostraFragmentTab(itemId);
        });

        binding.bottomNavigation.setOnItemReselectedListener(item -> {
            // No-op
        });

        setupBackPressHandler();

        gestisciDeepLink(getIntent());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_SELECTED_TAB, currentSelectedTabId);
        outState.putIntegerArrayList(KEY_TAB_STACK, new ArrayList<>(tabBackStack));
    }

    private void setupBackPressHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                FragmentManager fm = getSupportFragmentManager();
                if (fm.getBackStackEntryCount() > 0) {
                    fm.popBackStack();
                    return;
                }

                if (!tabBackStack.isEmpty()) {
                    int previousTab = tabBackStack.removeLast();
                    UserPreferencesRepository.getInstance(MainActivity.this).setSavedTabStack(tabBackStack);
                    isNavigatingBack = true;
                    binding.bottomNavigation.setSelectedItemId(previousTab);
                    isNavigatingBack = false;
                } else if (currentSelectedTabId != R.id.nav_spese) {
                    isNavigatingBack = true;
                    binding.bottomNavigation.setSelectedItemId(R.id.nav_spese);
                    isNavigatingBack = false;
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
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

    private boolean mostraFragmentTab(int itemId) {
        String tag;
        if (itemId == R.id.nav_spese) {
            tag = "tab_spese";
        } else if (itemId == R.id.nav_saldi) {
            tag = "tab_saldi";
        } else if (itemId == R.id.nav_valuta) {
            tag = "tab_valuta";
        } else if (itemId == R.id.nav_account) {
            tag = "tab_account";
        } else {
            return false;
        }

        FragmentManager fm = getSupportFragmentManager();
        fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

        Fragment targetFragment = fm.findFragmentByTag(tag);
        androidx.fragment.app.FragmentTransaction transaction = fm.beginTransaction();

        // Nasconde esplicitamente tutti gli altri fragment esistenti per evitare sovrapposizioni dopo ricreazione
        for (Fragment f : fm.getFragments()) {
            if (f != null && f != targetFragment) {
                transaction.hide(f);
            }
        }

        if (targetFragment == null) {
            if (itemId == R.id.nav_spese) {
                targetFragment = new SpeseFragment();
            } else if (itemId == R.id.nav_saldi) {
                targetFragment = new SaldiFragment();
            } else if (itemId == R.id.nav_valuta) {
                targetFragment = new ValutaFragment();
            } else {
                targetFragment = new AccountFragment();
            }
            transaction.add(R.id.fragment_container, targetFragment, tag);
        } else {
            transaction.show(targetFragment);
        }

        currentFragment = targetFragment;
        transaction.commit();
        return true;
    }

    public void impostaVisibilitaBottomNav(boolean visibile) {
        if (binding != null && binding.bottomNavigation != null) {
            binding.bottomNavigation.setVisibility(visibile ? View.VISIBLE : View.GONE);
        }
    }

    private void aggiornaFcmTokenSeLoggato() {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(com.example.paripariapp.service.PariPariMessagingService::inviaTokenAlServer)
                .addOnFailureListener(e -> android.util.Log.w("MainActivity", "Errore recupero token FCM", e));
    }

    private void richiediPermessoNotificheSeNecessario() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    public void navigaVersoAccount() {
        binding.bottomNavigation.setSelectedItemId(R.id.nav_account);
    }
}