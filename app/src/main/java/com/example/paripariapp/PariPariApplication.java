package com.example.paripariapp;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.paripariapp.data.repository.PariPariRepository;
import com.example.paripariapp.data.repository.UserPreferencesRepository;

/**
 * Classe Application principale per inizializzare il Repository,
 * la lingua/locale e il sistema di sincronizzazione all'avvio dell'app.
 * Monitora il ciclo di vita delle Activity per sospendere la sincronizzazione real-time
 * quando l'app è in background, massimizzando il risparmio di batteria, rete e CPU.
 */
public class PariPariApplication extends Application {

    private static final String TAG = "PariPariApplication";
    private PariPariRepository repository;
    private int startedActivitiesCount = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        // Inizializza e applica lingua e tema salvati
        UserPreferencesRepository prefs = UserPreferencesRepository.getInstance(this);
        prefs.applyCurrentLanguage();
        prefs.applyCurrentTheme();

        // Inizializza il repository (Room DB + monitor di rete + Firestore sync)
        repository = PariPariRepository.getInstance(this);

        // Gestione del ciclo di vita foreground / background per risparmio energetico
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                startedActivitiesCount++;
                if (startedActivitiesCount == 1 && repository != null) {
                    Log.d(TAG, "App in foreground: riattivazione sincronizzazione real-time");
                    repository.startRealtimeSync();
                }
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {}

            @Override
            public void onActivityPaused(@NonNull Activity activity) {}

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
                startedActivitiesCount--;
                if (startedActivitiesCount <= 0) {
                    startedActivitiesCount = 0;
                    if (repository != null) {
                        Log.d(TAG, "App in background: sospensione sincronizzazione real-time per risparmio batteria/CPU");
                        repository.stopRealtimeSync();
                    }
                }
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {}
        });
    }

    public PariPariRepository getRepository() {
        return repository;
    }
}
