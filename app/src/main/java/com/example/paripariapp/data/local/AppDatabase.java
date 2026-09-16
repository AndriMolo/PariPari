package com.example.paripariapp.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.Scheda;
import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.data.model.SpesaPartecipante;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Database principale Room di PariPari.
 * Fornisce accesso thread-safe a tutte le tabelle locali e gestisce il thread pool per le operazioni I/O.
 */
@Database(
        entities = {
                Scheda.class,
                Partecipante.class,
                Spesa.class,
                SpesaPartecipante.class
        },
        version = 10,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "paripari_database";
    private static volatile AppDatabase INSTANCE;

    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE spese_partecipanti ADD COLUMN quota_pagata REAL NOT NULL DEFAULT 0.0");
        }
    };

    public static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE schede ADD COLUMN codice_invito TEXT");
        }
    };

    public static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE partecipanti ADD COLUMN paypal_handle TEXT");
            database.execSQL("ALTER TABLE partecipanti ADD COLUMN revolut_handle TEXT");
        }
    };

    public static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE partecipanti ADD COLUMN user_id TEXT");
            database.execSQL("ALTER TABLE partecipanti ADD COLUMN previous_user_id TEXT");
            database.execSQL("ALTER TABLE partecipanti ADD COLUMN stato TEXT NOT NULL DEFAULT 'ATTIVO'");
        }
    };

    public static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE schede ADD COLUMN icona_url TEXT");
            database.execSQL("ALTER TABLE partecipanti ADD COLUMN photo_url TEXT");
        }
    };

    public static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE spese ADD COLUMN scontrino_json TEXT");
        }
    };

    public abstract SchedaDao schedaDao();
    public abstract PartecipanteDao partecipanteDao();
    public abstract SpesaDao spesaDao();

    /**
     * Restituisce l'istanza singleton del database Room.
     */
    public static AppDatabase getInstance(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    DATABASE_NAME
                            )
                            .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
