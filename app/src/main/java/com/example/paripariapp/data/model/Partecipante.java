package com.example.paripariapp.data.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

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

    public Partecipante(@NonNull String id, @NonNull String schedaId, @NonNull String nome,
                        String email, int syncStatus) {
        this.id = id;
        this.schedaId = schedaId;
        this.nome = nome;
        this.email = email;
        this.syncStatus = syncStatus;
    }

    /** Factory method per creare un nuovo partecipante */
    public static Partecipante createNew(@NonNull String schedaId, @NonNull String nome, String email) {
        return new Partecipante(
                UUID.randomUUID().toString(),
                schedaId,
                nome,
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
        return nome;
    }

    public void setNome(@NonNull String nome) {
        this.nome = nome;
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
}
