package com.example.paripariapp.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.paripariapp.data.repository.UserPreferencesRepository;

import java.util.Locale;

/**
 * Utilità per la formattazione coerente di importi monetari e valute in tutta l'applicazione.
 * Centralizza la gestione dei decimali, delle valute di default, dei segni e dei formati di input.
 */
public final class ImportoUtil {

    private ImportoUtil() {
        // Utility class
    }

    /**
     * Formatta un importo con 2 decimali e il codice valuta (es. "15.00 EUR").
     *
     * @param importo Valore numerico dell'importo
     * @param valuta  Codice valuta (se nullo o vuoto, usa la valuta di default "EUR")
     * @return Stringa formattata con importo e valuta
     */
    @NonNull
    public static String formatta(double importo, @Nullable String valuta) {
        String valutaEffettiva = (valuta != null && !valuta.trim().isEmpty())
                ? valuta.trim()
                : UserPreferencesRepository.DEFAULT_CURRENCY;
        return String.format(Locale.getDefault(), "%.2f %s", importo, valutaEffettiva);
    }

    /**
     * Formatta un importo esplicitando il segno positivo o negativo (es. "+10.00 EUR", "-5.50 EUR", "0.00 EUR").
     *
     * @param importo Valore del saldo (positivo per crediti, negativo per debiti)
     * @param valuta  Codice valuta
     * @return Stringa formattata con segno esplicito
     */
    @NonNull
    public static String formattaConSegno(double importo, @Nullable String valuta) {
        String valutaEffettiva = (valuta != null && !valuta.trim().isEmpty())
                ? valuta.trim()
                : UserPreferencesRepository.DEFAULT_CURRENCY;

        if (importo > 0.0001) {
            return String.format(Locale.getDefault(), "+%.2f %s", importo, valutaEffettiva);
        } else if (importo < -0.0001) {
            return String.format(Locale.getDefault(), "-%.2f %s", Math.abs(importo), valutaEffettiva);
        } else {
            return String.format(Locale.getDefault(), "%.2f %s", 0.0, valutaEffettiva);
        }
    }

    /**
     * Formatta un importo per campi di input numerici / EditText (es. "10.00") con punto decimale standard.
     */
    @NonNull
    public static String formattaPerInput(double importo) {
        return String.format(Locale.US, "%.2f", importo);
    }
}
