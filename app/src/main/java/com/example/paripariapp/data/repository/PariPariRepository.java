package com.example.paripariapp.data.repository;

import android.app.Application;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.example.paripariapp.data.local.AppDatabase;
import com.example.paripariapp.data.local.PartecipanteDao;
import com.example.paripariapp.data.local.SchedaDao;
import com.example.paripariapp.data.local.SpesaDao;
import com.example.paripariapp.data.model.BilancioPersonaItem;
import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.RisultatoSaldi;
import com.example.paripariapp.data.model.Scheda;
import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.data.model.SpesaConDettagli;
import com.example.paripariapp.data.model.SpesaPartecipante;
import com.example.paripariapp.data.model.SyncStatus;
import com.example.paripariapp.data.model.TrasferimentoSaldo;
import com.example.paripariapp.util.CalcolatoreSaldi;
import com.example.paripariapp.util.NetworkConnectivityMonitor;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;

import android.os.Handler;
import android.os.Looper;
import com.example.paripariapp.util.CodiceInvitoUtil;
import java.util.UUID;
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

    private final MediatorLiveData<RisultatoSaldi> risultatoSaldiLiveData = new MediatorLiveData<>();
    private boolean saldiSourcesInitialized = false;

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
            if (scheda.getCodiceInvito() == null || scheda.getCodiceInvito().trim().isEmpty()) {
                scheda.setCodiceInvito(CodiceInvitoUtil.generaCodice());
            }
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
            detachSubcollectionListeners(schedaId);

            if (auth.getCurrentUser() != null && networkMonitor.isConnected()) {
                FirebaseUser currentUser = auth.getCurrentUser();
                firestore.collection("groups").document(schedaId)
                        .collection("participants")
                        .get()
                        .addOnSuccessListener(AppDatabase.databaseWriteExecutor, snapshot -> {
                            if (snapshot == null || snapshot.isEmpty() || snapshot.size() <= 1) {
                                // Nessun altro partecipante: l'utente è l'unico, elimina l'intero gruppo da Firestore
                                firestore.collection("groups").document(schedaId).delete()
                                        .addOnFailureListener(e -> Log.w(TAG, "Errore eliminazione gruppo remoto: " + e.getMessage()));
                            } else {
                                // Il gruppo è condiviso: dissociati rimuovendo solo il proprio documento partecipante
                                for (DocumentSnapshot doc : snapshot.getDocuments()) {
                                    String email = doc.getString("email");
                                    String nome = doc.getString("nome");
                                    boolean isCurrentUser = (currentUser.getEmail() != null && currentUser.getEmail().equalsIgnoreCase(email))
                                            || (nome != null && (nome.trim().equalsIgnoreCase("io") || nome.trim().equalsIgnoreCase("me")));
                                    if (isCurrentUser) {
                                        doc.getReference().delete();
                                        break;
                                    }
                                }

                                // Se l'utente era registrato come creatoreId, riassegna per evitare che whereEqualTo lo risincronizzi
                                firestore.collection("groups").document(schedaId).get()
                                        .addOnSuccessListener(AppDatabase.databaseWriteExecutor, groupDoc -> {
                                            if (groupDoc != null && groupDoc.exists()) {
                                                String creatoreId = groupDoc.getString("creatoreId");
                                                if (creatoreId != null && creatoreId.equals(currentUser.getUid())) {
                                                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                                                        if (!doc.getId().equals(currentUser.getUid())) {
                                                            firestore.collection("groups").document(schedaId)
                                                                    .update("creatoreId", doc.getId());
                                                            break;
                                                        }
                                                    }
                                                }
                                            }
                                        });
                            }
                        })
                        .addOnFailureListener(e -> Log.w(TAG, "Errore dissociazione gruppo: " + e.getMessage()));
            }

            // Pulizia transazionale/a cascata nel database Room locale
            spesaDao.deleteQuoteBySchedaId(schedaId);
            spesaDao.deleteBySchedaId(schedaId);
            partecipanteDao.deleteBySchedaId(schedaId);
            schedaDao.deleteById(schedaId);
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

    public LiveData<RisultatoSaldi> getRisultatoSaldi() {
        if (!saldiSourcesInitialized) {
            saldiSourcesInitialized = true;
            risultatoSaldiLiveData.addSource(schedaDao.getAllSchedeLive(), schede -> ricalcolaSaldi(schede));
            risultatoSaldiLiveData.addSource(spesaDao.getCountSpeseLive(), count -> ricalcolaSaldi(null));
            risultatoSaldiLiveData.addSource(partecipanteDao.getCountPartecipantiLive(), count -> ricalcolaSaldi(null));
        }
        return risultatoSaldiLiveData;
    }

    private void ricalcolaSaldi(@Nullable List<Scheda> schedeCache) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<Scheda> schede = (schedeCache != null) ? schedeCache : schedaDao.getAllSchedeSync();
            if (schede == null || schede.isEmpty()) {
                risultatoSaldiLiveData.postValue(new RisultatoSaldi(0.0, 0.0, new ArrayList<>()));
                return;
            }

            double totaleRicevere = 0.0;
            double totaleDare = 0.0;
            List<BilancioPersonaItem> bilanci = new ArrayList<>();

            for (Scheda scheda : schede) {
                String idScheda = scheda.getId();
                String titoloScheda = scheda.getTitolo();
                String valuta = scheda.getValutaPredefinita() != null ? scheda.getValutaPredefinita() : "EUR";

                List<Partecipante> parti = partecipanteDao.getPartecipantiBySchedaSync(idScheda);
                List<Spesa> spese = spesaDao.getSpeseBySchedaSync(idScheda);
                List<SpesaPartecipante> quote = spesaDao.getTutteQuoteBySchedaSync(idScheda);

                if (parti == null || parti.isEmpty() || spese == null || spese.isEmpty()) {
                    continue;
                }

                List<TrasferimentoSaldo> trasferimenti = CalcolatoreSaldi.calcolaTrasferimenti(parti, spese, quote, valuta);

                String mioId = null;
                for (Partecipante p : parti) {
                    if (p.getNome() != null) {
                        String n = p.getNome().trim().toLowerCase();
                        if (n.equals("io") || n.equals("me")) {
                            mioId = p.getId();
                            break;
                        }
                    }
                }

                if (mioId != null) {
                    for (TrasferimentoSaldo t : trasferimenti) {
                        if (t.getDaId().equals(mioId)) {
                            totaleDare += t.getImporto();
                            bilanci.add(new BilancioPersonaItem(t.getANome(), titoloScheda, -t.getImporto(), valuta));
                        } else if (t.getAId().equals(mioId)) {
                            totaleRicevere += t.getImporto();
                            bilanci.add(new BilancioPersonaItem(t.getDaNome(), titoloScheda, t.getImporto(), valuta));
                        }
                    }
                }
            }

            risultatoSaldiLiveData.postValue(new RisultatoSaldi(totaleRicevere, totaleDare, bilanci));
        });
    }

    public void syncPendingData() {
        if (auth.getCurrentUser() == null) return;

        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<Scheda> pendingSchede = schedaDao.getPendingSyncSchede();
            for (Scheda s : pendingSchede) {
                if (s.getSyncStatus() == SyncStatus.PENDING_DELETE) {
                    firestore.collection("groups").document(s.getId()).delete()
                            .addOnSuccessListener(AppDatabase.databaseWriteExecutor, v -> schedaDao.deleteById(s.getId()));
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
                            .addOnSuccessListener(AppDatabase.databaseWriteExecutor, v -> partecipanteDao.deleteById(p.getId()));
                } else {
                    uploadPartecipante(p);
                }
            }

            List<Spesa> pendingSpese = spesaDao.getPendingSyncSpese();
            for (Spesa sp : pendingSpese) {
                if (sp.getSyncStatus() == SyncStatus.PENDING_DELETE) {
                    firestore.collection("groups").document(sp.getSchedaId())
                            .collection("expenses").document(sp.getId()).delete()
                            .addOnSuccessListener(AppDatabase.databaseWriteExecutor, v -> {
                                spesaDao.deleteQuoteBySpesaId(sp.getId());
                                spesaDao.deleteById(sp.getId());
                            });
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

        if (scheda.getCodiceInvito() == null || scheda.getCodiceInvito().trim().isEmpty()) {
            String nuovoCodice = CodiceInvitoUtil.generaCodice();
            scheda.setCodiceInvito(nuovoCodice);
            schedaDao.updateCodiceInvito(scheda.getId(), nuovoCodice);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("titolo", scheda.getTitolo());
        data.put("descrizione", scheda.getDescrizione());
        data.put("valutaPredefinita", scheda.getValutaPredefinita());
        data.put("creatoreId", user.getUid());
        data.put("dataCreazione", scheda.getDataCreazione());
        data.put("dataAggiornamento", scheda.getDataAggiornamento());
        data.put("codiceInvito", scheda.getCodiceInvito());

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
                .addOnSuccessListener(AppDatabase.databaseWriteExecutor, aVoid -> {
                    schedaDao.updateSyncStatus(scheda.getId(), SyncStatus.SYNCED);
                    if (partecipanti != null) {
                        for (Partecipante p : partecipanti) {
                            partecipanteDao.updateSyncStatus(p.getId(), SyncStatus.SYNCED);
                        }
                    }
                })
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
                .addOnSuccessListener(AppDatabase.databaseWriteExecutor, aVoid ->
                        partecipanteDao.updateSyncStatus(p.getId(), SyncStatus.SYNCED))
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
                qData.put("quotaPagata", q.getQuotaPagata());
                batch.set(qRef, qData);
            }
        }

        batch.commit()
                .addOnSuccessListener(AppDatabase.databaseWriteExecutor, aVoid ->
                        spesaDao.updateSyncStatus(spesa.getId(), SyncStatus.SYNCED))
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
                                    String codInvitoAdd = doc.getString("codiceInvito");

                                    if (titoloAdd != null) {
                                        // Verifica preventiva: se la scheda non esiste in locale, inseriscila per evitare chiavi esterne orfane
                                        Scheda schedaEsistente = schedaDao.getSchedaById(groupId);
                                        if (schedaEsistente == null) {
                                            Scheda nuovaScheda = new Scheda(
                                                    groupId,
                                                    titoloAdd,
                                                    descAdd != null ? descAdd : "",
                                                    valutaAdd != null ? valutaAdd : "EUR",
                                                    creatoreId != null ? creatoreId : "",
                                                    dataCreaz != null ? dataCreaz : System.currentTimeMillis(),
                                                    dataAggAdd != null ? dataAggAdd : System.currentTimeMillis(),
                                                    SyncStatus.SYNCED
                                            );
                                            nuovaScheda.setCodiceInvito(codInvitoAdd);
                                            schedaDao.insert(nuovaScheda);
                                        } else {
                                            schedaDao.updateTitolo(
                                                    groupId,
                                                    titoloAdd,
                                                    dataAggAdd != null ? dataAggAdd : System.currentTimeMillis(),
                                                    SyncStatus.SYNCED
                                            );
                                            if (codInvitoAdd != null) {
                                                schedaDao.updateCodiceInvito(groupId, codInvitoAdd);
                                            }
                                        }

                                        // Aggancia sempre i listener
                                        attachSubcollectionListeners(groupId);
                                    }
                                    break;

                                case MODIFIED:
                                    String titoloMod = doc.getString("titolo");
                                    Long dataAggMod = doc.getLong("dataAggiornamento");
                                    String codInvitoMod = doc.getString("codiceInvito");
                                    if (titoloMod != null) {
                                        schedaDao.updateTitolo(
                                                groupId,
                                                titoloMod,
                                                dataAggMod != null ? dataAggMod : System.currentTimeMillis(),
                                                SyncStatus.SYNCED
                                        );
                                    }
                                    if (codInvitoMod != null) {
                                        schedaDao.updateCodiceInvito(groupId, codInvitoMod);
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

        // Aggancia i listener delle schede già salvate in locale (anche quelle a cui ci si è uniti)
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<Scheda> schedeLocali = schedaDao.getAllSchedeSync();
            if (schedeLocali != null) {
                for (Scheda s : schedeLocali) {
                    attachSubcollectionListeners(s.getId());
                }
            }
        });
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

                                        doc.getReference().collection("shares").get().addOnSuccessListener(AppDatabase.databaseWriteExecutor, shareSnaps -> {
                                            if (shareSnaps != null && !shareSnaps.isEmpty()) {
                                                List<SpesaPartecipante> quoteRemote = new ArrayList<>();
                                                for (DocumentSnapshot sDoc : shareSnaps.getDocuments()) {
                                                    String pId = sDoc.getString("partecipanteId");
                                                    Double quotaVal = sDoc.getDouble("quota");
                                                    Double quotaPagataVal = sDoc.getDouble("quotaPagata");
                                                    double quotaPagata = quotaPagataVal != null ? quotaPagataVal : 0.0;
                                                    if (pId != null && quotaVal != null) {
                                                        Partecipante deb = partecipanteDao.getPartecipanteById(pId);
                                                        if (deb == null) {
                                                            partecipanteDao.insert(new Partecipante(pId, groupId, "Partecipante", null, SyncStatus.SYNCED));
                                                        }
                                                        quoteRemote.add(new SpesaPartecipante(spesaId, pId, quotaVal, quotaPagata, SyncStatus.SYNCED));
                                                    }
                                                }
                                                spesaDao.insertQuote(quoteRemote);
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

    public LiveData<Spesa> getSpesaById(String spesaId) {
        return spesaDao.getSpesaByIdLive(spesaId);
    }

    public LiveData<List<SpesaPartecipante>> getQuoteBySpesa(String spesaId) {
        return spesaDao.getQuoteBySpesaLive(spesaId);
    }

    public void aggiornaSpesaConQuote(Spesa spesa, List<SpesaPartecipante> quote) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            spesa.setSyncStatus(SyncStatus.PENDING_UPDATE);
            spesaDao.insert(spesa);
            spesaDao.deleteQuoteBySpesaId(spesa.getId());
            if (quote != null && !quote.isEmpty()) {
                for (SpesaPartecipante q : quote) {
                    q.setSyncStatus(SyncStatus.PENDING_UPDATE);
                }
                spesaDao.insertQuote(quote);
            }
            if (networkMonitor.isConnected() && auth.getCurrentUser() != null) {
                uploadSpesaConQuote(spesa, quote);
            }
        });
    }

    public LiveData<Scheda> getSchedaById(String schedaId) {
        return schedaDao.getSchedaByIdLive(schedaId);
    }

    public void assicuraCodiceInvito(Scheda scheda) {
        if (scheda == null) return;
        if (scheda.getCodiceInvito() == null || scheda.getCodiceInvito().trim().isEmpty()) {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                String nuovoCodice = CodiceInvitoUtil.generaCodice();
                scheda.setCodiceInvito(nuovoCodice);
                schedaDao.updateCodiceInvito(scheda.getId(), nuovoCodice);
                if (auth.getCurrentUser() != null && networkMonitor.isConnected()) {
                    firestore.collection("groups").document(scheda.getId())
                            .update("codiceInvito", nuovoCodice);
                }
            });
        }
    }

    public interface OnJoinSchedaCallback {
        void onSuccess(String schedaId, String titolo);
        void onError(String errore);
    }

    public void uniscitiASchedaTramiteCodice(String codice, OnJoinSchedaCallback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        String cleanCode = CodiceInvitoUtil.normalizzaCodice(codice);
        if (cleanCode.isEmpty()) {
            mainHandler.post(() -> callback.onError("Inserisci un codice valido"));
            return;
        }

        AppDatabase.databaseWriteExecutor.execute(() -> {
            // 1. Controlla se la scheda è già presente in locale
            Scheda local = schedaDao.getSchedaByCodiceInvito(cleanCode);
            if (local == null) {
                local = schedaDao.getSchedaById(cleanCode);
            }
            if (local != null) {
                final Scheda foundLocal = local;
                attachSubcollectionListeners(foundLocal.getId());
                mainHandler.post(() -> callback.onSuccess(foundLocal.getId(), foundLocal.getTitolo()));
                return;
            }

            // 2. Se non in locale, verifichiamo la connessione di rete
            if (!networkMonitor.isConnected() || auth.getCurrentUser() == null) {
                mainHandler.post(() -> callback.onError("Connessione a internet necessaria per cercare il gruppo"));
                return;
            }

            // 3. Cerca in Firestore per codiceInvito, poi document ID come fallback
            firestore.collection("groups").whereEqualTo("codiceInvito", cleanCode).limit(1).get()
                    .addOnSuccessListener(AppDatabase.databaseWriteExecutor, querySnapshot -> {
                        if (querySnapshot != null && !querySnapshot.isEmpty()) {
                            elaboraJoinGruppo(querySnapshot.getDocuments().get(0), cleanCode, callback, mainHandler);
                        } else {
                            firestore.collection("groups").document(cleanCode).get()
                                    .addOnSuccessListener(AppDatabase.databaseWriteExecutor, docSnapshot -> {
                                        if (docSnapshot != null && docSnapshot.exists()) {
                                            elaboraJoinGruppo(docSnapshot, cleanCode, callback, mainHandler);
                                        } else {
                                            mainHandler.post(() -> callback.onError("Nessun gruppo trovato con il codice inserito"));
                                        }
                                    })
                                    .addOnFailureListener(e ->
                                            mainHandler.post(() -> callback.onError("Errore durante la ricerca: " + e.getLocalizedMessage()))
                                    );
                        }
                    })
                    .addOnFailureListener(e ->
                            mainHandler.post(() -> callback.onError("Errore durante la ricerca: " + e.getLocalizedMessage()))
                    );
        });
    }

    private void elaboraJoinGruppo(DocumentSnapshot groupDoc, String cleanCode, OnJoinSchedaCallback callback, Handler mainHandler) {
        String groupId = groupDoc.getId();
        String titolo = groupDoc.getString("titolo");
        String desc = groupDoc.getString("descrizione");
        String valuta = groupDoc.getString("valutaPredefinita");
        String creatoreId = groupDoc.getString("creatoreId");
        Long dataCreaz = groupDoc.getLong("dataCreazione");
        Long dataAgg = groupDoc.getLong("dataAggiornamento");
        String codInv = groupDoc.getString("codiceInvito");
        if (codInv == null || codInv.isEmpty()) {
            codInv = cleanCode;
        }

        Scheda scheda = new Scheda(
                groupId,
                titolo != null ? titolo : "Gruppo",
                desc != null ? desc : "",
                valuta != null ? valuta : "EUR",
                creatoreId != null ? creatoreId : "",
                dataCreaz != null ? dataCreaz : System.currentTimeMillis(),
                dataAgg != null ? dataAgg : System.currentTimeMillis(),
                SyncStatus.SYNCED
        );
        scheda.setCodiceInvito(codInv);
        schedaDao.insert(scheda);

        FirebaseUser currentUser = auth.getCurrentUser();
        String currentEmail = currentUser != null ? currentUser.getEmail() : null;
        String currentUid = currentUser != null ? currentUser.getUid() : null;
        String currentNome = (currentUser != null && currentUser.getDisplayName() != null && !currentUser.getDisplayName().trim().isEmpty())
                ? currentUser.getDisplayName() : "Io";

        groupDoc.getReference().collection("participants").get()
                .addOnSuccessListener(AppDatabase.databaseWriteExecutor, pSnaps -> {
                    boolean giaPresente = false;
                    List<Partecipante> parts = new ArrayList<>();
                    if (pSnaps != null) {
                        for (DocumentSnapshot pDoc : pSnaps.getDocuments()) {
                            String pId = pDoc.getId();
                            String pNome = pDoc.getString("nome");
                            String pEmail = pDoc.getString("email");
                            if (pNome != null) {
                                parts.add(new Partecipante(pId, groupId, pNome, pEmail, SyncStatus.SYNCED));
                                if ((currentEmail != null && currentEmail.equalsIgnoreCase(pEmail)) ||
                                    (currentUid != null && currentUid.equals(pId))) {
                                    giaPresente = true;
                                }
                            }
                        }
                    }
                    if (!parts.isEmpty()) {
                        partecipanteDao.insertAll(parts);
                    }

                    if (!giaPresente && currentUser != null) {
                        Partecipante me = new Partecipante(
                                UUID.randomUUID().toString(),
                                groupId,
                                currentNome,
                                currentEmail,
                                SyncStatus.PENDING_INSERT
                        );
                        partecipanteDao.insert(me);
                        uploadPartecipante(me);
                    }

                    scaricaSpeseGruppo(groupDoc.getReference(), groupId);
                    attachSubcollectionListeners(groupId);

                    mainHandler.post(() -> callback.onSuccess(groupId, titolo != null ? titolo : "Gruppo"));
                })
                .addOnFailureListener(e -> {
                    attachSubcollectionListeners(groupId);
                    mainHandler.post(() -> callback.onSuccess(groupId, titolo != null ? titolo : "Gruppo"));
                });
    }

    private void scaricaSpeseGruppo(DocumentReference groupRef, String groupId) {
        groupRef.collection("expenses").get().addOnSuccessListener(AppDatabase.databaseWriteExecutor, expSnaps -> {
            if (expSnaps == null || expSnaps.isEmpty()) return;
            for (DocumentSnapshot doc : expSnaps.getDocuments()) {
                String spesaId = doc.getId();
                String titolo = doc.getString("titolo");
                Double importo = doc.getDouble("importo");
                String valuta = doc.getString("valuta");
                Long dataSpesa = doc.getLong("dataSpesa");
                String categoria = doc.getString("categoria");
                String pagatoDaId = doc.getString("pagatoDaId");
                String scontrinoUrl = doc.getString("scontrinoUrl");

                if (titolo != null && importo != null && pagatoDaId != null) {
                    Partecipante pagatore = partecipanteDao.getPartecipanteById(pagatoDaId);
                    if (pagatore == null) {
                        partecipanteDao.insert(new Partecipante(pagatoDaId, groupId, "Partecipante", null, SyncStatus.SYNCED));
                    }
                    Spesa sp = new Spesa(
                            spesaId, groupId, titolo, importo,
                            valuta != null ? valuta : "EUR",
                            dataSpesa != null ? dataSpesa : System.currentTimeMillis(),
                            categoria != null ? categoria : "Altro",
                            pagatoDaId, scontrinoUrl, SyncStatus.SYNCED
                    );
                    spesaDao.insert(sp);

                    doc.getReference().collection("shares").get().addOnSuccessListener(AppDatabase.databaseWriteExecutor, shareSnaps -> {
                        if (shareSnaps != null && !shareSnaps.isEmpty()) {
                            List<SpesaPartecipante> quoteRemote = new ArrayList<>();
                            for (DocumentSnapshot sDoc : shareSnaps.getDocuments()) {
                                String pId = sDoc.getString("partecipanteId");
                                Double quotaVal = sDoc.getDouble("quota");
                                Double quotaPagataVal = sDoc.getDouble("quotaPagata");
                                double quotaPagata = quotaPagataVal != null ? quotaPagataVal : 0.0;
                                if (pId != null && quotaVal != null) {
                                    Partecipante deb = partecipanteDao.getPartecipanteById(pId);
                                    if (deb == null) {
                                        partecipanteDao.insert(new Partecipante(pId, groupId, "Partecipante", null, SyncStatus.SYNCED));
                                    }
                                    quoteRemote.add(new SpesaPartecipante(spesaId, pId, quotaVal, quotaPagata, SyncStatus.SYNCED));
                                }
                            }
                            spesaDao.insertQuote(quoteRemote);
                        }
                    });
                }
            }
        });
    }
}