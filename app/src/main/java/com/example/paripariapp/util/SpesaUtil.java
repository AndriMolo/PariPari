package com.example.paripariapp.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.Spesa;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility per la formattazione e localizzazione dinamica dei titoli delle spese e dei rimborsi.
 */
public final class SpesaUtil {

    private SpesaUtil() {
        // Utility class
    }

    private static final Pattern PATTERN_RIMBORSO = Pattern.compile(
            "^(?:Rimborso|Reimbursement|Reembolso|Remboursement|Rückzahlung)\\s*:\\s*(?:da|from|de|von)\\s+(.+?)\\s+(?:a|to|à|an)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    /**
     * Verifica se una stringa corrisponde al pattern di un rimborso in una qualsiasi delle 5 lingue supportate.
     */
    public static boolean isPatternRimborso(@Nullable String titolo) {
        if (titolo == null) return false;
        return PATTERN_RIMBORSO.matcher(titolo.trim()).find();
    }

    /**
     * Estrae i nomi di mittente e destinatario da un titolo di rimborso multilingue.
     * Restituisce un array di 2 elementi [mittente, destinatario], oppure null se non è un rimborso valido.
     */
    @Nullable
    public static String[] estraiMittenteEDestinatario(@Nullable String titolo) {
        if (titolo == null) return null;
        Matcher matcher = PATTERN_RIMBORSO.matcher(titolo.trim());
        if (matcher.find()) {
            return new String[]{matcher.group(1), matcher.group(2)};
        }
        return null;
    }

    /**
     * Formatta dinamicamente il titolo di una spesa/rimborso in base alla lingua attiva dell'applicazione.
     * Se la spesa è un rimborso/saldo, estrae i nomi di mittente e destinatario ed applica
     * la risorsa stringa localizzata R.string.titolo_rimborso_formattato.
     */
    @NonNull
    public static String formattaTitoloSpesa(@NonNull Context context, @Nullable Spesa spesa) {
        if (spesa == null || spesa.getTitolo() == null) {
            return "";
        }
        String trimmed = spesa.getTitolo().trim();

        if (CategoriaUtil.isCategoriaSaldi(spesa.getCategoria())) {
            String[] parti = estraiMittenteEDestinatario(trimmed);
            if (parti != null) {
                return context.getString(R.string.titolo_rimborso_formattato, parti[0], parti[1]);
            }
        }
        return trimmed;
    }
}
