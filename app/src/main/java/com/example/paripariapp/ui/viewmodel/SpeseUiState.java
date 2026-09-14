package com.example.paripariapp.ui.viewmodel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.paripariapp.data.model.Scheda;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Rappresentazione immutabile dello stato UI per la schermata delle Schede Spese.
 * Conforme alle linee guida di Google Android Architecture (Single Source of Truth per la UI).
 */
public class SpeseUiState {

    private final boolean isLoading;
    @NonNull
    private final List<Scheda> schede;
    @NonNull
    private final Map<String, Integer> conteggioPartecipanti;
    @Nullable
    private final String errorMessage;

    public SpeseUiState(boolean isLoading,
                        @Nullable List<Scheda> schede,
                        @Nullable Map<String, Integer> conteggioPartecipanti,
                        @Nullable String errorMessage) {
        this.isLoading = isLoading;
        this.schede = (schede != null) ? Collections.unmodifiableList(schede) : Collections.emptyList();
        this.conteggioPartecipanti = (conteggioPartecipanti != null)
                ? Collections.unmodifiableMap(conteggioPartecipanti)
                : Collections.emptyMap();
        this.errorMessage = errorMessage;
    }

    public static SpeseUiState loading() {
        return new SpeseUiState(true, null, null, null);
    }

    public boolean isLoading() {
        return isLoading;
    }

    @NonNull
    public List<Scheda> getSchede() {
        return schede;
    }

    @NonNull
    public Map<String, Integer> getConteggioPartecipanti() {
        return conteggioPartecipanti;
    }

    @Nullable
    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isEmpty() {
        return !isLoading && schede.isEmpty();
    }
}
