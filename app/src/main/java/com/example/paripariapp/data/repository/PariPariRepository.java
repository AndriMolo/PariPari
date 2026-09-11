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
import com.example.paripariapp.data.model.SpesaConDettagli;
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

        networkMonitor = new NetworkConnectivityMonitor(application, new NetworkConnectivityMonitor.OnNetworkChangeListener() {
            @Override
            public void onNetworkAvailable() {
                Log.i(TAG, "Rete attiva rilevata");
                if (auth.getCurrentUser() != null) {
                    syncPendingData();
                    startRealtimeSync();
                }
            }

            @Override
            public void onNetworkLost() {
                Log.w(TAG, "Rete assente: l'app opera in modalità solo locale (Room)");
            }
        });

        networkMonitor.startMonitoring();

        auth.addAuthStateListener(firebaseAuth -> {
            FirebaseUser user = firebaseAuth.getCurrentUser();
            if (user != null) {
                if (networkMonitor.isConnected()) {
                    syncPendingData();
                    startRealtimeSync();
                }
            } else {
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
            if (networkMonitor.isConnected() && auth.getCurrentUser() != null) {
                uploadScheda(scheda, partecipanti);
            }
        });
    }

    public void updateTitoloScheda(String schedaId, String nuovoTitolo) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            long adesso = System.currentTimeMillis();
            schedaDao.updateTitolo(schedaId, nuovoTitolo, adesso, SyncStatus.PENDING_UPDATE);

            if (auth.getCurrentUser() != null && networkMonitor.isConnected()) {
                firestore.collection("groups").document(schedaId)
                        .update("titolo", nuovoTitolo, "dataAggiornamento", adesso);
            }
        });
    }

    public void deleteScheda(String schedaId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            schedaDao.deleteById(schedaId);
            detachSubcollectionListeners(schedaId);

            if (auth.getCurrentUser() != null && networkMonitor.isConnected()) {
                firestore.collection("groups").document(schedaId).delete();
            }
        });
    }

    public LiveData<List<Partecipante>> getPartecipanti(String schedaId) {
        if (auth.getCurrentUser() != null && networkMonitor.isConnected()) {
            attachSubcollectionListeners(schedaId);
        }
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

    public void deletePartecipante(String partecipanteId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Partecipante p = partecipanteDao.getPartecipanteById(partecipanteId);
            partecipanteDao.deleteById(partecipanteId);

            if (p != null && auth.getCurrentUser() != null && networkMonitor.isConnected()) {
                firestore.collection("groups").document(p.getSchedaId())
                        .collection("participants").document(partecipanteId).delete();
            }
        });
    }

    public LiveData<List<Spesa>> getSpese(String schedaId) {
        if (auth.getCurrentUser() != null && networkMonitor.isConnected()) {
            attachSubcollectionListeners(schedaId);
        }
        return spesaDao.getSpeseBySchedaLive(schedaId);
    }

    public LiveData<List<SpesaConDettagli>> getSpeseConDettagli(String schedaId) {
        if (auth.getCurrentUser() != null && networkMonitor.isConnected()) {
            attachSubcollectionListeners(schedaId);
        }
        return spesaDao.getSpeseConDettagliBySchedaLive(schedaId);
    }

    public void insertSpesa(Spesa spesa, @Nullable List<SpesaPartecipante> quote) {
        insertSpesaConQuote(spesa, quote);
    }

    public void insertSpesaConQuote(Spesa spesa, @Nullable List<SpesaPartecipante> quote) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // Salva sempre e prioritariamente in Room
            spesaDao.insert(spesa);
            if (quote != null && !quote.isEmpty()) {
                spesaDao.insertQuote(quote);
            }
            if (networkMonitor.isConnected() && auth.getCurrentUser() != null) {
                uploadSpesaConQuote(spesa, quote);
            }
        });
    }

    public void deleteSpesa(String spesaId, String schedaId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            spesaDao.deleteQuoteBySpesaId(spesaId);
            spesaDao.deleteById(spesaId);

            if (networkMonitor.isConnected() && auth.getCurrentUser() != null) {
                firestore.collection("groups").document(schedaId)
                        .collection("expenses").document(spesaId).delete()
                        .addOnFailureListener(e -> Log.w(TAG, "Eliminazione spesa remota differita: " + e.getMessage()));
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

    public void syncPendingData() {
        if (auth.getCurrentUser() == null) return;

        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<Scheda> pendingSchede = schedaDao.getPendingSyncSchede();
            for (Scheda s : pendingSchede) {
                if (s.getSyncStatus() == SyncStatus.PENDING_DELETE) {
                    firestore.collection("groups").document(s.getId()).delete()
                            .addOnSuccessListener(v -> AppDatabase.databaseWriteExecutor.execute(() -> schedaDao.deleteById(s.getId())));
                } else {
                    List<Partecipante> parts = partecipanteDao.getPartecipantiBySchedaSync(s.getId());
                    uploadScheda(s, parts);
                }
            }

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

            List<Spesa> pendingSpese = spesaDao.getPendingSyncSpese();
            for (Spesa sp : pendingSpese) {
                if (sp.getSyncStatus() == SyncStatus.PENDING_DELETE) {
                    firestore.collection("groups").document(sp.getSchedaId())
                            .collection("expenses").document(sp.getId()).delete()
                            .addOnSuccessListener(v -> AppDatabase.databaseWriteExecutor.execute(() -> {
                                spesaDao.deleteQuoteBySpesaId(sp.getId());
                                spesaDao.deleteById(sp.getId());
                            }));
                } else {
                    List<SpesaPartecipante> quote = spesaDao.getQuoteBySpesaSync(sp.getId());
                    uploadSpesaConQuote(sp, quote);
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
                .addOnFailureListener(e -> Log.d(TAG, "Caricamento scheda differito: " + e.getMessage()));
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

    private void uploadSpesaConQuote(Spesa spesa, @Nullable List<SpesaPartecipante> quote) {
        if (auth.getCurrentUser() == null) return;

        WriteBatch batch = firestore.batch();
        DocumentReference spesaRef = firestore.collection("groups").document(spesa.getSchedaId())
                .collection("expenses").document(spesa.getId());

        Map<String, Object> data = new HashMap<>();
        data.put("titolo", spesa.getTitolo());
        data.put("importo", spesa.getImporto());
        data.put("valuta", spesa.getValuta());
        data.put("dataSpesa", spesa.getDataSpesa());
        data.put("categoria", spesa.getCategoria());
        data.put("pagatoDaId", spesa.getPagatoDaId());
        data.put("scontrinoUrl", spesa.getScontrinoUrl());

        batch.set(spesaRef, data);

        if (quote != null) {
            for (SpesaPartecipante q : quote) {
                DocumentReference qRef = spesaRef.collection("shares").document(q.getPartecipanteId());
                Map<String, Object> qData = new HashMap<>();
                qData.put("partecipanteId", q.getPartecipanteId());
                qData.put("quota", q.getQuota());
                batch.set(qRef, qData);
            }
        }

        batch.commit()
                .addOnSuccessListener(aVoid -> AppDatabase.databaseWriteExecutor.execute(() ->
                        spesaDao.updateSyncStatus(spesa.getId(), SyncStatus.SYNCED)))
                .addOnFailureListener(e -> Log.e(TAG, "Upload spesa fallito: " + e.getMessage()));
    }

    public synchronized void startRealtimeSync() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) return;

        stopRealtimeSync();

        ListenerRegistration reg = firestore.collection("groups")
                .whereEqualTo("creatoreId", currentUser.getUid())
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;

                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        for (DocumentChange dc : snapshots.getDocumentChanges()) {
                            DocumentSnapshot doc = dc.getDocument();
                            String groupId = doc.getId();

                            switch (dc.getType()) {
                                case ADDED:
                                    String titoloAdd = doc.getString("titolo");
                                    String descAdd = doc.getString("descrizione");
                                    String valutaAdd = doc.getString("valutaPredefinita");
                                    String creatoreId = doc.getString("creatoreId");
                                    Long dataCreaz = doc.getLong("dataCreazione");
                                    Long dataAggAdd = doc.getLong("dataAggiornamento");

                                    if (titoloAdd != null) {
                                        // PROTEZIONE CASCADE:
                                        // Non fare REPLACE se la scheda esiste già in locale, altrimenti SQLite attiva il CASCADE
                                        // ed elimina tutte le spese collegate!
                                        schedaDao.updateTitolo(
                                                groupId,
                                                titoloAdd,
                                                dataAggAdd != null ? dataAggAdd : System.currentTimeMillis(),
                                                SyncStatus.SYNCED
                                        );

                                        // Aggancia sempre i listener
                                        attachSubcollectionListeners(groupId);
                                    }
                                    break;

                                case MODIFIED:
                                    String titoloMod = doc.getString("titolo");
                                    Long dataAggMod = doc.getLong("dataAggiornamento");
                                    if (titoloMod != null) {
                                        schedaDao.updateTitolo(
                                                groupId,
                                                titoloMod,
                                                dataAggMod != null ? dataAggMod : System.currentTimeMillis(),
                                                SyncStatus.SYNCED
                                        );
                                    }
                                    break;

                                case REMOVED:
                                    // Non cancellare localmente se siamo offline o in stato incerto
                                    Log.w(TAG, "Scheda rimossa da remoto: " + groupId);
                                    break;
                            }
                        }
                    });
                });

        activeListeners.add(reg);
    }

    private void attachSubcollectionListeners(String groupId) {
        if (groupSubListeners.containsKey(groupId + "_parts")) return;

        // 1. LISTENER PARTECIPANTI
        ListenerRegistration pReg = firestore.collection("groups").document(groupId)
                .collection("participants")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        for (DocumentChange dc : snapshots.getDocumentChanges()) {
                            DocumentSnapshot doc = dc.getDocument();
                            String partId = doc.getId();
                            if (dc.getType() == DocumentChange.Type.REMOVED) {
                                Log.w(TAG, "Partecipante rimosso da remoto: " + partId);
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

        // 2. LISTENER SPESE
        ListenerRegistration eReg = firestore.collection("groups").document(groupId)
                .collection("expenses")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        for (DocumentChange dc : snapshots.getDocumentChanges()) {
                            DocumentSnapshot doc = dc.getDocument();
                            String spesaId = doc.getId();

                            if (dc.getType() == DocumentChange.Type.REMOVED) {
                                Log.w(TAG, "Spesa rimossa da remoto ignorata per sicurezza locale: " + spesaId);
                            } else {
                                String titolo = doc.getString("titolo");
                                Double importo = doc.getDouble("importo");
                                String valuta = doc.getString("valuta");
                                Long dataSpesa = doc.getLong("dataSpesa");
                                String categoria = doc.getString("categoria");
                                String pagatoDaId = doc.getString("pagatoDaId");
                                String scontrinoUrl = doc.getString("scontrinoUrl");

                                if (titolo != null && importo != null && pagatoDaId != null) {
                                    Partecipante pagatoreEsistente = partecipanteDao.getPartecipanteById(pagatoDaId);
                                    if (pagatoreEsistente == null) {
                                        Partecipante placeholder = new Partecipante(
                                                pagatoDaId,
                                                groupId,
                                                "Partecipante",
                                                null,
                                                SyncStatus.SYNCED
                                        );
                                        partecipanteDao.insert(placeholder);
                                    }

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

                                        doc.getReference().collection("shares").get().addOnSuccessListener(shareSnaps -> {
                                            if (shareSnaps != null && !shareSnaps.isEmpty()) {
                                                List<SpesaPartecipante> quoteRemote = new ArrayList<>();
                                                for (DocumentSnapshot sDoc : shareSnaps.getDocuments()) {
                                                    String pId = sDoc.getString("partecipanteId");
                                                    Double quotaVal = sDoc.getDouble("quota");
                                                    if (pId != null && quotaVal != null) {
                                                        Partecipante deb = partecipanteDao.getPartecipanteById(pId);
                                                        if (deb == null) {
                                                            partecipanteDao.insert(new Partecipante(pId, groupId, "Partecipante", null, SyncStatus.SYNCED));
                                                        }
                                                        quoteRemote.add(new SpesaPartecipante(spesaId, pId, quotaVal, SyncStatus.SYNCED));
                                                    }
                                                }
                                                AppDatabase.databaseWriteExecutor.execute(() -> spesaDao.insertQuote(quoteRemote));
                                            }
                                        });

                                    } catch (Exception e) {
                                        Log.w(TAG, "Sync spesa fallito: " + e.getMessage());
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

    public LiveData<List<SpesaPartecipante>> getQuoteDellaScheda(String schedaId) {
        return spesaDao.getTutteQuoteBySchedaLive(schedaId);
    }
}