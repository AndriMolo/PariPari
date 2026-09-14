package com.example.paripariapp.data.remote;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.paripariapp.data.local.AppDatabase;
import com.example.paripariapp.data.local.PartecipanteDao;
import com.example.paripariapp.data.local.SchedaDao;
import com.example.paripariapp.data.local.SpesaDao;
import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.Scheda;
import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.data.model.SpesaPartecipante;
import com.example.paripariapp.data.model.SyncStatus;
import com.example.paripariapp.util.CodiceInvitoUtil;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestore dedicato per tutte le operazioni remote Cloud Firestore:
 * upload differiti, cancellazioni su cloud, ascoltatori in tempo reale (snapshot listener)
 * e risoluzione dei codici invito per unirsi ai gruppi.
 */
public class FirestoreSyncManager {

    private static final String TAG = "FirestoreSyncManager";

    public interface OnJoinSchedaCallback {
        void onSuccess(String schedaId, String titolo);
        void onError(String errore);
    }

    private final Context context;
    private final SchedaDao schedaDao;
    private final PartecipanteDao partecipanteDao;
    private final SpesaDao spesaDao;

    private final FirebaseFirestore firestore;
    private final FirebaseAuth auth;
    private final NetworkConnectivityMonitor networkMonitor;

    private final List<ListenerRegistration> activeListeners = new ArrayList<>();
    private final Map<String, ListenerRegistration> groupSubListeners = new ConcurrentHashMap<>();

    public FirestoreSyncManager(@NonNull Context context,
                                @NonNull SchedaDao schedaDao,
                                @NonNull PartecipanteDao partecipanteDao,
                                @NonNull SpesaDao spesaDao) {
        this.context = context.getApplicationContext();
        this.schedaDao = schedaDao;
        this.partecipanteDao = partecipanteDao;
        this.spesaDao = spesaDao;

        this.firestore = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();

        this.networkMonitor = new NetworkConnectivityMonitor(this.context, new NetworkConnectivityMonitor.OnNetworkChangeListener() {
            @Override
            public void onNetworkAvailable() {
                Log.i(TAG, "Rete attiva rilevata: avvio sincronizzazione pendente e real-time");
                if (auth.getCurrentUser() != null) {
                    syncPendingData();
                    startRealtimeSync();
                }
            }

            @Override
            public void onNetworkLost() {
                Log.w(TAG, "Rete assente: operatività solo locale (Room)");
            }
        });

        this.networkMonitor.startMonitoring();
    }

    public NetworkConnectivityMonitor getNetworkMonitor() {
        return networkMonitor;
    }

    public boolean isConnected() {
        return networkMonitor.isConnected();
    }

    // ===============================================================
    // UPLOAD DIFFERITI E AGGIORNAMENTI
    // ===============================================================

    public void uploadScheda(Scheda scheda, @Nullable List<Partecipante> partecipanti) {
        if (auth.getCurrentUser() == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("titolo", scheda.getTitolo());
        data.put("descrizione", scheda.getDescrizione());
        data.put("valutaPredefinita", scheda.getValutaPredefinita());
        data.put("creatoreId", auth.getCurrentUser().getUid());
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

    public void uploadPartecipante(Partecipante p) {
        if (auth.getCurrentUser() == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("nome", p.getNome());
        data.put("email", p.getEmail());

        FirebaseUser currentUser = auth.getCurrentUser();
        List<Partecipante> localParts = partecipanteDao.getPartecipantiBySchedaSync(p.getSchedaId());
        String myPartId = Partecipante.findCurrentUserId(localParts, currentUser);
        boolean isMe = Partecipante.isCurrentUserParticipant(p, currentUser) || (myPartId != null && myPartId.equals(p.getId()));

        if (isMe) {
            data.put("userId", currentUser.getUid());
            if (currentUser.getEmail() != null && !currentUser.getEmail().isEmpty()) {
                data.put("email", currentUser.getEmail());
            }
        }

        firestore.collection("groups").document(p.getSchedaId())
                .collection("participants").document(p.getId())
                .set(data)
                .addOnSuccessListener(AppDatabase.databaseWriteExecutor, aVoid -> partecipanteDao.updateSyncStatus(p.getId(), SyncStatus.SYNCED))
                .addOnFailureListener(e -> Log.d(TAG, "Caricamento partecipante differito: " + e.getMessage()));
    }

    public void uploadSpesaConQuote(Spesa spesa, @Nullable List<SpesaPartecipante> quote) {
        if (auth.getCurrentUser() == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("titolo", spesa.getTitolo());
        data.put("importo", spesa.getImporto());
        data.put("valuta", spesa.getValuta());
        data.put("dataSpesa", spesa.getDataSpesa());
        data.put("categoria", spesa.getCategoria());
        data.put("pagatoDaId", spesa.getPagatoDaId());

        WriteBatch batch = firestore.batch();
        DocumentReference spesaRef = firestore.collection("groups").document(spesa.getSchedaId())
                .collection("expenses").document(spesa.getId());
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
                .addOnFailureListener(e -> Log.d(TAG, "Caricamento spesa differito: " + e.getMessage()));
    }

    public void updateTitoloScheda(String schedaId, String nuovoTitolo, long adesso) {
        if (auth.getCurrentUser() != null && networkMonitor.isConnected()) {
            firestore.collection("groups").document(schedaId)
                    .update("titolo", nuovoTitolo, "dataAggiornamento", adesso);
        }
    }

    public void updateNomePartecipante(String partecipanteId, String nuovoNome, String schedaId) {
        if (auth.getCurrentUser() != null && networkMonitor.isConnected() && schedaId != null) {
            firestore.collection("groups").document(schedaId)
                    .collection("participants").document(partecipanteId)
                    .update("nome", nuovoNome);
        }
    }

    // ===============================================================
    // CANCELLAZIONI REMOTE
    // ===============================================================

    public void deleteScheda(String schedaId) {
        detachSubcollectionListeners(schedaId);

        if (auth.getCurrentUser() != null && networkMonitor.isConnected()) {
            FirebaseUser currentUser = auth.getCurrentUser();
            List<Partecipante> localParts = partecipanteDao.getPartecipantiBySchedaSync(schedaId);
            String myPartId = Partecipante.findCurrentUserId(localParts, currentUser);

            firestore.collection("groups").document(schedaId)
                    .collection("participants")
                    .get()
                    .addOnSuccessListener(AppDatabase.databaseWriteExecutor, snapshot -> {
                        if (snapshot == null || snapshot.isEmpty() || snapshot.size() <= 1) {
                            firestore.collection("groups").document(schedaId).delete()
                                    .addOnFailureListener(e -> Log.w(TAG, "Errore eliminazione gruppo remoto: " + e.getMessage()));
                        } else {
                            DocumentSnapshot targetDoc = null;
                            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                                String email = doc.getString("email");
                                String nome = doc.getString("nome");
                                String userId = doc.getString("userId");
                                if ((myPartId != null && myPartId.equals(doc.getId()))
                                        || (userId != null && userId.equals(currentUser.getUid()))
                                        || (currentUser.getEmail() != null && currentUser.getEmail().equalsIgnoreCase(email))
                                        || (nome != null && (nome.trim().equalsIgnoreCase("io") || nome.trim().equalsIgnoreCase("me")))) {
                                    targetDoc = doc;
                                    break;
                                }
                            }

                            if (targetDoc != null) {
                                targetDoc.getReference().delete();
                            }

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
                    .addOnFailureListener(e -> {
                        firestore.collection("groups").document(schedaId).delete();
                    });
        }
    }

    public void deletePartecipante(String partecipanteId, @Nullable String schedaId) {
        if (schedaId != null && auth.getCurrentUser() != null && networkMonitor.isConnected()) {
            firestore.collection("groups").document(schedaId)
                    .collection("participants").document(partecipanteId)
                    .delete();
        }
    }

    public void esciDalGruppo(String schedaId, String partecipanteId) {
        if (auth.getCurrentUser() != null && networkMonitor.isConnected()) {
            DocumentReference pRef = firestore.collection("groups").document(schedaId)
                    .collection("participants").document(partecipanteId);

            pRef.delete().addOnSuccessListener(AppDatabase.databaseWriteExecutor, aVoid -> {
                firestore.collection("groups").document(schedaId).get()
                        .addOnSuccessListener(AppDatabase.databaseWriteExecutor, groupDoc -> {
                            if (groupDoc != null && groupDoc.exists()) {
                                String creatoreId = groupDoc.getString("creatoreId");
                                FirebaseUser currentUser = auth.getCurrentUser();
                                boolean eraCreatore = (creatoreId != null && currentUser != null &&
                                        (creatoreId.equals(currentUser.getUid()) || creatoreId.equals(partecipanteId)));

                                if (eraCreatore) {
                                    firestore.collection("groups").document(schedaId)
                                            .collection("participants").get()
                                            .addOnSuccessListener(AppDatabase.databaseWriteExecutor, pSnaps -> {
                                                if (pSnaps == null || pSnaps.isEmpty()) {
                                                    firestore.collection("groups").document(schedaId).delete();
                                                } else {
                                                    DocumentSnapshot nextP = pSnaps.getDocuments().get(0);
                                                    String newCreatore = nextP.getString("userId");
                                                    if (newCreatore == null || newCreatore.isEmpty()) {
                                                        newCreatore = nextP.getId();
                                                    }
                                                    firestore.collection("groups").document(schedaId)
                                                            .update("creatoreId", newCreatore);
                                                }
                                            });
                                }
                            }
                        });
            });
        }
    }

    public void deleteSpesa(String spesaId, String schedaId) {
        if (auth.getCurrentUser() != null && networkMonitor.isConnected()) {
            firestore.collection("groups").document(schedaId)
                    .collection("expenses").document(spesaId)
                    .delete();
        }
    }

    // ===============================================================
    // SINCRONIZZAZIONE DATI PENDENTI
    // ===============================================================

    public void syncPendingData() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (!networkMonitor.isConnected() || auth.getCurrentUser() == null) return;

            // Schede in attesa
            List<Scheda> schedePendenti = schedaDao.getPendingSyncSchede();
            if (schedePendenti != null) {
                for (Scheda s : schedePendenti) {
                    if (s.getSyncStatus() == SyncStatus.PENDING_DELETE) {
                        firestore.collection("groups").document(s.getId()).delete()
                                .addOnSuccessListener(AppDatabase.databaseWriteExecutor, aVoid -> schedaDao.deleteById(s.getId()));
                    } else {
                        uploadScheda(s, partecipanteDao.getPartecipantiBySchedaSync(s.getId()));
                    }
                }
            }

            // Partecipanti in attesa
            List<Partecipante> partecipantiPendenti = partecipanteDao.getPendingSyncPartecipanti();
            if (partecipantiPendenti != null) {
                for (Partecipante p : partecipantiPendenti) {
                    if (p.getSyncStatus() == SyncStatus.PENDING_DELETE) {
                        firestore.collection("groups").document(p.getSchedaId())
                                .collection("participants").document(p.getId()).delete()
                                .addOnSuccessListener(AppDatabase.databaseWriteExecutor, aVoid -> partecipanteDao.deleteById(p.getId()));
                    } else {
                        uploadPartecipante(p);
                    }
                }
            }

            // Spese in attesa
            List<Spesa> spesePendenti = spesaDao.getPendingSyncSpese();
            if (spesePendenti != null) {
                for (Spesa sp : spesePendenti) {
                    if (sp.getSyncStatus() == SyncStatus.PENDING_DELETE) {
                        firestore.collection("groups").document(sp.getSchedaId())
                                .collection("expenses").document(sp.getId()).delete()
                                .addOnSuccessListener(AppDatabase.databaseWriteExecutor, aVoid -> spesaDao.deleteById(sp.getId()));
                    } else {
                        uploadSpesaConQuote(sp, spesaDao.getQuoteBySpesaSync(sp.getId()));
                    }
                }
            }
        });
    }

    // ===============================================================
    // REAL-TIME SYNC (SNAPSHOT LISTENERS)
    // ===============================================================

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
                                    Log.w(TAG, "Scheda rimossa da remoto: " + groupId);
                                    break;
                            }
                        }
                    });
                });

        activeListeners.add(reg);

        // Aggancia i listener per tutte le schede già presenti localmente
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<Scheda> locali = schedaDao.getAllSchedeSync();
            if (locali != null) {
                for (Scheda s : locali) {
                    attachSubcollectionListeners(s.getId());
                }
            }
        });
    }

    public synchronized void stopRealtimeSync() {
        for (ListenerRegistration reg : activeListeners) {
            reg.remove();
        }
        activeListeners.clear();

        for (ListenerRegistration reg : groupSubListeners.values()) {
            reg.remove();
        }
        groupSubListeners.clear();
    }

    public void attachSubcollectionListeners(String groupId) {
        if (groupId == null || groupSubListeners.containsKey(groupId)) return;

        ListenerRegistration pReg = firestore.collection("groups").document(groupId)
                .collection("participants")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;

                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        for (DocumentChange dc : snapshots.getDocumentChanges()) {
                            DocumentSnapshot doc = dc.getDocument();
                            String pId = doc.getId();

                            switch (dc.getType()) {
                                case ADDED:
                                case MODIFIED:
                                    String nome = doc.getString("nome");
                                    String email = doc.getString("email");
                                    if (nome != null && !nome.trim().isEmpty()) {
                                        partecipanteDao.insert(new Partecipante(pId, groupId, nome, email, SyncStatus.SYNCED));
                                    }
                                    break;
                                case REMOVED:
                                    partecipanteDao.deleteById(pId);
                                    break;
                            }
                        }
                    });
                });

        ListenerRegistration eReg = firestore.collection("groups").document(groupId)
                .collection("expenses")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;

                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        for (DocumentChange dc : snapshots.getDocumentChanges()) {
                            DocumentSnapshot doc = dc.getDocument();
                            String eId = doc.getId();

                            switch (dc.getType()) {
                                case ADDED:
                                case MODIFIED:
                                    String titolo = doc.getString("titolo");
                                    Double importo = doc.getDouble("importo");
                                    String valuta = doc.getString("valuta");
                                    Long dataSpesa = doc.getLong("dataSpesa");
                                    String categoria = doc.getString("categoria");
                                    String pagatoDaId = doc.getString("pagatoDaId");

                                    if (titolo != null && importo != null) {
                                        Spesa spesa = new Spesa(
                                                eId,
                                                groupId,
                                                titolo,
                                                importo,
                                                valuta != null ? valuta : "EUR",
                                                dataSpesa != null ? dataSpesa : System.currentTimeMillis(),
                                                categoria != null ? categoria : "Generale",
                                                pagatoDaId != null ? pagatoDaId : "",
                                                null,
                                                SyncStatus.SYNCED
                                        );
                                        spesaDao.insert(spesa);

                                        doc.getReference().collection("shares").get()
                                                .addOnSuccessListener(AppDatabase.databaseWriteExecutor, shareSnaps -> {
                                                    if (shareSnaps != null) {
                                                        List<SpesaPartecipante> quote = new ArrayList<>();
                                                        for (DocumentSnapshot sDoc : shareSnaps.getDocuments()) {
                                                            Double quota = sDoc.getDouble("quota");
                                                            Double quotaPagata = sDoc.getDouble("quotaPagata");
                                                            if (quota != null) {
                                                                quote.add(new SpesaPartecipante(
                                                                        eId,
                                                                        sDoc.getId(),
                                                                        quota,
                                                                        quotaPagata != null ? quotaPagata : 0.0,
                                                                        SyncStatus.SYNCED
                                                                ));
                                                            }
                                                        }
                                                        if (!quote.isEmpty()) {
                                                            spesaDao.insertQuote(quote);
                                                        }
                                                    }
                                                });
                                    }
                                    break;
                                case REMOVED:
                                    spesaDao.deleteById(eId);
                                    break;
                            }
                        }
                    });
                });

        groupSubListeners.put(groupId, pReg);
        groupSubListeners.put(groupId + "_expenses", eReg);
        attachGroupDocListener(groupId);
    }

    public void detachSubcollectionListeners(String groupId) {
        if (groupId == null) return;
        ListenerRegistration pReg = groupSubListeners.remove(groupId);
        if (pReg != null) pReg.remove();

        ListenerRegistration eReg = groupSubListeners.remove(groupId + "_expenses");
        if (eReg != null) eReg.remove();

        ListenerRegistration gReg = groupSubListeners.remove(groupId + "_doc");
        if (gReg != null) gReg.remove();
    }

    private void attachGroupDocListener(String groupId) {
        if (groupId == null || groupSubListeners.containsKey(groupId + "_doc")) return;

        ListenerRegistration gReg = firestore.collection("groups").document(groupId)
                .addSnapshotListener((doc, error) -> {
                    if (error != null || doc == null || !doc.exists()) return;

                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        String titolo = doc.getString("titolo");
                        String desc = doc.getString("descrizione");
                        String valuta = doc.getString("valutaPredefinita");
                        Long dataAgg = doc.getLong("dataAggiornamento");
                        String codInvito = doc.getString("codiceInvito");

                        if (titolo != null) {
                            Scheda s = schedaDao.getSchedaById(groupId);
                            if (s != null) {
                                boolean modificata = false;
                                if (!titolo.equals(s.getTitolo())) {
                                    s.setTitolo(titolo);
                                    modificata = true;
                                }
                                if (desc != null && !desc.equals(s.getDescrizione())) {
                                    s.setDescrizione(desc);
                                    modificata = true;
                                }
                                if (valuta != null && !valuta.equals(s.getValutaPredefinita())) {
                                    s.setValutaPredefinita(valuta);
                                    modificata = true;
                                }
                                if (codInvito != null && !codInvito.equals(s.getCodiceInvito())) {
                                    s.setCodiceInvito(codInvito);
                                    modificata = true;
                                }
                                if (modificata) {
                                    s.setDataAggiornamento(dataAgg != null ? dataAgg : System.currentTimeMillis());
                                    s.setSyncStatus(SyncStatus.SYNCED);
                                    schedaDao.insert(s);
                                }
                            }
                        }
                    });
                });

        groupSubListeners.put(groupId + "_doc", gReg);
    }

    // ===============================================================
    // CODICI INVITO & JOIN GRUPPO
    // ===============================================================

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

    public void uniscitiASchedaTramiteCodice(String codice, @Nullable String nomePersonalizzato, OnJoinSchedaCallback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        String cleanCode = CodiceInvitoUtil.normalizzaCodice(codice);

        if (cleanCode == null || cleanCode.length() != 6) {
            mainHandler.post(() -> callback.onError("Formato codice non valido (deve essere di 6 caratteri alfanumerici)"));
            return;
        }

        AppDatabase.databaseWriteExecutor.execute(() -> {
            Scheda locale = schedaDao.getSchedaByCodiceInvito(cleanCode);
            if (locale != null) {
                mainHandler.post(() -> callback.onSuccess(locale.getId(), locale.getTitolo()));
                return;
            }

            if (!networkMonitor.isConnected() || auth.getCurrentUser() == null) {
                mainHandler.post(() -> callback.onError("Connessione a internet necessaria per cercare il gruppo"));
                return;
            }

            firestore.collection("groups").whereEqualTo("codiceInvito", cleanCode).limit(1).get()
                    .addOnSuccessListener(AppDatabase.databaseWriteExecutor, querySnapshot -> {
                        if (querySnapshot != null && !querySnapshot.isEmpty()) {
                            elaboraJoinGruppo(querySnapshot.getDocuments().get(0), cleanCode, nomePersonalizzato, callback, mainHandler);
                        } else {
                            firestore.collection("groups").document(cleanCode).get()
                                    .addOnSuccessListener(AppDatabase.databaseWriteExecutor, docSnapshot -> {
                                        if (docSnapshot != null && docSnapshot.exists()) {
                                            elaboraJoinGruppo(docSnapshot, cleanCode, nomePersonalizzato, callback, mainHandler);
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

    private void elaboraJoinGruppo(DocumentSnapshot groupDoc, String cleanCode, @Nullable String nomePersonalizzato,
                                  OnJoinSchedaCallback callback, Handler mainHandler) {
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
        String currentNome = (nomePersonalizzato != null && !nomePersonalizzato.trim().isEmpty())
                ? nomePersonalizzato.trim()
                : ((currentUser != null && currentUser.getDisplayName() != null && !currentUser.getDisplayName().trim().isEmpty())
                ? currentUser.getDisplayName() : "Io");

        groupDoc.getReference().collection("participants").get()
                .addOnSuccessListener(AppDatabase.databaseWriteExecutor, pSnaps -> {
                    boolean giaPresente = false;
                    DocumentSnapshot existingDocToUpdate = null;
                    List<Partecipante> partiScaricati = new ArrayList<>();

                    if (pSnaps != null) {
                        for (DocumentSnapshot pDoc : pSnaps.getDocuments()) {
                            String pNome = pDoc.getString("nome");
                            String pEmail = pDoc.getString("email");
                            String pUserId = pDoc.getString("userId");

                            boolean isMatch = (currentUid != null && currentUid.equals(pUserId))
                                    || (currentEmail != null && currentEmail.equalsIgnoreCase(pEmail));

                            if (isMatch) {
                                giaPresente = true;
                                if (currentUid != null && pUserId == null) {
                                    existingDocToUpdate = pDoc;
                                }
                            }

                            if (pNome != null) {
                                partiScaricati.add(new Partecipante(pDoc.getId(), groupId, pNome, pEmail, SyncStatus.SYNCED));
                            }
                        }
                    }

                    if (!giaPresente) {
                        String mioPartId = UUID.randomUUID().toString();
                        Partecipante mioPartecipante = new Partecipante(mioPartId, groupId, currentNome, currentEmail, SyncStatus.SYNCED);
                        partiScaricati.add(mioPartecipante);

                        Map<String, Object> myData = new HashMap<>();
                        myData.put("nome", currentNome);
                        myData.put("email", currentEmail);
                        myData.put("userId", currentUid);

                        groupDoc.getReference().collection("participants").document(mioPartId)
                                .set(myData);
                    } else if (existingDocToUpdate != null) {
                        Map<String, Object> patch = new HashMap<>();
                        patch.put("userId", currentUid);
                        if (currentEmail != null) patch.put("email", currentEmail);
                        existingDocToUpdate.getReference().update(patch);
                    }

                    partecipanteDao.insertAll(partiScaricati);

                    groupDoc.getReference().collection("expenses").get()
                            .addOnSuccessListener(AppDatabase.databaseWriteExecutor, expSnaps -> {
                                if (expSnaps != null) {
                                    List<Spesa> speseScaricate = new ArrayList<>();
                                    for (DocumentSnapshot eDoc : expSnaps.getDocuments()) {
                                        String eTit = eDoc.getString("titolo");
                                        Double eImp = eDoc.getDouble("importo");
                                        String eVal = eDoc.getString("valuta");
                                        Long eData = eDoc.getLong("dataSpesa");
                                        String eCat = eDoc.getString("categoria");
                                        String ePagato = eDoc.getString("pagatoDaId");

                                        if (eTit != null && eImp != null) {
                                            speseScaricate.add(new Spesa(
                                                    eDoc.getId(),
                                                    groupId,
                                                    eTit,
                                                    eImp,
                                                    eVal != null ? eVal : "EUR",
                                                    eData != null ? eData : System.currentTimeMillis(),
                                                    eCat != null ? eCat : "Generale",
                                                    ePagato != null ? ePagato : "",
                                                    null,
                                                    SyncStatus.SYNCED
                                            ));

                                            eDoc.getReference().collection("shares").get()
                                                    .addOnSuccessListener(AppDatabase.databaseWriteExecutor, sSnaps -> {
                                                        if (sSnaps != null) {
                                                            List<SpesaPartecipante> quote = new ArrayList<>();
                                                            for (DocumentSnapshot sDoc : sSnaps.getDocuments()) {
                                                                Double qVal = sDoc.getDouble("quota");
                                                                Double qPag = sDoc.getDouble("quotaPagata");
                                                                if (qVal != null) {
                                                                    quote.add(new SpesaPartecipante(
                                                                            eDoc.getId(),
                                                                            sDoc.getId(),
                                                                            qVal,
                                                                            qPag != null ? qPag : 0.0,
                                                                            SyncStatus.SYNCED
                                                                    ));
                                                                }
                                                            }
                                                            if (!quote.isEmpty()) {
                                                                spesaDao.insertQuote(quote);
                                                            }
                                                        }
                                                    });
                                        }
                                    }
                                    spesaDao.insertAll(speseScaricate);
                                }

                                attachSubcollectionListeners(groupId);

                                String finalTitolo = (titolo != null) ? titolo : "Gruppo";
                                mainHandler.post(() -> callback.onSuccess(groupId, finalTitolo));
                            })
                            .addOnFailureListener(e -> {
                                attachSubcollectionListeners(groupId);
                                String finalTitolo = (titolo != null) ? titolo : "Gruppo";
                                mainHandler.post(() -> callback.onSuccess(groupId, finalTitolo));
                            });
                })
                .addOnFailureListener(e ->
                        mainHandler.post(() -> callback.onError("Errore durante l'accesso ai partecipanti: " + e.getLocalizedMessage()))
                );
    }
}
