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
            Matcher matcher = PATTERN_RIMBORSO.matcher(trimmed);
            if (matcher.find()) {
                String da = matcher.group(1);
                String a = matcher.group(2);
                return context.getString(R.string.titolo_rimborso_formattato, da, a);
            }
        }
        return trimmed;
    }
}
