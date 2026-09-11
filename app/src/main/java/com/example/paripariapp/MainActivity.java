package com.example.paripariapp;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.paripariapp.databinding.ActivityMainBinding;
import com.example.paripariapp.ui.view.AccountFragment;
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

        // Controlla automaticamente se nascondere o mostrare la barra
        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            boolean isRootScreen = getSupportFragmentManager().getBackStackEntryCount() == 0;
            binding.bottomNavigation.setVisibility(isRootScreen ? View.VISIBLE : View.GONE);
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
}