package com.example.paripariapp.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.SyncStatus;

import java.util.List;

/**
 * Data Access Object per la gestione dei Partecipanti alle schede.
 */
@Dao
public interface PartecipanteDao {

    @Query("SELECT * FROM partecipanti WHERE scheda_id = :schedaId AND sync_status != " + SyncStatus.PENDING_DELETE + " ORDER BY nome ASC")
    LiveData<List<Partecipante>> getPartecipantiBySchedaLive(String schedaId);

    @Query("SELECT * FROM partecipanti WHERE scheda_id = :schedaId AND sync_status != " + SyncStatus.PENDING_DELETE + " ORDER BY nome ASC")
    List<Partecipante> getPartecipantiBySchedaSync(String schedaId);

    @Query("SELECT * FROM partecipanti WHERE id = :id LIMIT 1")
    Partecipante getPartecipanteById(String id);

    @Query("SELECT * FROM partecipanti WHERE sync_status != " + SyncStatus.SYNCED)
    List<Partecipante> getPendingSyncPartecipanti();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Partecipante partecipante);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Partecipante> partecipanti);


    public static class ConteggioPartecipantiTuple {
        public String scheda_id;
        public int count;
    }

    @Query("SELECT scheda_id, COUNT(*) AS count FROM partecipanti GROUP BY scheda_id")
    LiveData<List<ConteggioPartecipantiTuple>> getAllConteggiPartecipanti();

    @Query("SELECT COUNT(*) FROM partecipanti WHERE sync_status != " + SyncStatus.PENDING_DELETE)
    LiveData<Integer> getCountPartecipantiLive();

    @Update
    void update(Partecipante partecipante);

    @Delete
    void delete(Partecipante partecipante);

    @Query("DELETE FROM partecipanti WHERE id = :id")
    void deleteById(String id);

    @Query("DELETE FROM partecipanti WHERE scheda_id = :schedaId")
    void deleteBySchedaId(String schedaId);

    @Query("UPDATE partecipanti SET sync_status = :status WHERE id = :id")
    void updateSyncStatus(String id, int status);
}
