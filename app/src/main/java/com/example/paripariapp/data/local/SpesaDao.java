package com.example.paripariapp.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.data.model.SpesaPartecipante;
import com.example.paripariapp.data.model.SyncStatus;

import java.util.List;

/**
 * Data Access Object per la gestione delle Spese e delle relative quote di divisione.
 */
@Dao
public interface SpesaDao {

    @Query("SELECT * FROM spese WHERE scheda_id = :schedaId AND sync_status != " + SyncStatus.PENDING_DELETE + " ORDER BY data_spesa DESC")
    LiveData<List<Spesa>> getSpeseBySchedaLive(String schedaId);

    @Query("SELECT * FROM spese WHERE scheda_id = :schedaId AND sync_status != " + SyncStatus.PENDING_DELETE + " ORDER BY data_spesa DESC")
    List<Spesa> getSpeseBySchedaSync(String schedaId);

    @Query("SELECT * FROM spese WHERE id = :id LIMIT 1")
    LiveData<Spesa> getSpesaByIdLive(String id);

    @Query("SELECT * FROM spese WHERE id = :id LIMIT 1")
    Spesa getSpesaById(String id);

    @Query("SELECT * FROM spese WHERE sync_status != " + SyncStatus.SYNCED)
    List<Spesa> getPendingSyncSpese();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Spesa spesa);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Spesa> spese);

    @Update
    void update(Spesa spesa);

    @Delete
    void delete(Spesa spesa);

    @Query("DELETE FROM spese WHERE id = :id")
    void deleteById(String id);

    @Query("UPDATE spese SET sync_status = :status WHERE id = :id")
    void updateSyncStatus(String id, int status);

    // Gestione quote
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertQuote(List<SpesaPartecipante> quote);

    @Query("SELECT * FROM spese_partecipanti WHERE spesa_id = :spesaId")
    List<SpesaPartecipante> getQuoteBySpesa(String spesaId);

    @Query("DELETE FROM spese_partecipanti WHERE spesa_id = :spesaId")
    void deleteQuoteBySpesa(String spesaId);

    // Statistiche aggregate
    @Query("SELECT COUNT(*) FROM spese WHERE sync_status != " + SyncStatus.PENDING_DELETE)
    LiveData<Integer> getCountSpeseLive();

    @Query("SELECT COALESCE(SUM(importo), 0.0) FROM spese WHERE scheda_id = :schedaId AND sync_status != " + SyncStatus.PENDING_DELETE)
    LiveData<Double> getTotaleSpeseBySchedaLive(String schedaId);
}
