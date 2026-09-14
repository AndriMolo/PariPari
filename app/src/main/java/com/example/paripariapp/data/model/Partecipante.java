package com.example.paripariapp.data.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.Locale;
import java.util.UUID;

/**
 * Entità Room che rappresenta un partecipante a una specifica scheda.
 */
@Entity(
        tableName = "partecipanti",
        indices = {@Index("scheda_id")}
)


public class Partecipante {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    private String id;

    @NonNull
    @ColumnInfo(name = "scheda_id")
    private String schedaId;

    @NonNull
    @ColumnInfo(name = "nome")
    private String nome;

    @ColumnInfo(name = "email")
    private String email;

    @ColumnInfo(name = "sync_status")
    private int syncStatus;

    @ColumnInfo(name = "paypal_handle")
    private String paypalHandle;

    @ColumnInfo(name = "revolut_handle")
    private String revolutHandle;

    @NonNull
    public static String pulisciNome(@androidx.annotation.Nullable String nome) {
        if (nome == null) return "";
        String pulito = nome.trim();
        String pulitoLower = pulito.toLowerCase(Locale.ROOT);
        while (pulitoLower.endsWith("(io)") || pulitoLower.endsWith("(me)")) {
            if (pulitoLower.endsWith("(io)")) {
                pulito = pulito.substring(0, pulito.length() - 4).trim();
            } else if (pulitoLower.endsWith("(me)")) {
                pulito = pulito.substring(0, pulito.length() - 4).trim();
            }
            pulitoLower = pulito.toLowerCase(Locale.ROOT);
        }
        return pulito;
    }

    /**
     * Formatta il nome del partecipante per la visualizzazione nella UI:
     * solo il proprietario dell'account corrente vede l'etichetta localizzata (io) / (me) di fianco al proprio nome.
     */
    @NonNull
    public static String formattaNomePerVisualizzazione(@NonNull android.content.Context context,
                                                       @NonNull Partecipante p,
                                                       boolean isMe) {
        String pulito = pulisciNome(p.getNome());
        if (isMe) {
            String etichettaIo = context.getString(com.example.paripariapp.R.string.etichetta_io);
            return context.getString(com.example.paripariapp.R.string.formato_nome_con_io, pulito, etichettaIo);
        }
        return pulito;
    }

    @Ignore
    public Partecipante(@NonNull String id, @NonNull String schedaId, @NonNull String nome,
                        String email, int syncStatus) {
        this.id = id;
        this.schedaId = schedaId;
        this.nome = pulisciNome(nome);
        this.email = email;
        this.syncStatus = syncStatus;
    }

    public Partecipante(@NonNull String id, @NonNull String schedaId, @NonNull String nome,
                        String email, int syncStatus, String paypalHandle, String revolutHandle) {
        this.id = id;
        this.schedaId = schedaId;
        this.nome = pulisciNome(nome);
        this.email = email;
        this.syncStatus = syncStatus;
        this.paypalHandle = paypalHandle;
        this.revolutHandle = revolutHandle;
    }

    /** Factory method per creare un nuovo partecipante */
    public static Partecipante createNew(@NonNull String schedaId, @NonNull String nome, String email) {
        return new Partecipante(
                UUID.randomUUID().toString(),
                schedaId,
                pulisciNome(nome),
                email,
                SyncStatus.PENDING_INSERT
        );
    }

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    @NonNull
    public String getSchedaId() {
        return schedaId;
    }

    public void setSchedaId(@NonNull String schedaId) {
        this.schedaId = schedaId;
    }

    @NonNull
    public String getNome() {
        return pulisciNome(nome);
    }

    public void setNome(@NonNull String nome) {
        this.nome = pulisciNome(nome);
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public int getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(int syncStatus) {
        this.syncStatus = syncStatus;
    }

    public String getPaypalHandle() {
        return paypalHandle;
    }

    public void setPaypalHandle(String paypalHandle) {
        this.paypalHandle = paypalHandle;
    }

    public String getRevolutHandle() {
        return revolutHandle;
    }

    public void setRevolutHandle(String revolutHandle) {
        this.revolutHandle = revolutHandle;
    }

    /**
     * Riconosce se un Partecipante corrisponde all'utente corrente autenticato.
     */
    public static boolean isCurrentUserParticipant(Partecipante p, @androidx.annotation.Nullable com.google.firebase.auth.FirebaseUser currentUser) {
        if (p == null) return false;

        // 1. Corrispondenza per Email
        if (currentUser != null && currentUser.getEmail() != null && !currentUser.getEmail().trim().isEmpty()) {
            if (p.getEmail() != null && p.getEmail().trim().equalsIgnoreCase(currentUser.getEmail().trim())) {
                return true;
            }
        }

        // 2. Corrispondenza per ID / UID
        if (currentUser != null && p.getId().equals(currentUser.getUid())) {
            return true;
        }

        // 3. Corrispondenza per Display Name
        if (currentUser != null && currentUser.getDisplayName() != null && !currentUser.getDisplayName().trim().isEmpty()) {
            String displayName = currentUser.getDisplayName().trim().toLowerCase(Locale.ROOT);
            String pNome = pulisciNome(p.getNome()).toLowerCase(Locale.ROOT);
            if (pNome.equalsIgnoreCase(displayName)) {
                return true;
            }
        }

        // 4. Se il partecipante ha nome letterale "io" o "me" (fallback per gruppi offline locali)
        String n = pulisciNome(p.getNome()).toLowerCase(Locale.ROOT);
        if (n.equals("io") || n.equals("me")) {
            return true;
        }

        return false;
    }

    /**
     * Trova l'ID del partecipante che rappresenta l'utente corrente in una lista di partecipanti.
     */
    @androidx.annotation.Nullable
    public static String findCurrentUserId(java.util.List<Partecipante> partecipanti,
                                           @androidx.annotation.Nullable com.google.firebase.auth.FirebaseUser currentUser) {
        return findCurrentUserId(partecipanti, currentUser, null, null);
    }

    /**
     * Trova l'ID del partecipante associato all'account/dispositivo corrente considerando le preferenze locali.
     */
    @androidx.annotation.Nullable
    public static String findCurrentUserId(@androidx.annotation.Nullable java.util.List<Partecipante> partecipanti,
                                           @androidx.annotation.Nullable com.google.firebase.auth.FirebaseUser currentUser,
                                           @androidx.annotation.Nullable com.example.paripariapp.data.repository.UserPreferencesRepository prefs,
                                           @androidx.annotation.Nullable String schedaId) {
        if (partecipanti == null || partecipanti.isEmpty()) return null;

        // 1. Controllo preferenze memorizzate su questo dispositivo per questa scheda
        if (prefs != null && schedaId != null) {
            String savedId = prefs.getMyParticipantId(schedaId);
            if (savedId != null) {
                for (Partecipante p : partecipanti) {
                    if (p.getId().equals(savedId)) {
                        return p.getId();
                    }
                }
            }
        }

        // 2. Controllo per utente autenticato (email, UID, displayName)
        for (Partecipante p : partecipanti) {
            if (isCurrentUserParticipant(p, currentUser)) {
                return p.getId();
            }
        }

        return null;
    }

    /**
     * Identifica il partecipante proprietario/capogruppo della scheda.
     */
    public static Partecipante trovaProprietario(
            @androidx.annotation.Nullable Scheda scheda,
            @androidx.annotation.Nullable java.util.List<Partecipante> partecipanti,
            @androidx.annotation.Nullable com.google.firebase.auth.FirebaseUser currentUser) {
        if (partecipanti == null || partecipanti.isEmpty()) return null;

        String creatoreId = (scheda != null) ? scheda.getCreatoreId() : null;

        if (creatoreId != null && !creatoreId.trim().isEmpty()) {
            // 1. Corrispondenza diretta per ID Partecipante
            for (Partecipante p : partecipanti) {
                if (p.getId().equals(creatoreId)) {
                    return p;
                }
            }

            // 2. Corrispondenza se creatoreId corrisponde all'UID dell'utente autenticato
            if (currentUser != null && creatoreId.equals(currentUser.getUid())) {
                for (Partecipante p : partecipanti) {
                    if (isCurrentUserParticipant(p, currentUser)) {
                        return p;
                    }
                }
            }
        }

        // 3. Fallback: primo partecipante della scheda
        return partecipanti.get(0);
    }

    /**
     * Riordina la lista posizionando il proprietario della scheda sempre al primo posto (in cima).
     */
    public static java.util.List<Partecipante> ordinaConProprietarioInCima(
            @androidx.annotation.Nullable Scheda scheda,
            @androidx.annotation.Nullable java.util.List<Partecipante> partecipanti,
            @androidx.annotation.Nullable com.google.firebase.auth.FirebaseUser currentUser) {
        if (partecipanti == null || partecipanti.size() <= 1) {
            return partecipanti != null ? new java.util.ArrayList<>(partecipanti) : new java.util.ArrayList<>();
        }

        Partecipante proprietario = trovaProprietario(scheda, partecipanti, currentUser);
        if (proprietario == null) {
            return new java.util.ArrayList<>(partecipanti);
        }

        java.util.List<Partecipante> ordinati = new java.util.ArrayList<>();
        ordinati.add(proprietario);

        for (Partecipante p : partecipanti) {
            if (!p.getId().equals(proprietario.getId())) {
                ordinati.add(p);
            }
        }

        return ordinati;
    }
}
