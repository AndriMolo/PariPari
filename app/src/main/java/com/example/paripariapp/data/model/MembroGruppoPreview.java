package com.example.paripariapp.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;

/**
 * Modello leggero per rappresentare un partecipante nell'anteprima di accesso a una scheda.
 * Distingue chiaramente tra membri già autenticati (non sostituibili)
 * e membri anonimi/offline (disponibili per il subentro).
 */
public class MembroGruppoPreview implements Serializable {

    @NonNull
    private final String id;
    @NonNull
    private final String schedaId;
    @NonNull
    private final String nome;
    @Nullable
    private final String email;
    @Nullable
    private final String userId;
    private final boolean isAutenticato;

    public MembroGruppoPreview(@NonNull String id,
                               @NonNull String schedaId,
                               @NonNull String nome,
                               @Nullable String email,
                               @Nullable String userId,
                               boolean isAutenticato) {
        this.id = id;
        this.schedaId = schedaId;
        this.nome = nome;
        this.email = email;
        this.userId = userId;
        this.isAutenticato = isAutenticato;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getSchedaId() {
        return schedaId;
    }

    @NonNull
    public String getNome() {
        return nome;
    }

    @Nullable
    public String getEmail() {
        return email;
    }

    @Nullable
    public String getUserId() {
        return userId;
    }

    public boolean isAutenticato() {
        return isAutenticato;
    }

    /**
     * Un membro è sostituibile (claimable) se non è associato ad un account autenticato.
     */
    public boolean isSostituibile() {
        return !isAutenticato;
    }
}
