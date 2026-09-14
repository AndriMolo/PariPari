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

    public static final String INVITE_HOST_FIREBASE = "paripari-app-2026.web.app";
    public static final String INVITE_BASE_URL = "https://" + INVITE_HOST_FIREBASE + "/join";
    public static final String CUSTOM_SCHEME_JOIN = "paripari://join";

    /**
     * Pulisce e normalizza un codice inserito dall'utente (rimuove spazi, converte in maiuscolo).
     */
    public static String normalizzaCodice(String input) {
        if (input == null) return "";
        return input.trim().replace(" ", "").toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * Genera l'URL HTTPS completo di invito da condividere.
     */
    public static String generaLinkInvito(String codice) {
        String pulito = normalizzaCodice(codice);
        return INVITE_BASE_URL + "?code=" + pulito;
    }

    /**
     * Estrae e normalizza il codice di invito da una stringa URL o deep link.
     */
    public static String estraiCodiceDaUrlString(String urlString) {
        if (urlString == null || urlString.trim().isEmpty()) return null;
        String trim = urlString.trim();

        // 1. Cerca parametro query: ?code=... oppure &code=...
        int codeIdx = trim.indexOf("code=");
        if (codeIdx != -1) {
            String sub = trim.substring(codeIdx + 5);
            int ampIdx = sub.indexOf('&');
            int hashIdx = sub.indexOf('#');
            int endIdx = sub.length();
            if (ampIdx != -1) endIdx = Math.min(endIdx, ampIdx);
            if (hashIdx != -1) endIdx = Math.min(endIdx, hashIdx);
            String rawCode = sub.substring(0, endIdx);
            if (!rawCode.isEmpty()) {
                return normalizzaCodice(rawCode);
            }
        }

        // 2. Cerca path /join/...
        int joinIdx = trim.indexOf("/join/");
        if (joinIdx != -1) {
            String sub = trim.substring(joinIdx + 6);
            int qIdx = sub.indexOf('?');
            int slashIdx = sub.indexOf('/');
            int hashIdx = sub.indexOf('#');
            int endIdx = sub.length();
            if (qIdx != -1) endIdx = Math.min(endIdx, qIdx);
            if (slashIdx != -1) endIdx = Math.min(endIdx, slashIdx);
            if (hashIdx != -1) endIdx = Math.min(endIdx, hashIdx);
            String rawCode = sub.substring(0, endIdx);
            if (!rawCode.isEmpty()) {
                return normalizzaCodice(rawCode);
            }
        }

        return null;
    }

    /**
     * Estrae e normalizza il codice di invito da un Uri (supporta sia ?code=XYZ sia /join/XYZ sia pariari://join?code=XYZ).
     */
    public static String estraiCodiceDaUri(android.net.Uri uri) {
        if (uri == null) return null;
        String scheme = uri.getScheme();
        String host = uri.getHost();

        boolean isCustomScheme = "paripari".equalsIgnoreCase(scheme) && "join".equalsIgnoreCase(host);
        boolean isHttpsHost = ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) && (
                "paripari-app-2026.web.app".equalsIgnoreCase(host) ||
                "paripari-app-2026.firebaseapp.com".equalsIgnoreCase(host) ||
                "paripari.app".equalsIgnoreCase(host)
        );

        if (!isCustomScheme && !isHttpsHost) {
            return null;
        }

        return estraiCodiceDaUrlString(uri.toString());
    }
}
