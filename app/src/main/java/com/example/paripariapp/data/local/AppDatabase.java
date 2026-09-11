    package com.example.paripariapp.data.local;

    import android.content.Context;

    import androidx.room.Database;
    import androidx.room.Room;
    import androidx.room.RoomDatabase;

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
            version = 4,
            exportSchema = false
    )
    public abstract class AppDatabase extends RoomDatabase {

        private static final String DATABASE_NAME = "paripari_database";
        private static volatile AppDatabase INSTANCE;

        private static final int NUMBER_OF_THREADS = 4;
        public static final ExecutorService databaseWriteExecutor =
                Executors.newFixedThreadPool(NUMBER_OF_THREADS);

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
                                .fallbackToDestructiveMigration()
                                .build();
                    }
                }
            }
            return INSTANCE;
        }
    }