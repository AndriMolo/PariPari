package com.example.paripariapp.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.paripariapp.data.model.Scheda;
import com.example.paripariapp.data.model.SyncStatus;

import java.util.List;

@Dao
public interface SchedaDao {

    @Query("SELECT * FROM schede WHERE sync_status != " + SyncStatus.PENDING_DELETE + " ORDER BY data_aggiornamento DESC")
    LiveData<List<Scheda>> getAllSchedeLive();

    @Query("SELECT * FROM schede WHERE sync_status != " + SyncStatus.PENDING_DELETE + " ORDER BY data_aggiornamento DESC")
    List<Scheda> getAllSchedeSync();

    @Query("SELECT * FROM schede WHERE id = :id LIMIT 1")
    LiveData<Scheda> getSchedaByIdLive(String id);

    @Query("SELECT * FROM schede WHERE id = :id LIMIT 1")
    Scheda getSchedaById(String id);

    @Query("SELECT * FROM schede WHERE sync_status != " + SyncStatus.SYNCED)
    List<Scheda> getPendingSyncSchede();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Scheda scheda);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Scheda> schede);

    @Update
    void update(Scheda scheda);

    @Delete
    void delete(Scheda scheda);

    @Query("DELETE FROM schede WHERE id = :id")
    void deleteById(String id);

    @Query("UPDATE schede SET sync_status = :status WHERE id = :id")
    void updateSyncStatus(String id, int status);

    @Query("SELECT COUNT(*) FROM schede WHERE sync_status != " + SyncStatus.PENDING_DELETE)
    LiveData<Integer> getCountSchedeLive();

    @Query("UPDATE schede SET titolo = :nuovoTitolo, data_aggiornamento = :dataAggiornamento, sync_status = :syncStatus WHERE id = :schedaId")
    void updateTitolo(String schedaId, String nuovoTitolo, long dataAggiornamento, int syncStatus);

    @Query("SELECT * FROM schede WHERE codice_invito = :codice LIMIT 1")
    Scheda getSchedaByCodiceInvito(String codice);

    @Query("UPDATE schede SET codice_invito = :codice WHERE id = :schedaId")
    void updateCodiceInvito(String schedaId, String codice);
}