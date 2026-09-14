package com.example.paripariapp.util;

import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Utility per il feedback aptico secondo i Golden Standard di Android e Material 3.
 * Fornisce micro-vibrazioni contestuali che rendono l'interazione fluida e reattiva (stile fintech).
 */
public final class HapticUtil {

    private HapticUtil() {}

    /**
     * Feedback leggero per selezioni, toggle di chip e switch (es. divisione equa/percentuale).
     */
    public static void tick(@Nullable View view) {
        if (view == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK);
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
    }

    /**
     * Feedback tattile per tap su bottoni primari o interazioni standard.
     */
    public static void tap(@Nullable View view) {
        if (view == null) return;
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    /**
     * Feedback marcato di successo/conferma (es. registrazione pagamento o spesa salvata).
     */
    public static void confirm(@Nullable View view) {
        if (view == null) return;
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
    }

    /**
     * Feedback di rifiuto o errore (es. saldo non valido, divisione non bilanciata).
     */
    public static void reject(@Nullable View view) {
        if (view == null) return;
        view.performHapticFeedback(HapticFeedbackConstants.REJECT);
    }
}
