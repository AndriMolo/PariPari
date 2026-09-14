package com.example.paripariapp.util;

import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.Nullable;

/**
 * Utility per la gestione morbida della tastiera software (IME)
 * secondo le best practice Android.
 */
public final class KeyboardUtil {

    private KeyboardUtil() {}

    /**
     * Richiede il focus e visualizza automaticamente la tastiera software.
     */
    public static void showKeyboard(@Nullable View view) {
        if (view == null) return;
        view.requestFocus();
        view.post(() -> {
            if (view.getContext() != null) {
                InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(view, 0);
                }
            }
        });
    }

    /**
     * Nasconde la tastiera software.
     */
    public static void hideKeyboard(@Nullable View view) {
        if (view == null) return;
        if (view.getContext() != null) {
            InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
        view.clearFocus();
    }
}
