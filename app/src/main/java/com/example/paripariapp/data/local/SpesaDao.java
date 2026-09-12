package com.example.paripariapp.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.data.model.SpesaConDettagli;
import com.example.paripariapp.data.model.SpesaPartecipante;
import com.example.paripariapp.data.model.SyncStatus;

import java.util.List;

@Dao
public interface SpesaDao {

    @Query("SELECT * FROM spese WHERE scheda_id = :schedaId AND sync_status != " + SyncStatus.PENDING_DELETE + " ORDER BY data_spesa DESC")
    LiveData<List<Spesa>> getSpeseBySchedaLive(String schedaId);

    @Query("SELECT * FROM spese WHERE scheda_id = :schedaId AND sync_status != " + SyncStatus.PENDING_DELETE + " ORDER BY data_spesa DESC")
    List<Spesa> getSpeseBySchedaSync(String schedaId);

    @Query("SELECT * FROM spese_partecipanti WHERE spesa_id = :spesaId")
    LiveData<List<SpesaPartecipante>> getQuoteBySpesaLive(String spesaId);

    @Query("SELECT * FROM spese_partecipanti WHERE spesa_id = :spesaId")
    List<SpesaPartecipante> getQuoteBySpesaSync(String spesaId);

    @Query("SELECT * FROM spese WHERE sync_status != " + SyncStatus.SYNCED)
    List<Spesa> getPendingSyncSpese();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Spesa spesa);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertQuote(List<SpesaPartecipante> quote);

    @Query("DELETE FROM spese WHERE id = :spesaId")
    void deleteById(String spesaId);

    @Query("DELETE FROM spese WHERE scheda_id = :schedaId")
    void deleteBySchedaId(String schedaId);

    @Query("DELETE FROM spese_partecipanti WHERE spesa_id = :spesaId")
    void deleteQuoteBySpesaId(String spesaId);

    @Query("DELETE FROM spese_partecipanti WHERE spesa_id IN (SELECT id FROM spese WHERE scheda_id = :schedaId)")
    void deleteQuoteBySchedaId(String schedaId);

    @Query("UPDATE spese SET sync_status = :status WHERE id = :id")
    void updateSyncStatus(String id, int status);

    @Query("SELECT COUNT(*) FROM spese WHERE sync_status != " + SyncStatus.PENDING_DELETE)
    LiveData<Integer> getCountSpeseLive();

    @Query("SELECT SUM(importo) FROM spese WHERE scheda_id = :schedaId AND sync_status != " + SyncStatus.PENDING_DELETE)
    LiveData<Double> getTotaleSpeseBySchedaLive(String schedaId);

    @Query("SELECT * FROM spese WHERE id = :id LIMIT 1")
    Spesa getSpesaByIdSync(String id);

    @Query("SELECT * FROM spese WHERE id = :id LIMIT 1")
    LiveData<Spesa> getSpesaByIdLive(String id);

    @Query("SELECT s.*, " +
            "COALESCE(p.nome, '') AS nomePagatore, " +
            "(SELECT COUNT(*) FROM spese_partecipanti sp WHERE sp.spesa_id = s.id AND sp.quota > 0) AS numeroPartecipanti " +
            "FROM spese s " +
            "LEFT JOIN partecipanti p ON s.pagato_da_id = p.id " +
            "WHERE s.scheda_id = :schedaId AND s.sync_status != " + SyncStatus.PENDING_DELETE + " " +
            "ORDER BY s.data_spesa DESC")
    LiveData<List<SpesaConDettagli>> getSpeseConDettagliBySchedaLive(String schedaId);

    @Query("SELECT q.* FROM spese_partecipanti q INNER JOIN spese s ON q.spesa_id = s.id WHERE s.scheda_id = :schedaId")
    LiveData<List<SpesaPartecipante>> getTutteQuoteBySchedaLive(String schedaId);

    @Query("SELECT q.* FROM spese_partecipanti q INNER JOIN spese s ON q.spesa_id = s.id WHERE s.scheda_id = :schedaId")
    List<SpesaPartecipante> getTutteQuoteBySchedaSync(String schedaId);
}