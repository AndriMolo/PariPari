package com.example.paripariapp;

import android.app.Application;

import com.example.paripariapp.data.repository.PariPariRepository;

/**
 * Classe Application principale per inizializzare il Repository
 * e il sistema di sincronizzazione all'avvio dell'app.
 */
public class PariPariApplication extends Application {

    private PariPariRepository repository;

    @Override
    public void onCreate() {
        super.onCreate();
        // Inizializza il repository (Room DB + monitor di rete + Firestore sync)
        repository = PariPariRepository.getInstance(this);
    }

    public PariPariRepository getRepository() {
        return repository;
    }
}
