package com.example.paripariapp.data.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.UUID;

/**
 * Entità Room che rappresenta una "Scheda Spese" (Gruppo di spesa condiviso).
 */
@Entity(tableName = "schede")
public class Scheda {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    private String id;

    @NonNull
    @ColumnInfo(name = "titolo")
    private String titolo;

    @ColumnInfo(name = "descrizione")
    private String descrizione;

    @NonNull
    @ColumnInfo(name = "valuta_predefinita")
    private String valutaPredefinita;

    @ColumnInfo(name = "creatore_id")
    private String creatoreId;

    @ColumnInfo(name = "data_creazione")
    private long dataCreazione;

    @ColumnInfo(name = "data_aggiornamento")
    private long dataAggiornamento;

    @ColumnInfo(name = "sync_status")
    private int syncStatus;

    public Scheda(@NonNull String id, @NonNull String titolo, String descrizione,
                  @NonNull String valutaPredefinita, String creatoreId,
                  long dataCreazione, long dataAggiornamento, int syncStatus) {
        this.id = id;
        this.titolo = titolo;
        this.descrizione = descrizione;
        this.valutaPredefinita = valutaPredefinita;
        this.creatoreId = creatoreId;
        this.dataCreazione = dataCreazione;
        this.dataAggiornamento = dataAggiornamento;
        this.syncStatus = syncStatus;
    }

    /** Factory method per creare una nuova scheda con ID autogenerato */
    public static Scheda createNew(@NonNull String titolo, String descrizione,
                                  @NonNull String valutaPredefinita, String creatoreId) {
        long now = System.currentTimeMillis();
        return new Scheda(
                UUID.randomUUID().toString(),
                titolo,
                descrizione,
                valutaPredefinita,
                creatoreId,
                now,
                now,
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
    public String getTitolo() {
        return titolo;
    }

    public void setTitolo(@NonNull String titolo) {
        this.titolo = titolo;
    }

    public String getDescrizione() {
        return descrizione;
    }

    public void setDescrizione(String descrizione) {
        this.descrizione = descrizione;
    }

    @NonNull
    public String getValutaPredefinita() {
        return valutaPredefinita;
    }

    public void setValutaPredefinita(@NonNull String valutaPredefinita) {
        this.valutaPredefinita = valutaPredefinita;
    }

    public String getCreatoreId() {
        return creatoreId;
    }

    public void setCreatoreId(String creatoreId) {
        this.creatoreId = creatoreId;
    }

    public long getDataCreazione() {
        return dataCreazione;
    }

    public void setDataCreazione(long dataCreazione) {
        this.dataCreazione = dataCreazione;
    }

    public long getDataAggiornamento() {
        return dataAggiornamento;
    }

    public void setDataAggiornamento(long dataAggiornamento) {
        this.dataAggiornamento = dataAggiornamento;
    }

    public int getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(int syncStatus) {
        this.syncStatus = syncStatus;
    }
}
