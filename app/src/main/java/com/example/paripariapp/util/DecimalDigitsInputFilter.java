package com.example.paripariapp.util;

import android.text.InputFilter;
import android.text.Spanned;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * InputFilter per EditText che impedisce la digitazione di più di N cifre decimali (es. 2 decimali).
 * Gestisce sia il separatore punto (.) che virgola (,).
 */
public class DecimalDigitsInputFilter implements InputFilter {

    private final Pattern pattern;

    public DecimalDigitsInputFilter(int digitsAfterZero) {
        pattern = Pattern.compile("^\\d*([.,]\\d{0," + digitsAfterZero + "})?$");
    }

    @Override
    public CharSequence filter(CharSequence source, int start, int end,
                               Spanned dest, int dstart, int dend) {
        String replacement = source.subSequence(start, end).toString();
        String newVal = dest.subSequence(0, dstart).toString()
                + replacement
                + dest.subSequence(dend, dest.length()).toString();

        // Consenti espressioni matematiche per il calcolo inline (es. 12 + 5, 20 - 4)
        if (CalcolatriceEspressioniUtil.contieneOperatori(newVal) || CalcolatriceEspressioniUtil.contieneOperatori(replacement) || replacement.contains(" ")) {
            return null;
        }

        Matcher matcher = pattern.matcher(newVal);
        if (!matcher.matches()) {
            return "";
        }
        return null;
    }
}
