package com.example.paripariapp.data.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.UUID;

/**
 * Entità Room che rappresenta una spesa registrata all'interno di una scheda.
 */
@Entity(
        tableName = "spese",
        indices = {
                @Index("scheda_id"),
                @Index("pagato_da_id")
        }
)
public class Spesa {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    private String id;

    @NonNull
    @ColumnInfo(name = "scheda_id")
    private String schedaId;

    @NonNull
    @ColumnInfo(name = "titolo")
    private String titolo;

    @ColumnInfo(name = "importo")
    private double importo;

    @NonNull
    @ColumnInfo(name = "valuta")
    private String valuta;

    @ColumnInfo(name = "data_spesa")
    private long dataSpesa;

    @ColumnInfo(name = "categoria")
    private String categoria;

    @NonNull
    @ColumnInfo(name = "pagato_da_id")
    private String pagatoDaId;

    @ColumnInfo(name = "scontrino_url")
    private String scontrinoUrl;

    @ColumnInfo(name = "sync_status")
    private int syncStatus;

    public Spesa(@NonNull String id, @NonNull String schedaId, @NonNull String titolo,
                 double importo, @NonNull String valuta, long dataSpesa,
                 String categoria, @NonNull String pagatoDaId,
                 String scontrinoUrl, int syncStatus) {
        this.id = id;
        this.schedaId = schedaId;
        this.titolo = titolo;
        this.importo = importo;
        this.valuta = valuta;
        this.dataSpesa = dataSpesa;
        this.categoria = categoria;
        this.pagatoDaId = pagatoDaId;
        this.scontrinoUrl = scontrinoUrl;
        this.syncStatus = syncStatus;
    }

    /** Factory method per creare una nuova spesa */
    public static Spesa createNew(@NonNull String schedaId, @NonNull String titolo,
                                 double importo, @NonNull String valuta,
                                 String categoria, @NonNull String pagatoDaId,
                                 String scontrinoUrl) {
        return new Spesa(
                UUID.randomUUID().toString(),
                schedaId,
                titolo,
                importo,
                valuta,
                System.currentTimeMillis(),
                categoria,
                pagatoDaId,
                scontrinoUrl,
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
    public String getTitolo() {
        return titolo;
    }

    public void setTitolo(@NonNull String titolo) {
        this.titolo = titolo;
    }

    public double getImporto() {
        return importo;
    }

    public void setImporto(double importo) {
        this.importo = importo;
    }

    @NonNull
    public String getValuta() {
        return valuta;
    }

    public void setValuta(@NonNull String valuta) {
        this.valuta = valuta;
    }

    public long getDataSpesa() {
        return dataSpesa;
    }

    public void setDataSpesa(long dataSpesa) {
        this.dataSpesa = dataSpesa;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    @NonNull
    public String getPagatoDaId() {
        return pagatoDaId;
    }

    public void setPagatoDaId(@NonNull String pagatoDaId) {
        this.pagatoDaId = pagatoDaId;
    }

    public String getScontrinoUrl() {
        return scontrinoUrl;
    }

    public void setScontrinoUrl(String scontrinoUrl) {
        this.scontrinoUrl = scontrinoUrl;
    }

    public int getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(int syncStatus) {
        this.syncStatus = syncStatus;
    }
}
