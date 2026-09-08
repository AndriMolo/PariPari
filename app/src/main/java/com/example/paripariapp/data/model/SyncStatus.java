package com.example.paripariapp.data.model;

/**
 * Costanti per tracciare lo stato di sincronizzazione dei record
 * tra il database locale (Room) e il database remoto (Firestore).
 */
public class SyncStatus {
    /** Il dato in locale è perfettamente allineato con Firestore */
    public static final int SYNCED = 0;

    /** Il dato è stato creato in locale (offline) e deve essere caricato su Firestore */
    public static final int PENDING_INSERT = 1;

    /** Il dato è stato modificato in locale e deve essere aggiornato su Firestore */
    public static final int PENDING_UPDATE = 2;

    /** Il dato è stato contrassegnato per l'eliminazione e deve essere rimosso da Firestore */
    public static final int PENDING_DELETE = 3;

    private SyncStatus() {
        // Non istanziabile
    }
}
