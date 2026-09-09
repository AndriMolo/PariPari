package com.example.paripariapp;

import android.app.Application;

import com.example.paripariapp.data.repository.PariPariRepository;
import com.example.paripariapp.data.repository.UserPreferencesRepository;

/**
 * Classe Application principale per inizializzare il Repository,
 * la lingua/locale e il sistema di sincronizzazione all'avvio dell'app.
 */
public class PariPariApplication extends Application {

    private PariPariRepository repository;

    @Override
    public void onCreate() {
        super.onCreate();
        // Inizializza e applica la lingua/locale salvata
        UserPreferencesRepository.getInstance(this).applyCurrentLanguage();

        // Inizializza il repository (Room DB + monitor di rete + Firestore sync)
        repository = PariPariRepository.getInstance(this);
    }

    public PariPariRepository getRepository() {
        return repository;
    }
}
