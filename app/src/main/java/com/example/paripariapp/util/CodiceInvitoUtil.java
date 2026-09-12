package com.example.paripariapp.util;

import java.security.SecureRandom;

/**
 * Utility per la generazione di codici di invito brevi a 6 caratteri alfanumerici.
 * Esclude caratteri ambigui come '0', 'O', '1', 'I' per facilitare la lettura e la digitazione.
 */
public final class CodiceInvitoUtil {

    private static final String CARATTERI_PERMESSI = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LUNGHEZZA_CODICE = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private CodiceInvitoUtil() {
        // Classe di utilità statica
    }

    /**
     * Genera un codice alfanumerico casuale di 6 caratteri in maiuscolo.
     */
    public static String generaCodice() {
        StringBuilder sb = new StringBuilder(LUNGHEZZA_CODICE);
        for (int i = 0; i < LUNGHEZZA_CODICE; i++) {
            int indice = RANDOM.nextInt(CARATTERI_PERMESSI.length());
            sb.append(CARATTERI_PERMESSI.charAt(indice));
        }
        return sb.toString();
    }

    /**
     * Pulisce e normalizza un codice inserito dall'utente (rimuove spazi, converte in maiuscolo).
     */
    public static String normalizzaCodice(String input) {
        if (input == null) return "";
        return input.trim().replace(" ", "").toUpperCase();
    }
}
