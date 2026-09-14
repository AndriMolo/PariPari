package com.example.paripariapp.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;

/**
 * Modello leggero per rappresentare un partecipante nell'anteprima di accesso a una scheda.
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
    @Nullable
    private final String previousUserId;
    @NonNull
    private final String stato;
    private final boolean isAutenticato;

    public MembroGruppoPreview(@NonNull String id,
                               @NonNull String schedaId,
                               @NonNull String nome,
                               @Nullable String email,
                               @Nullable String userId,
                               @Nullable String previousUserId,
                               @Nullable String stato,
                               boolean isAutenticato) {
        this.id = id;
        this.schedaId = schedaId;
        this.nome = nome;
        this.email = email;
        this.userId = userId;
        this.previousUserId = previousUserId;
        this.stato = stato != null ? stato : Partecipante.STATO_ATTIVO;
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

    @Nullable
    public String getPreviousUserId() {
        return previousUserId;
    }

    @NonNull
    public String getStato() {
        return stato;
    }

    public boolean isAutenticato() {
        return isAutenticato;
    }

    /**
     * Un membro è sostituibile (claimable) se non è associato ad un account autenticato o se è uscito.
     */
    public boolean isSostituibile() {
        return userId == null || userId.trim().isEmpty() || Partecipante.STATO_USCITO.equalsIgnoreCase(stato);
    }

    /**
     * Verifica se questo profilo corrisponde all'utente corrente tramite match rigoroso ed esclusivo su userId o previousUserId.
     */
    public boolean isMyProfile(@Nullable String currentUid) {
        if (currentUid == null || currentUid.trim().isEmpty()) return false;
        return currentUid.equals(userId) || currentUid.equals(previousUserId);
    }

    /**
     * Verifica se questo profilo corrisponde al precedente profilo dell'utente corrente per il re-link automatico.
     */
    public boolean isMyPreviousProfile(@Nullable String currentUid) {
        return currentUid != null && currentUid.equals(previousUserId);
    }

    /**
     * Determina se l'utente può selezionare questo membro: o è libero/sostituibile, oppure appartiene già all'utente stesso.
     */
    public boolean isSelezionabileDa(@Nullable String currentUid) {
        return isSostituibile() || isMyProfile(currentUid);
    }
}
