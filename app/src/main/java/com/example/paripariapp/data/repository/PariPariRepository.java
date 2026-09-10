package com.example.paripariapp.data.repository;

import android.app.Application;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.example.paripariapp.data.local.AppDatabase;
import com.example.paripariapp.data.local.PartecipanteDao;
import com.example.paripariapp.data.local.SchedaDao;
import com.example.paripariapp.data.local.SpesaDao;
import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.Scheda;
import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.data.model.SpesaPartecipante;
import com.example.paripariapp.data.model.SyncStatus;
import com.example.paripariapp.util.NetworkConnectivityMonitor;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repository centrale dell'applicazione PariPari (Single Source of Truth).
 * Coordina il database locale Room e la sincronizzazione bidirezionale con Cloud Firestore:
 * - Letture immediate e continue dal DB locale (Room) via LiveData
 * - Scritture locali istantanee con flag di sincronizzazione (Offline-First)
 * - Push asincrono dei dati pendenti verso Firestore quando connesso e autenticato
 * - Download in tempo reale dei dati remoti da Firestore verso Room via snapshot listeners
 */
public class PariPariRepository {

    private static final String TAG = "PariPariRepository";

    private static volatile PariPariRepository INSTANCE;

    private final SchedaDao schedaDao;
    private final PartecipanteDao partecipanteDao;
    private final SpesaDao spesaDao;
    private final FirebaseFirestore firestore;
    private final FirebaseAuth auth;
    private final NetworkConnectivityMonitor networkMonitor;

    private final List<ListenerRegistration> activeListeners = new ArrayList<>();
    private final Map<String, ListenerRegistration> groupSubListeners = new ConcurrentHashMap<>();

    private PariPariRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        schedaDao = db.schedaDao();
        partecipanteDao = db.partecipanteDao();
        spesaDao = db.spesaDao();
        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // Monitor di rete: quando la connessione torna attiva, avvia il sync se autenticato
        networkMonitor = new NetworkConnectivityMonitor(application, new NetworkConnectivityMonitor.OnNetworkChangeListener() {
            @Override
            public void onNetworkAvailable() {
                Log.i(TAG, "Rete attiva rilevata");
                if (auth.getCurrentUser() != null) {
                    syncPendingData();
                    startRealtimeSync();
                } else {
                    Log.d(TAG, "Rete attiva, ma nessun utente loggato su Firebase: operatività locale Room");
                }
            }

            @Override
            public void onNetworkLost() {
                Log.w(TAG, "Rete assente: l'app opera in modalità solo locale (Room)");
            }
        });

        networkMonitor.startMonitoring();

        // Ascolta lo stato di autenticazione per agganciare/sganciare Firestore
        auth.addAuthStateListener(firebaseAuth -> {
            FirebaseUser user = firebaseAuth.getCurrentUser();
            if (user != null) {
                Log.i(TAG, "Utente autenticato (" + user.getUid() + "): aggancio sincronizzazione Cloud Firestore");
                if (networkMonitor.isConnected()) {
                    syncPendingData();
                    startRealtimeSync();
                }
            } else {
                Log.i(TAG, "Nessun utente autenticato: sincronizzazione cloud disattivata (solo Room locale)");
                stopRealtimeSync();
            }
        });
    }

    public static PariPariRepository getInstance(Application application) {
        if (INSTANCE == null) {
            synchronized (PariPariRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new PariPariRepository(application);
                }
            }
        }
        return INSTANCE;
    }

    // ====================================================================
    // METODI ACCESSO DATI (LOCAL SINGLE SOURCE OF TRUTH)
    // ====================================================================

    public LiveData<List<Scheda>> getAllSchede() {
        return schedaDao.getAllSchedeLive();
    }

    public LiveData<List<PartecipanteDao.ConteggioPartecipantiTuple>> getAllConteggiPartecipanti() {
        return partecipanteDao.getAllConteggiPartecipanti();
    }

    public void insertScheda(Scheda scheda, @Nullable List<Partecipante> partecipanti) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            schedaDao.insert(scheda);
            if (partecipanti != null && !partecipanti.isEmpty()) {
                partecipanteDao.insertAll(partecipanti);
            }
            // Sincronizza su Firestore solo se connesso e autenticato
            if (networkMonitor.isConnected() && auth.getCurrentUser() != null) {
                uploadScheda(scheda, partecipanti);
            }
        });
    }

    public void deleteScheda(String schedaId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (!networkMonitor.isConnected() || auth.getCurrentUser() == null) {
                schedaDao.updateSyncStatus(schedaId, SyncStatus.PENDING_DELETE);
            } else {
                schedaDao.deleteById(schedaId);
                firestore.collection("groups").document(schedaId).delete()
                        .addOnFailureListener(e -> Log.w(TAG, "Eliminazione remota fallita: " + e.getMessage()));
            }
        });
    }

    public LiveData<List<Partecipante>> getPartecipanti(String schedaId) {
        return partecipanteDao.getPartecipantiBySchedaLive(schedaId);
    }



    public void insertPartecipante(Partecipante partecipante) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            partecipanteDao.insert(partecipante);
            if (networkMonitor.isConnected() && auth.getCurrentUser() != null) {
                uploadPartecipante(partecipante);
            }
        });
    }

    public LiveData<List<Spesa>> getSpese(String schedaId) {
        return spesaDao.getSpeseBySchedaLive(schedaId);
    }

    public void insertSpesa(Spesa spesa, @Nullable List<SpesaPartecipante> quote) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            spesaDao.insert(spesa);
            if (quote != null && !quote.isEmpty()) {
                spesaDao.insertQuote(quote);
            }
            if (networkMonitor.isConnected() && auth.getCurrentUser() != null) {
                uploadSpesa(spesa);
            }
        });
    }

    public void deleteSpesa(String spesaId, String schedaId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (!networkMonitor.isConnected() || auth.getCurrentUser() == null) {
                spesaDao.updateSyncStatus(spesaId, SyncStatus.PENDING_DELETE);
            } else {
                spesaDao.deleteById(spesaId);
                firestore.collection("groups").document(schedaId)
                        .collection("expenses").document(spesaId).delete()
                        .addOnFailureListener(e -> Log.w(TAG, "Eliminazione spesa remota fallita: " + e.getMessage()));
            }
        });
    }

    public LiveData<Integer> getCountSchede() {
        return schedaDao.getCountSchedeLive();
    }

    public LiveData<Integer> getCountSpese() {
        return spesaDao.getCountSpeseLive();
    }

    public LiveData<Double> getTotaleSpeseByScheda(String schedaId) {
        return spesaDao.getTotaleSpeseBySchedaLive(schedaId);
    }

    // ====================================================================
    // SINCRONIZZAZIONE OUTGOING (UPLOAD MODIFICHE LOCALI -> FIRESTORE)
    // ====================================================================

    public void syncPendingData() {
        if (auth.getCurrentUser() == null) {
            Log.d(TAG, "Sync pendenti ignorata: nessun utente autenticato");
            return;
        }

        AppDatabase.databaseWriteExecutor.execute(() -> {
            // 1. Schede
            List<Scheda> pendingSchede = schedaDao.getPendingSyncSchede();
            for (Scheda s : pendingSchede) {
                if (s.getSyncStatus() == SyncStatus.PENDING_DELETE) {
                    firestore.collection("groups").document(s.getId()).delete()
                            .addOnSuccessListener(v -> AppDatabase.databaseWriteExecutor.execute(() -> schedaDao.deleteById(s.getId())))
                            .addOnFailureListener(e -> Log.w(TAG, "Eliminazione remota differita fallita: " + e.getMessage()));
                } else {
                    List<Partecipante> parts = partecipanteDao.getPartecipantiBySchedaSync(s.getId());
                    uploadScheda(s, parts);
                }
            }

            // 2. Partecipanti
            List<Partecipante> pendingParts = partecipanteDao.getPendingSyncPartecipanti();
            for (Partecipante p : pendingParts) {
                if (p.getSyncStatus() == SyncStatus.PENDING_DELETE) {
                    firestore.collection("groups").document(p.getSchedaId())
                            .collection("participants").document(p.getId()).delete()
                            .addOnSuccessListener(v -> AppDatabase.databaseWriteExecutor.execute(() -> partecipanteDao.deleteById(p.getId())));
                } else {
                    uploadPartecipante(p);
                }
            }

            // 3. Spese
            List<Spesa> pendingSpese = spesaDao.getPendingSyncSpese();
            for (Spesa sp : pendingSpese) {
                if (sp.getSyncStatus() == SyncStatus.PENDING_DELETE) {
                    firestore.collection("groups").document(sp.getSchedaId())
                            .collection("expenses").document(sp.getId()).delete()
                            .addOnSuccessListener(v -> AppDatabase.databaseWriteExecutor.execute(() -> spesaDao.deleteById(sp.getId())));
                } else {
                    uploadSpesa(sp);
                }
            }
        });
    }

    private void uploadScheda(Scheda scheda, @Nullable List<Partecipante> partecipanti) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("titolo", scheda.getTitolo());
        data.put("descrizione", scheda.getDescrizione());
        data.put("valutaPredefinita", scheda.getValutaPredefinita());
        data.put("creatoreId", user.getUid());
        data.put("dataCreazione", scheda.getDataCreazione());
        data.put("dataAggiornamento", scheda.getDataAggiornamento());

        WriteBatch batch = firestore.batch();
        DocumentReference ref = firestore.collection("groups").document(scheda.getId());
        batch.set(ref, data);

        if (partecipanti != null) {
            for (Partecipante p : partecipanti) {
                DocumentReference pRef = ref.collection("participants").document(p.getId());
                Map<String, Object> pData = new HashMap<>();
                pData.put("nome", p.getNome());
                pData.put("email", p.getEmail());
                batch.set(pRef, pData);
            }
        }

        batch.commit()
                .addOnSuccessListener(aVoid -> AppDatabase.databaseWriteExecutor.execute(() -> {
                    schedaDao.updateSyncStatus(scheda.getId(), SyncStatus.SYNCED);
                    if (partecipanti != null) {
                        for (Partecipante p : partecipanti) {
                            partecipanteDao.updateSyncStatus(p.getId(), SyncStatus.SYNCED);
                        }
                    }
                }))
                .addOnFailureListener(e -> Log.d(TAG, "Caricamento scheda fallito (riproverà): " + e.getMessage()));
    }

    private void uploadPartecipante(Partecipante p) {
        if (auth.getCurrentUser() == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("nome", p.getNome());
        data.put("email", p.getEmail());

        firestore.collection("groups").document(p.getSchedaId())
                .collection("participants").document(p.getId())
                .set(data)
                .addOnSuccessListener(aVoid -> AppDatabase.databaseWriteExecutor.execute(() ->
                        partecipanteDao.updateSyncStatus(p.getId(), SyncStatus.SYNCED)))
                .addOnFailureListener(e -> Log.d(TAG, "Caricamento partecipante fallito: " + e.getMessage()));
    }

    private void uploadSpesa(Spesa spesa) {
        if (auth.getCurrentUser() == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("titolo", spesa.getTitolo());
        data.put("importo", spesa.getImporto());
        data.put("valuta", spesa.getValuta());
        data.put("dataSpesa", spesa.getDataSpesa());
        data.put("categoria", spesa.getCategoria());
        data.put("pagatoDaId", spesa.getPagatoDaId());
        data.put("scontrinoUrl", spesa.getScontrinoUrl());

        firestore.collection("groups").document(spesa.getSchedaId())
                .collection("expenses").document(spesa.getId())
                .set(data)
                .addOnSuccessListener(aVoid -> AppDatabase.databaseWriteExecutor.execute(() ->
                        spesaDao.updateSyncStatus(spesa.getId(), SyncStatus.SYNCED)))
                .addOnFailureListener(e -> Log.d(TAG, "Caricamento spesa fallito: " + e.getMessage()));
    }

    // ====================================================================
    // SINCRONIZZAZIONE INCOMING (DOWNLOAD REAL-TIME DA FIRESTORE -> ROOM)
    // ====================================================================

    public synchronized void startRealtimeSync() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Log.d(TAG, "Realtime sync ignorata: utente non autenticato");
            return;
        }

        stopRealtimeSync();

        ListenerRegistration reg = firestore.collection("groups")
                .whereEqualTo("creatoreId", currentUser.getUid())
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.w(TAG, "Errore snapshot gruppi Firestore: " + error.getMessage());
                        return;
                    }
                    if (snapshots == null) return;

                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        for (DocumentChange dc : snapshots.getDocumentChanges()) {
                            DocumentSnapshot doc = dc.getDocument();
                            String groupId = doc.getId();

                            switch (dc.getType()) {
                                case ADDED:
                                case MODIFIED:
                                    String titolo = doc.getString("titolo");
                                    String descrizione = doc.getString("descrizione");
                                    String valuta = doc.getString("valutaPredefinita");
                                    String creatoreId = doc.getString("creatoreId");
                                    Long dataCreaz = doc.getLong("dataCreazione");
                                    Long dataAgg = doc.getLong("dataAggiornamento");

                                    if (titolo != null) {
                                        Scheda remoteScheda = new Scheda(
                                                groupId,
                                                titolo,
                                                descrizione != null ? descrizione : "",
                                                valuta != null ? valuta : "EUR",
                                                creatoreId != null ? creatoreId : "",
                                                dataCreaz != null ? dataCreaz : System.currentTimeMillis(),
                                                dataAgg != null ? dataAgg : System.currentTimeMillis(),
                                                SyncStatus.SYNCED
                                        );
                                        try {
                                            schedaDao.insert(remoteScheda);
                                            attachSubcollectionListeners(groupId);
                                        } catch (Exception e) {
                                            Log.w(TAG, "Sync scheda fallito: " + e.getMessage());
                                        }
                                    }
                                    break;

                                case REMOVED:
                                    schedaDao.deleteById(groupId);
                                    detachSubcollectionListeners(groupId);
                                    break;
                            }
                        }
                    });
                });

        activeListeners.add(reg);
    }

    private void attachSubcollectionListeners(String groupId) {
        if (groupSubListeners.containsKey(groupId + "_parts")) return;

        ListenerRegistration pReg = firestore.collection("groups").document(groupId)
                .collection("participants")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        for (DocumentChange dc : snapshots.getDocumentChanges()) {
                            DocumentSnapshot doc = dc.getDocument();
                            String partId = doc.getId();
                            if (dc.getType() == DocumentChange.Type.REMOVED) {
                                partecipanteDao.deleteById(partId);
                            } else {
                                String nome = doc.getString("nome");
                                String email = doc.getString("email");
                                if (nome != null) {
                                    Partecipante p = new Partecipante(partId, groupId, nome, email, SyncStatus.SYNCED);
                                    try {
                                        partecipanteDao.insert(p);
                                    } catch (Exception e) {
                                        Log.w(TAG, "Sync partecipante fallito: " + e.getMessage());
                                    }
                                }
                            }
                        }
                    });
                });
        groupSubListeners.put(groupId + "_parts", pReg);

        ListenerRegistration eReg = firestore.collection("groups").document(groupId)
                .collection("expenses")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        for (DocumentChange dc : snapshots.getDocumentChanges()) {
                            DocumentSnapshot doc = dc.getDocument();
                            String spesaId = doc.getId();
                            if (dc.getType() == DocumentChange.Type.REMOVED) {
                                spesaDao.deleteById(spesaId);
                            } else {
                                String titolo = doc.getString("titolo");
                                Double importo = doc.getDouble("importo");
                                String valuta = doc.getString("valuta");
                                Long dataSpesa = doc.getLong("dataSpesa");
                                String categoria = doc.getString("categoria");
                                String pagatoDaId = doc.getString("pagatoDaId");
                                String scontrinoUrl = doc.getString("scontrinoUrl");

                                if (titolo != null && importo != null && pagatoDaId != null) {
                                    Spesa sp = new Spesa(
                                            spesaId,
                                            groupId,
                                            titolo,
                                            importo,
                                            valuta != null ? valuta : "EUR",
                                            dataSpesa != null ? dataSpesa : System.currentTimeMillis(),
                                            categoria != null ? categoria : "Altro",
                                            pagatoDaId,
                                            scontrinoUrl,
                                            SyncStatus.SYNCED
                                    );
                                    try {
                                        spesaDao.insert(sp);
                                    } catch (Exception e) {
                                        Log.w(TAG, "Sync spesa fallito (vincolo o partecipante non ancora presente): " + e.getMessage());
                                    }
                                }
                            }
                        }
                    });
                });
        groupSubListeners.put(groupId + "_expenses", eReg);
    }

    private void detachSubcollectionListeners(String groupId) {
        ListenerRegistration pReg = groupSubListeners.remove(groupId + "_parts");
        if (pReg != null) pReg.remove();

        ListenerRegistration eReg = groupSubListeners.remove(groupId + "_expenses");
        if (eReg != null) eReg.remove();
    }

    public synchronized void stopRealtimeSync() {
        for (ListenerRegistration reg : activeListeners) {
            if (reg != null) reg.remove();
        }
        activeListeners.clear();

        for (ListenerRegistration reg : groupSubListeners.values()) {
            if (reg != null) reg.remove();
        }
        groupSubListeners.clear();
    }
}
