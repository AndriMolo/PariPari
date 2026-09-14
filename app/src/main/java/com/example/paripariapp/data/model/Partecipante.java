package com.example.paripariapp.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

    public static final String STATO_ATTIVO = "ATTIVO";
    public static final String STATO_USCITO = "USCITO";
    public static final String STATO_OSPITE = "OSPITE";

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

    @ColumnInfo(name = "user_id")
    private String userId;

    @ColumnInfo(name = "previous_user_id")
    private String previousUserId;

    @NonNull
    @ColumnInfo(name = "stato", defaultValue = STATO_ATTIVO)
    private String stato = STATO_ATTIVO;

    @NonNull
    public static String pulisciNome(@Nullable String nome) {
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
        this.stato = STATO_ATTIVO;
    }

    public Partecipante(@NonNull String id, @NonNull String schedaId, @NonNull String nome,
                        String email, int syncStatus, String paypalHandle, String revolutHandle,
                        String userId, String previousUserId, String stato) {
        this.id = id;
        this.schedaId = schedaId;
        this.nome = pulisciNome(nome);
        this.email = email;
        this.syncStatus = syncStatus;
        this.paypalHandle = paypalHandle;
        this.revolutHandle = revolutHandle;
        this.userId = userId;
        this.previousUserId = previousUserId;
        this.stato = stato != null ? stato : STATO_ATTIVO;
    }

    /** Factory method per creare un nuovo partecipante */
    public static Partecipante createNew(@NonNull String schedaId, @NonNull String nome, String email) {
        return new Partecipante(
                UUID.randomUUID().toString(),
                schedaId,
                pulisciNome(nome),
                email,
                SyncStatus.PENDING_INSERT,
                null,
                null,
                null,
                null,
                STATO_ATTIVO
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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPreviousUserId() {
        return previousUserId;
    }

    public void setPreviousUserId(String previousUserId) {
        this.previousUserId = previousUserId;
    }

    @NonNull
    public String getStato() {
        return stato;
    }

    public void setStato(@NonNull String stato) {
        this.stato = stato;
    }

    public boolean isAttivo() {
        return !STATO_USCITO.equalsIgnoreCase(stato);
    }

    public boolean isAutenticato() {
        return userId != null && !userId.trim().isEmpty();
    }

    /**
     * Riconosce se un Partecipante corrisponde all'utente corrente autenticato.
     * Basato esclusivamente su UID (userId) o ID, senza alcun controllo email.
     */
    public static boolean isCurrentUserParticipant(Partecipante p, @Nullable com.google.firebase.auth.FirebaseUser currentUser) {
        if (p == null || currentUser == null) return false;
        String currentUid = currentUser.getUid();

        if (p.getUserId() != null && p.getUserId().equals(currentUid)) {
            return true;
        }

        if (p.getId().equals(currentUid)) {
            return true;
        }

        // 3. Corrispondenza per Display Name
        if (currentUser.getDisplayName() != null && !currentUser.getDisplayName().trim().isEmpty()) {
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

    @Nullable
    public static String findCurrentUserId(java.util.List<Partecipante> partecipanti,
                                           @Nullable com.google.firebase.auth.FirebaseUser currentUser) {
        return findCurrentUserId(partecipanti, currentUser, null, null);
    }

    @Nullable
    public static String findCurrentUserId(@Nullable java.util.List<Partecipante> partecipanti,
                                           @Nullable com.google.firebase.auth.FirebaseUser currentUser,
                                           @Nullable com.example.paripariapp.data.repository.UserPreferencesRepository prefs,
                                           @Nullable String schedaId) {
        if (partecipanti == null || partecipanti.isEmpty() || currentUser == null) return null;

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

        for (Partecipante p : partecipanti) {
            if (isCurrentUserParticipant(p, currentUser)) {
                return p.getId();
            }
        }

        return null;
    }

    public static Partecipante trovaProprietario(
            @Nullable Scheda scheda,
            @Nullable java.util.List<Partecipante> partecipanti,
            @Nullable com.google.firebase.auth.FirebaseUser currentUser
    ) {
        if (scheda == null || partecipanti == null || partecipanti.isEmpty()) return null;

        String creatoreId = scheda.getCreatoreId();
        if (creatoreId != null && !creatoreId.isEmpty()) {
            for (Partecipante p : partecipanti) {
                if (p.getId().equals(creatoreId)) {
                    return p;
                }
            }
        }

        if (currentUser != null) {
            for (Partecipante p : partecipanti) {
                if (isCurrentUserParticipant(p, currentUser)) {
                    return p;
                }
            }
        }

        return partecipanti.get(0);
    }

    @NonNull
    public static java.util.List<Partecipante> ordinaConProprietarioInCima(
            @Nullable Scheda scheda,
            @NonNull java.util.List<Partecipante> partecipanti,
            @Nullable com.google.firebase.auth.FirebaseUser currentUser
    ) {
        java.util.List<Partecipante> ordinati = new java.util.ArrayList<>();
        Partecipante proprietario = trovaProprietario(scheda, partecipanti, currentUser);

        if (proprietario != null) {
            ordinati.add(proprietario);
            for (Partecipante p : partecipanti) {
                if (!p.getId().equals(proprietario.getId())) {
                    ordinati.add(p);
                }
            }
        } else {
            ordinati.addAll(partecipanti);
        }

        return ordinati;
    }
}
