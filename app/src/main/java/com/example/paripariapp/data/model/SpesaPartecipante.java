package com.example.paripariapp.data.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;

/**
 * Tabella ponte Room per la divisione delle quote di una spesa tra i partecipanti.
 */
@Entity(
        tableName = "spese_partecipanti",
        primaryKeys = {"spesa_id", "partecipante_id"},
        indices = {
                @Index("spesa_id"),
                @Index("partecipante_id")
        }
)
public class SpesaPartecipante {

    @NonNull
    @ColumnInfo(name = "spesa_id")
    private String spesaId;

    @NonNull
    @ColumnInfo(name = "partecipante_id")
    private String partecipanteId;

    @ColumnInfo(name = "quota")
    private double quota;

    @ColumnInfo(name = "sync_status")
    private int syncStatus;

    public SpesaPartecipante(@NonNull String spesaId, @NonNull String partecipanteId,
                             double quota, int syncStatus) {
        this.spesaId = spesaId;
        this.partecipanteId = partecipanteId;
        this.quota = quota;
        this.syncStatus = syncStatus;
    }

    @NonNull
    public String getSpesaId() {
        return spesaId;
    }

    public void setSpesaId(@NonNull String spesaId) {
        this.spesaId = spesaId;
    }

    @NonNull
    public String getPartecipanteId() {
        return partecipanteId;
    }

    public void setPartecipanteId(@NonNull String partecipanteId) {
        this.partecipanteId = partecipanteId;
    }

    public double getQuota() {
        return quota;
    }

    public void setQuota(double quota) {
        this.quota = quota;
    }

    public int getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(int syncStatus) {
        this.syncStatus = syncStatus;
    }
}