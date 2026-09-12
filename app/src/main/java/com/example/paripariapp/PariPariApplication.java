package com.example.paripariapp;

import android.app.Application;import android.content.Intent;
import android.util.Log;

import com.example.paripariapp.data.repository.PariPariRepository;
import com.example.paripariapp.data.repository.UserPreferencesRepository;
import com.google.android.gms.security.ProviderInstaller;

/**
 * Classe Application principale per inizializzare il Repository,
 * la lingua/locale e il sistema di sincronizzazione all'avvio dell'app.
 */
public class PariPariApplication extends Application {

    private static final String TAG = "PariPariApplication";
    private PariPariRepository repository;

    @Override
    public void onCreate() {
        super.onCreate();
        // Inizializza e applica lingua e tema salvati
        UserPreferencesRepository prefs = UserPreferencesRepository.getInstance(this);
        prefs.applyCurrentLanguage();
        prefs.applyCurrentTheme();

        // Inizializza il repository (Room DB + monitor di rete + Firestore sync)
        repository = PariPariRepository.getInstance(this);
    }

    public PariPariRepository getRepository() {
        return repository;
    }
}
