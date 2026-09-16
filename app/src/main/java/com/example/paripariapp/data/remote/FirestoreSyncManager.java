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
import com.example.paripariapp.data.model.MembroGruppoPreview;
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
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
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

    public static class GruppoPreview {
        private final String groupId;
        private final String titolo;
        private final String descrizione;
        private final String valutaPredefinita;
        private final List<MembroGruppoPreview> membri;

        public GruppoPreview(String groupId, String titolo, String descrizione, String valutaPredefinita, List<MembroGruppoPreview> membri) {
            this.groupId = groupId;
            this.titolo = titolo;
            this.descrizione = descrizione;
            this.valutaPredefinita = valutaPredefinita;
            this.membri = membri != null ? membri : new ArrayList<>();
        }

        public String getGroupId() { return groupId; }
        public String getTitolo() { return titolo; }
        public String getDescrizione() { return descrizione; }
        public String getValutaPredefinita() { return valutaPredefinita; }
        public List<MembroGruppoPreview> getMembri() { return membri; }

        public List<MembroGruppoPreview> getMembriSostituibili() {
            List<MembroGruppoPreview> sostituibili = new ArrayList<>();
            for (MembroGruppoPreview m : membri) {
                if (m.isSostituibile()) {
                    sostituibili.add(m);
                }
            }
            return sostituibili;
        }

        public List<Partecipante> getPartecipantiDisponibili() {
            List<Partecipante> list = new ArrayList<>();
            for (MembroGruppoPreview m : getMembriSostituibili()) {
                list.add(new Partecipante(m.getId(), m.getSchedaId(), m.getNome(), m.getEmail(), SyncStatus.SYNCED));
            }
            return list;
        }
    }

    public interface OnPreviewGruppoCallback {
        void onPreviewLoaded(GruppoPreview preview);
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
        if (scheda.getIconaUrl() != null) {
            data.put("iconaUrl", scheda.getIconaUrl());
        }

        List<String> membriUids = new ArrayList<>();
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            membriUids.add(currentUser.getUid());
        }
        if (partecipanti != null) {
            for (Partecipante p : partecipanti) {
                if (p.getUserId() != null && !p.getUserId().trim().isEmpty() && !membriUids.contains(p.getUserId())) {
                    membriUids.add(p.getUserId());
                }
            }
        }
        data.put("membriUids", membriUids);

        WriteBatch batch = firestore.batch();
        DocumentReference ref = firestore.collection("groups").document(scheda.getId());
        batch.set(ref, data);

        if (partecipanti != null) {
            for (Partecipante p : partecipanti) {
                DocumentReference pRef = ref.collection("participants").document(p.getId());
                boolean isCreator = (currentUser != null && Partecipante.isCurrentUserParticipant(p, currentUser))
                        || (scheda.getCreatoreId() != null && scheda.getCreatoreId().equals(p.getId()));
                batch.set(pRef, creaMappaPartecipante(p, isCreator, currentUser));
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

    /**
     * Costruisce la mappa dati per Firestore di un partecipante, garantendo coerenza
     * tra uploadScheda, migraSchedaAdAccount e uploadPartecipante senza duplicazione di codice.
     */
    private Map<String, Object> creaMappaPartecipante(@NonNull Partecipante p, boolean isCurrentUser, @Nullable FirebaseUser currentUser) {
        Map<String, Object> data = new HashMap<>();
        data.put("nome", p.getNome());
        if (p.getEmail() != null) {
            data.put("email", p.getEmail());
        }

        if (isCurrentUser && currentUser != null) {
            data.put("userId", currentUser.getUid());
            data.put("isAutenticato", !currentUser.isAnonymous());
            if (currentUser.getEmail() != null && !currentUser.getEmail().trim().isEmpty()) {
                data.put("email", currentUser.getEmail().trim());
            }
        } else {
            if (p.getUserId() != null) {
                data.put("userId", p.getUserId());
            }
            data.put("isAutenticato", p.getUserId() != null && !p.getUserId().trim().isEmpty());
        }

        if (p.getPreviousUserId() != null) {
            data.put("previousUserId", p.getPreviousUserId());
        }
        if (p.getStato() != null) {
            data.put("stato", p.getStato());
        }
        if (p.getPaypalHandle() != null) {
            data.put("paypalHandle", p.getPaypalHandle());
        }
        if (p.getRevolutHandle() != null) {
            data.put("revolutHandle", p.getRevolutHandle());
        }
        if (p.getPhotoUrl() != null) {
            data.put("photoUrl", p.getPhotoUrl());
        }
        return data;
    }

    /**
     * Sincronizza e migra una scheda e i suoi partecipanti all'account autenticato corrente.
     * Garantisce che creatoreId e membriUids su Firestore siano aggiornati correttamente,
     * consentendo a qualsiasi altro dispositivo dell'utente di scaricare e visualizzare il gruppo.
     */
    public void migraSchedaAdAccount(@NonNull Scheda scheda, @NonNull Partecipante myPart, @NonNull FirebaseUser currentUser) {
        if (!networkMonitor.isConnected()) return;

        DocumentReference gRef = firestore.collection("groups").document(scheda.getId());
        gRef.get().addOnSuccessListener(AppDatabase.databaseWriteExecutor, groupDoc -> {
            List<Partecipante> localParts = partecipanteDao.getPartecipantiBySchedaSync(scheda.getId());
            List<String> membriUids = new ArrayList<>();
            membriUids.add(currentUser.getUid());
            if (localParts != null) {
                for (Partecipante p : localParts) {
                    if (p.getUserId() != null && !p.getUserId().trim().isEmpty() && !membriUids.contains(p.getUserId())) {
                        membriUids.add(p.getUserId());
                    }
                }
            }

            boolean isCreator = (scheda.getCreatoreId() == null || scheda.getCreatoreId().isEmpty()
                    || scheda.getCreatoreId().equals(myPart.getId())
                    || (myPart.getUserId() != null && myPart.getUserId().equals(scheda.getCreatoreId()))
                    || currentUser.getUid().equals(scheda.getCreatoreId())
                    || (localParts != null && !localParts.isEmpty() && localParts.get(0).getId().equals(myPart.getId())));

            Map<String, Object> gData = new HashMap<>();
            gData.put("titolo", scheda.getTitolo());
            gData.put("descrizione", scheda.getDescrizione() != null ? scheda.getDescrizione() : "");
            gData.put("valutaPredefinita", scheda.getValutaPredefinita() != null ? scheda.getValutaPredefinita() : "EUR");
            gData.put("dataAggiornamento", System.currentTimeMillis());
            if (scheda.getCodiceInvito() != null) {
                gData.put("codiceInvito", scheda.getCodiceInvito());
            }
            if (scheda.getIconaUrl() != null) {
                gData.put("iconaUrl", scheda.getIconaUrl());
            }
            gData.put("membriUids", membriUids);

            if (!groupDoc.exists() || isCreator) {
                gData.put("creatoreId", currentUser.getUid());
            } else {
                String remCreator = groupDoc.getString("creatoreId");
                if (remCreator == null || remCreator.isEmpty()) {
                    gData.put("creatoreId", currentUser.getUid());
                }
            }
            if (!groupDoc.exists()) {
                gData.put("dataCreazione", scheda.getDataCreazione());
            }

            WriteBatch batch = firestore.batch();
            batch.set(gRef, gData, SetOptions.merge());

            if (localParts != null) {
                for (Partecipante p : localParts) {
                    DocumentReference pRef = gRef.collection("participants").document(p.getId());
                    boolean isMe = p.getId().equals(myPart.getId());
                    batch.set(pRef, creaMappaPartecipante(p, isMe, currentUser), SetOptions.merge());
                }
            }

            batch.commit().addOnSuccessListener(AppDatabase.databaseWriteExecutor, aVoid -> {
                Log.i(TAG, "Gruppo " + scheda.getId() + " migrato/sincronizzato con successo per l'utente " + currentUser.getUid());
                schedaDao.updateSyncStatus(scheda.getId(), SyncStatus.SYNCED);
                if (localParts != null) {
                    for (Partecipante p : localParts) {
                        partecipanteDao.updateSyncStatus(p.getId(), SyncStatus.SYNCED);
                    }
                }
                // Assicura che anche tutte le spese e quote locali siano caricate su Firestore
                List<Spesa> speseLocali = spesaDao.getSpeseBySchedaSync(scheda.getId());
                if (speseLocali != null) {
                    for (Spesa s : speseLocali) {
                        uploadSpesaConQuote(s, spesaDao.getQuoteBySpesaSync(s.getId()));
                    }
                }
            }).addOnFailureListener(e -> Log.w(TAG, "Errore commit migrazione scheda: " + e.getMessage()));
        }).addOnFailureListener(e -> Log.w(TAG, "Errore lettura scheda per migrazione: " + e.getMessage()));
    }

    /**
     * Scorre tutte le schede locali in Room ed esegue la migrazione/allineamento
     * di ciascuna verso l'account autenticato specificato.
     */
    public void migraTuttiIGruppiLocali(@NonNull FirebaseUser currentUser) {
        if (currentUser.isAnonymous()) return;

        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<Scheda> schede = schedaDao.getAllSchedeSync();
            if (schede == null || schede.isEmpty()) return;

            com.example.paripariapp.data.repository.UserPreferencesRepository prefs =
                    com.example.paripariapp.data.repository.UserPreferencesRepository.getInstance(context);

            for (Scheda s : schede) {
                List<Partecipante> partecipanti = partecipanteDao.getPartecipantiBySchedaSync(s.getId());
                if (partecipanti == null || partecipanti.isEmpty()) continue;

                Partecipante myPart = null;
                String savedPartId = prefs.getMyParticipantId(s.getId());
                if (savedPartId != null) {
                    for (Partecipante p : partecipanti) {
                        if (p.getId().equals(savedPartId)) {
                            myPart = p;
                            break;
                        }
                    }
                }
                if (myPart == null) {
                    String foundId = Partecipante.findCurrentUserId(partecipanti, currentUser, prefs, s.getId());
                    if (foundId != null) {
                        for (Partecipante p : partecipanti) {
                            if (p.getId().equals(foundId)) {
                                myPart = p;
                                break;
                            }
                        }
                    }
                }
                if (myPart == null && s.getCreatoreId() != null) {
                    for (Partecipante p : partecipanti) {
                        if (p.getId().equals(s.getCreatoreId())) {
                            myPart = p;
                            break;
                        }
                    }
                }
                if (myPart == null) {
                    myPart = partecipanti.get(0);
                }

                boolean partModificato = false;
                if (myPart.getUserId() == null || !myPart.getUserId().equals(currentUser.getUid())) {
                    myPart.setUserId(currentUser.getUid());
                    myPart.setSyncStatus(SyncStatus.SYNCED);
                    partModificato = true;
                }
                if (currentUser.getEmail() != null && !currentUser.getEmail().trim().isEmpty()
                        && (myPart.getEmail() == null || !myPart.getEmail().equalsIgnoreCase(currentUser.getEmail().trim()))) {
                    myPart.setEmail(currentUser.getEmail().trim());
                    partModificato = true;
                }

                if (partModificato) {
                    partecipanteDao.insert(myPart);
                }
                prefs.setMyParticipantId(s.getId(), myPart.getId());

                if (s.getCreatoreId() == null || s.getCreatoreId().isEmpty() || s.getCreatoreId().equals(myPart.getId())) {
                    s.setCreatoreId(myPart.getId());
                    schedaDao.update(s);
                }

                if (isConnected()) {
                    migraSchedaAdAccount(s, myPart, currentUser);
                }
            }
        });
    }

    public void uploadPartecipante(Partecipante p) {
        if (auth.getCurrentUser() == null) return;

        FirebaseUser currentUser = auth.getCurrentUser();
        List<Partecipante> localParts = partecipanteDao.getPartecipantiBySchedaSync(p.getSchedaId());
        com.example.paripariapp.data.repository.UserPreferencesRepository prefs =
                com.example.paripariapp.data.repository.UserPreferencesRepository.getInstance(context);
        String myPartId = Partecipante.findCurrentUserId(localParts, currentUser, prefs, p.getSchedaId());
        boolean isMe = (myPartId != null && myPartId.equals(p.getId())) || Partecipante.isCurrentUserParticipant(p, currentUser);

        firestore.collection("groups").document(p.getSchedaId())
                .collection("participants").document(p.getId())
                .set(creaMappaPartecipante(p, isMe, currentUser), SetOptions.merge())
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

        String scontrinoUrl = spesa.getScontrinoUrl();
        if (scontrinoUrl != null && (scontrinoUrl.startsWith("http://") || scontrinoUrl.startsWith("https://"))) {
            data.put("scontrinoUrl", scontrinoUrl);
        } else {
            data.put("scontrinoUrl", null);
        }

        String scontrinoJson = spesa.getScontrinoJson();
        if (scontrinoJson != null && !scontrinoJson.trim().isEmpty()) {
            data.put("scontrinoJson", scontrinoJson);
        } else {
            data.put("scontrinoJson", null);
        }

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
        deletePartecipanteDefinitivamente(schedaId, partecipanteId);
    }

    public void deletePartecipanteDefinitivamente(@Nullable String schedaId, String partecipanteId) {
        if (schedaId != null && partecipanteId != null) {
            firestore.collection("groups").document(schedaId)
                    .collection("participants").document(partecipanteId)
                    .delete();
        }
    }

    public void disattivaMembroLocale(String schedaId, String partecipanteId) {
        if (schedaId != null && partecipanteId != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("stato", Partecipante.STATO_ARCHIVIATO);
            updates.put("userId", com.google.firebase.firestore.FieldValue.delete());

            firestore.collection("groups").document(schedaId)
                    .collection("participants").document(partecipanteId)
                    .update(updates);
        }
    }

    public void eliminaGruppoDefinitivamente(String schedaId) {
        if (schedaId == null) return;
        detachSubcollectionListeners(schedaId);
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().unsubscribeFromTopic("group_" + schedaId);
        } catch (Exception ignored) {}

        DocumentReference gRef = firestore.collection("groups").document(schedaId);

        gRef.collection("participants").get().addOnSuccessListener(AppDatabase.databaseWriteExecutor, pSnaps -> {
            if (pSnaps != null) {
                WriteBatch batch = firestore.batch();
                for (DocumentSnapshot doc : pSnaps.getDocuments()) {
                    batch.delete(doc.getReference());
                }
                batch.commit();
            }
        });

        gRef.collection("expenses").get().addOnSuccessListener(AppDatabase.databaseWriteExecutor, eSnaps -> {
            if (eSnaps != null) {
                WriteBatch batch = firestore.batch();
                for (DocumentSnapshot doc : eSnaps.getDocuments()) {
                    batch.delete(doc.getReference());
                }
                batch.commit();
            }
        });

        gRef.delete().addOnFailureListener(e -> Log.w(TAG, "Eliminazione gruppo remoto fallita", e));
    }

    public void esciDalGruppo(String schedaId, String partecipanteId) {
        if (auth.getCurrentUser() != null && networkMonitor.isConnected()) {
            DocumentReference pRef = firestore.collection("groups").document(schedaId)
                    .collection("participants").document(partecipanteId);

            Map<String, Object> updates = new HashMap<>();
            updates.put("stato", Partecipante.STATO_USCITO);
            updates.put("previousUserId", auth.getCurrentUser().getUid());
            updates.put("userId", com.google.firebase.firestore.FieldValue.delete());

            firestore.collection("groups").document(schedaId)
                    .update("membriUids", FieldValue.arrayRemove(auth.getCurrentUser().getUid()))
                    .addOnFailureListener(e -> Log.d(TAG, "Rimozione da membriUids non bloccante: " + e.getMessage()));

            pRef.update(updates).addOnSuccessListener(AppDatabase.databaseWriteExecutor, aVoid -> {
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
                                                String nuovoCreatore = null;
                                                if (pSnaps != null && !pSnaps.isEmpty()) {
                                                    for (DocumentSnapshot doc : pSnaps.getDocuments()) {
                                                        String uId = doc.getString("userId");
                                                        String st = doc.getString("stato");
                                                        if (uId != null && !uId.isEmpty() && !doc.getId().equals(partecipanteId) && !"USCITO".equalsIgnoreCase(st)) {
                                                            nuovoCreatore = uId;
                                                            break;
                                                        }
                                                    }
                                                }

                                                if (nuovoCreatore != null) {
                                                    firestore.collection("groups").document(schedaId)
                                                            .update("creatoreId", nuovoCreatore);
                                                } else {
                                                    // Nessun altro utente reale autenticato presente: cancella l'intero gruppo orfano da Firestore!
                                                    eliminaGruppoDefinitivamente(schedaId);
                                                }
                                            });
                                }
                            }
                        });
            });
        }
    }

    public void deleteSpesa(String spesaId, String schedaId) {
        if (schedaId != null && spesaId != null && auth.getCurrentUser() != null) {
            firestore.collection("groups").document(schedaId)
                    .collection("expenses").document(spesaId)
                    .delete()
                    .addOnFailureListener(e -> Log.w(TAG, "Eliminazione spesa remota differita: " + e.getMessage()));
        }
    }

    public void syncPendingData() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (!networkMonitor.isConnected() || auth.getCurrentUser() == null) return;

            FirebaseUser cu = auth.getCurrentUser();
            if (cu != null && !cu.isAnonymous()) {
                migraTuttiIGruppiLocali(cu);
            }

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

    private void processGroupSnapshots(QuerySnapshot snapshots) {
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
                        String iconaUrlAdd = doc.getString("iconaUrl");

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
                                if (iconaUrlAdd != null) {
                                    nuovaScheda.setIconaUrl(iconaUrlAdd);
                                }
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
                                if (iconaUrlAdd != null && !iconaUrlAdd.equals(schedaEsistente.getIconaUrl())) {
                                    schedaEsistente.setIconaUrl(iconaUrlAdd);
                                    schedaDao.update(schedaEsistente);
                                }
                            }
                            attachSubcollectionListeners(groupId);
                        }
                        break;

                    case MODIFIED:
                        String titoloMod = doc.getString("titolo");
                        Long dataAggMod = doc.getLong("dataAggiornamento");
                        String codInvitoMod = doc.getString("codiceInvito");
                        String iconaUrlMod = doc.getString("iconaUrl");
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
                        if (iconaUrlMod != null) {
                            Scheda sMod = schedaDao.getSchedaById(groupId);
                            if (sMod != null && !iconaUrlMod.equals(sMod.getIconaUrl())) {
                                sMod.setIconaUrl(iconaUrlMod);
                                schedaDao.update(sMod);
                            }
                        }
                        break;

                    case REMOVED:
                        Log.w(TAG, "Scheda rimossa da remoto: " + groupId);
                        break;
                }
            }
        });
    }

    public synchronized void startRealtimeSync() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) return;

        stopRealtimeSync();

        // 1. Ascolta gruppi creati da currentUser
        ListenerRegistration regCreator = firestore.collection("groups")
                .whereEqualTo("creatoreId", currentUser.getUid())
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;
                    processGroupSnapshots(snapshots);
                });
        activeListeners.add(regCreator);

        // 2. Ascolta gruppi di cui currentUser è membro (membriUids array)
        ListenerRegistration regMembri = firestore.collection("groups")
                .whereArrayContains("membriUids", currentUser.getUid())
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;
                    processGroupSnapshots(snapshots);
                });
        activeListeners.add(regMembri);

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
                                    String pUserId = doc.getString("userId");
                                    String pPreviousUserId = doc.getString("previousUserId");
                                    String pStato = doc.getString("stato");
                                    String paypal = doc.getString("paypalHandle");
                                    String revolut = doc.getString("revolutHandle");
                                    String photoUrl = doc.getString("photoUrl");

                                    if (nome != null && !nome.trim().isEmpty()) {
                                        Partecipante p = new Partecipante(pId, groupId, nome, email, SyncStatus.SYNCED);
                                        p.setUserId(pUserId);
                                        p.setPreviousUserId(pPreviousUserId);
                                        if (pStato != null) p.setStato(pStato);
                                        p.setPaypalHandle(paypal);
                                        p.setRevolutHandle(revolut);
                                        p.setPhotoUrl(photoUrl);
                                        partecipanteDao.insert(p);
                                    }
                                    if (pUserId != null && !pUserId.trim().isEmpty()) {
                                        FirebaseUser cu = auth.getCurrentUser();
                                        if (cu != null && pUserId.equals(cu.getUid())) {
                                            com.example.paripariapp.data.repository.UserPreferencesRepository.getInstance(context)
                                                    .setMyParticipantId(groupId, pId);
                                        }
                                        Scheda s = schedaDao.getSchedaById(groupId);
                                        if (s != null && (pUserId.equals(s.getCreatoreId()) || (cu != null && cu.getUid().equals(s.getCreatoreId())))) {
                                            schedaDao.updateCreatoreId(groupId, pId);
                                        }
                                    }
                                    break;
                                case REMOVED:
                                    // Manteniamo il record in Room per evitare che le spese passate mostrino "unknown"
                                    break;
                            }
                        }
                    });
                });

        final boolean[] isInitialBatch = new boolean[]{true};

        ListenerRegistration eReg = firestore.collection("groups").document(groupId)
                .collection("expenses")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;

                    final boolean isFirstBatch = isInitialBatch[0];
                    isInitialBatch[0] = false;

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
                                    String scontrinoUrl = doc.getString("scontrinoUrl");
                                    String scontrinoJson = doc.getString("scontrinoJson");

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
                                                scontrinoUrl,
                                                SyncStatus.SYNCED
                                        );
                                        spesa.setScontrinoJson(scontrinoJson);
                                        spesaDao.insert(spesa);

                                        final List<SpesaPartecipante> quoteInMem = new ArrayList<>();
                                        doc.getReference().collection("shares").get()
                                                .addOnSuccessListener(AppDatabase.databaseWriteExecutor, shareSnaps -> {
                                                    if (shareSnaps != null) {
                                                        for (DocumentSnapshot sDoc : shareSnaps.getDocuments()) {
                                                            Double quota = sDoc.getDouble("quota");
                                                            Double quotaPagata = sDoc.getDouble("quotaPagata");
                                                            if (quota != null) {
                                                                quoteInMem.add(new SpesaPartecipante(
                                                                        eId,
                                                                        sDoc.getId(),
                                                                        quota,
                                                                        quotaPagata != null ? quotaPagata : 0.0,
                                                                        SyncStatus.SYNCED
                                                                ));
                                                            }
                                                        }
                                                        if (!quoteInMem.isEmpty()) {
                                                            spesaDao.deleteQuoteBySpesaId(eId);
                                                            spesaDao.insertQuote(quoteInMem);
                                                        }
                                                    }

                                                    // Notifica NATIVA in Java per gli altri membri SOLO se il cambio avviene in tempo reale
                                                    if (!isFirstBatch && !doc.getMetadata().hasPendingWrites()) {
                                                        Scheda s = schedaDao.getSchedaById(groupId);
                                                        String nomeGruppo = (s != null && s.getTitolo() != null) ? s.getTitolo() : "Gruppo";

                                                        Partecipante pPagante = (pagatoDaId != null && !pagatoDaId.isEmpty()) ? partecipanteDao.getPartecipanteById(pagatoDaId) : null;
                                                        String nomePagatore = (pPagante != null && pPagante.getNome() != null) ? pPagante.getNome() : "Un partecipante";

                                                        boolean isRimborso = com.example.paripariapp.util.CategoriaUtil.isCategoriaSaldi(categoria);
                                                        String valutaStr = valuta != null ? valuta : "EUR";
                                                        String importoFmt = String.format(java.util.Locale.getDefault(), "%.2f %s", importo, valutaStr);

                                                        String notifTitolo;
                                                        String notifMessaggio;

                                                        if (isRimborso) {
                                                            if (dc.getType() == DocumentChange.Type.ADDED) {
                                                                notifTitolo = "Pagamento saldato in \"" + nomeGruppo + "\"";
                                                            } else {
                                                                notifTitolo = "Rimborso modificato in \"" + nomeGruppo + "\"";
                                                            }
                                                            notifMessaggio = importoFmt + " Da " + nomePagatore + " Pagati.";
                                                        } else {
                                                            if (dc.getType() == DocumentChange.Type.ADDED) {
                                                                notifTitolo = "Nuova spesa in \"" + nomeGruppo + "\"";
                                                            } else {
                                                                notifTitolo = "Spesa modificata in \"" + nomeGruppo + "\"";
                                                            }

                                                            List<Partecipante> partGroup = partecipanteDao.getPartecipantiBySchedaSync(groupId);
                                                            FirebaseUser currentUser = auth.getCurrentUser();
                                                            com.example.paripariapp.data.repository.UserPreferencesRepository prefs =
                                                                    com.example.paripariapp.data.repository.UserPreferencesRepository.getInstance(context);
                                                            String myPartId = Partecipante.findCurrentUserId(partGroup, currentUser, prefs, groupId);

                                                            double miaQuota = 0.0;
                                                            if (myPartId != null && !quoteInMem.isEmpty()) {
                                                                for (SpesaPartecipante q : quoteInMem) {
                                                                    if (q.getPartecipanteId().equals(myPartId)) {
                                                                        miaQuota = q.getQuota();
                                                                        break;
                                                                    }
                                                                }
                                                            }
                                                            if (miaQuota <= 0.001 && !quoteInMem.isEmpty()) {
                                                                miaQuota = importo / quoteInMem.size();
                                                            }

                                                            String quotaFmt = String.format(java.util.Locale.getDefault(), "%.2f %s", miaQuota, valutaStr);
                                                            notifMessaggio = titolo + ". La tua Quota: " + quotaFmt;
                                                        }

                                                        com.example.paripariapp.service.PariPariMessagingService.mostraNotificaNativa(context, notifTitolo, notifMessaggio, groupId);
                                                    }
                                                });
                                    }
                                    break;
                                case REMOVED:
                                    Spesa spesaEliminata = spesaDao.getSpesaByIdSync(eId);
                                    spesaDao.deleteById(eId);

                                    if (!isFirstBatch && !doc.getMetadata().hasPendingWrites() && spesaEliminata != null) {
                                        Scheda sRem = schedaDao.getSchedaById(groupId);
                                        String nomeGruppoRem = (sRem != null && sRem.getTitolo() != null) ? sRem.getTitolo() : "Gruppo";
                                        boolean isRimborsoRem = com.example.paripariapp.util.CategoriaUtil.isCategoriaSaldi(spesaEliminata.getCategoria());

                                        String notifTitoloRem = isRimborsoRem ? "Rimborso eliminato in \"" + nomeGruppoRem + "\"" : "Spesa eliminata in \"" + nomeGruppoRem + "\"";
                                        String notifMessaggioRem = spesaEliminata.getTitolo() + " rimosso: bilancio ricalcolato";

                                        com.example.paripariapp.service.PariPariMessagingService.mostraNotificaNativa(context, notifTitoloRem, notifMessaggioRem, groupId);
                                    }
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
                        String iconaUrl = doc.getString("iconaUrl");

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
                                String creatoreId = doc.getString("creatoreId");
                                if (creatoreId != null && !creatoreId.equals(s.getCreatoreId())) {
                                    s.setCreatoreId(creatoreId);
                                    modificata = true;
                                }
                                if (iconaUrl != null && !iconaUrl.equals(s.getIconaUrl())) {
                                    s.setIconaUrl(iconaUrl);
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

    private interface AuthSessionCallback {
        void onAuthenticated(@NonNull FirebaseUser user);
        void onError(@NonNull String errorMessage);
    }

    private void ensureAuthenticatedSession(@NonNull AuthSessionCallback callback) {
        FirebaseUser current = auth.getCurrentUser();
        if (current != null) {
            callback.onAuthenticated(current);
            return;
        }

        if (!networkMonitor.isConnected()) {
            callback.onError("Nessuna connessione a internet");
            return;
        }

        auth.signInAnonymously().addOnCompleteListener(task -> {
            if (task.isSuccessful() && auth.getCurrentUser() != null) {
                Log.d(TAG, "Accesso anonimo on-demand completato: " + auth.getCurrentUser().getUid());
                callback.onAuthenticated(auth.getCurrentUser());
            } else {
                Exception e = task.getException();
                String err = (e != null && e.getLocalizedMessage() != null)
                        ? e.getLocalizedMessage()
                        : "Impossibile autenticare la sessione ospite";
                Log.w(TAG, "Accesso anonimo on-demand non riuscito: " + err);
                callback.onError("Errore autenticazione ospite: " + err);
            }
        });
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

    public void recuperaAnteprimaGruppo(String codice, OnPreviewGruppoCallback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        String cleanCode = CodiceInvitoUtil.normalizzaCodice(codice);

        if (cleanCode == null || cleanCode.length() != 6) {
            mainHandler.post(() -> callback.onError("Formato codice non valido (deve essere di 6 caratteri alfanumerici)"));
            return;
        }

        AppDatabase.databaseWriteExecutor.execute(() -> {
            Scheda locale = schedaDao.getSchedaByCodiceInvito(cleanCode);
            if (locale != null) {
                mainHandler.post(() -> callback.onError("Sei già membro di questo gruppo (" + locale.getTitolo() + ")"));
                return;
            }

            if (!networkMonitor.isConnected()) {
                mainHandler.post(() -> callback.onError("Nessuna connessione a internet"));
                return;
            }

            ensureAuthenticatedSession(new AuthSessionCallback() {
                @Override
                public void onAuthenticated(@NonNull FirebaseUser user) {
                    eseguiRicercaAnteprima(cleanCode, callback, mainHandler);
                }

                @Override
                public void onError(@NonNull String errorMessage) {
                    mainHandler.post(() -> callback.onError(errorMessage));
                }
            });
        });
    }

    private void eseguiRicercaAnteprima(String cleanCode, OnPreviewGruppoCallback callback, Handler mainHandler) {
        firestore.collection("groups").whereEqualTo("codiceInvito", cleanCode).limit(1).get()
                .addOnSuccessListener(AppDatabase.databaseWriteExecutor, querySnapshot -> {
                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        elaboraAnteprimaGruppo(querySnapshot.getDocuments().get(0), callback, mainHandler);
                    } else {
                        firestore.collection("groups").document(cleanCode).get()
                                .addOnSuccessListener(AppDatabase.databaseWriteExecutor, docSnapshot -> {
                                    if (docSnapshot != null && docSnapshot.exists()) {
                                        elaboraAnteprimaGruppo(docSnapshot, callback, mainHandler);
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
    }

    private void elaboraAnteprimaGruppo(DocumentSnapshot groupDoc, OnPreviewGruppoCallback callback, Handler mainHandler) {
        String groupId = groupDoc.getId();
        String titolo = groupDoc.getString("titolo");
        String desc = groupDoc.getString("descrizione");
        String valuta = groupDoc.getString("valutaPredefinita");

        FirebaseUser currentUser = auth.getCurrentUser();
        String currentUid = currentUser != null ? currentUser.getUid() : null;

        groupDoc.getReference().collection("participants").get()
                .addOnSuccessListener(AppDatabase.databaseWriteExecutor, pSnaps -> {
                    List<MembroGruppoPreview> membri = new ArrayList<>();
                    if (pSnaps != null) {
                        for (DocumentSnapshot pDoc : pSnaps.getDocuments()) {
                            String pNome = pDoc.getString("nome");
                            String pUserId = pDoc.getString("userId");
                            String pEmail = pDoc.getString("email");
                            Boolean isAutenticatoDoc = pDoc.getBoolean("isAutenticato");

                            boolean isAutenticato = Boolean.TRUE.equals(isAutenticatoDoc)
                                    || (pUserId != null && !pUserId.trim().isEmpty())
                                    || (pEmail != null && !pEmail.trim().isEmpty() && pEmail.contains("@"));

                            String pPreviousUserId = pDoc.getString("previousUserId");
                            String pStato = pDoc.getString("stato");
                            if (pStato == null) pStato = Partecipante.STATO_ATTIVO;

                            if (pNome != null && !pNome.trim().isEmpty()) {
                                membri.add(new MembroGruppoPreview(
                                        pDoc.getId(),
                                        groupId,
                                        pNome.trim(),
                                        pEmail,
                                        pUserId,
                                        pPreviousUserId,
                                        pStato,
                                        isAutenticato
                                ));
                            }
                        }
                    }

                    String creatoreId = groupDoc.getString("creatoreId");
                    if (creatoreId != null && !creatoreId.trim().isEmpty() && membri.size() > 1) {
                        int ownerIdx = -1;
                        for (int i = 0; i < membri.size(); i++) {
                            MembroGruppoPreview m = membri.get(i);
                            if (m.getId().equals(creatoreId) || (m.getUserId() != null && m.getUserId().equals(creatoreId))) {
                                ownerIdx = i;
                                break;
                            }
                        }
                        if (ownerIdx > 0) {
                            MembroGruppoPreview owner = membri.remove(ownerIdx);
                            membri.add(0, owner);
                        }
                    }

                    GruppoPreview preview = new GruppoPreview(
                            groupId,
                            titolo != null ? titolo : "Gruppo",
                            desc != null ? desc : "",
                            valuta != null ? valuta : "EUR",
                            membri
                    );

                    mainHandler.post(() -> callback.onPreviewLoaded(preview));
                })
                .addOnFailureListener(e ->
                        mainHandler.post(() -> callback.onError("Impossibile recuperare i membri del gruppo: " + e.getLocalizedMessage()))
                );
    }

    public void uniscitiASchedaTramiteCodice(String codice, @Nullable String claimedPartecipanteId,
                                            @Nullable String nomePersonalizzato, OnJoinSchedaCallback callback) {
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

            if (!networkMonitor.isConnected()) {
                mainHandler.post(() -> callback.onError("Nessuna connessione a internet"));
                return;
            }

            ensureAuthenticatedSession(new AuthSessionCallback() {
                @Override
                public void onAuthenticated(@NonNull FirebaseUser user) {
                    eseguiJoinGruppo(cleanCode, claimedPartecipanteId, nomePersonalizzato, callback, mainHandler);
                }

                @Override
                public void onError(@NonNull String errorMessage) {
                    mainHandler.post(() -> callback.onError(errorMessage));
                }
            });
        });
    }

    private void eseguiJoinGruppo(String cleanCode, @Nullable String claimedPartecipanteId,
                                 @Nullable String nomePersonalizzato, OnJoinSchedaCallback callback, Handler mainHandler) {
        firestore.collection("groups").whereEqualTo("codiceInvito", cleanCode).limit(1).get()
                .addOnSuccessListener(AppDatabase.databaseWriteExecutor, querySnapshot -> {
                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        elaboraJoinGruppo(querySnapshot.getDocuments().get(0), cleanCode, claimedPartecipanteId, nomePersonalizzato, callback, mainHandler);
                    } else {
                        firestore.collection("groups").document(cleanCode).get()
                                .addOnSuccessListener(AppDatabase.databaseWriteExecutor, docSnapshot -> {
                                    if (docSnapshot != null && docSnapshot.exists()) {
                                        elaboraJoinGruppo(docSnapshot, cleanCode, claimedPartecipanteId, nomePersonalizzato, callback, mainHandler);
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
    }

    public void uniscitiASchedaTramiteCodice(String codice, @Nullable String nomePersonalizzato, OnJoinSchedaCallback callback) {
        uniscitiASchedaTramiteCodice(codice, null, nomePersonalizzato, callback);
    }

    public void uniscitiASchedaTramiteCodice(String codice, OnJoinSchedaCallback callback) {
        uniscitiASchedaTramiteCodice(codice, null, null, callback);
    }

    private void elaboraJoinGruppo(DocumentSnapshot groupDoc, String cleanCode,
                                  @Nullable String claimedPartecipanteId,
                                  @Nullable String nomePersonalizzato,
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
                            String pPreviousUserId = pDoc.getString("previousUserId");

                            boolean isClaimTarget = (claimedPartecipanteId != null && claimedPartecipanteId.equals(pDoc.getId()));
                            boolean isUserMatch = (currentUid != null && (currentUid.equals(pUserId) || currentUid.equals(pPreviousUserId)))
                                    || (currentEmail != null && !currentEmail.isEmpty() && currentEmail.equalsIgnoreCase(pEmail));

                            if (creatoreId != null && (creatoreId.equals(pUserId) || creatoreId.equals(pDoc.getId()))) {
                                scheda.setCreatoreId(pDoc.getId());
                            }

                            if (isClaimTarget || isUserMatch) {
                                giaPresente = true;
                                existingDocToUpdate = pDoc;
                                String finalName = (nomePersonalizzato != null && !nomePersonalizzato.trim().isEmpty())
                                        ? nomePersonalizzato.trim()
                                        : (pNome != null ? pNome : currentNome);
                                Partecipante pClaimed = new Partecipante(pDoc.getId(), groupId, finalName, currentEmail, SyncStatus.SYNCED);
                                pClaimed.setUserId(currentUid);
                                pClaimed.setStato(Partecipante.STATO_ATTIVO);
                                pClaimed.setPaypalHandle(pDoc.getString("paypalHandle"));
                                pClaimed.setRevolutHandle(pDoc.getString("revolutHandle"));
                                partiScaricati.add(pClaimed);
                            } else if (pNome != null) {
                                Partecipante pOther = new Partecipante(pDoc.getId(), groupId, pNome, pEmail, SyncStatus.SYNCED);
                                pOther.setUserId(pUserId);
                                pOther.setPreviousUserId(pPreviousUserId);
                                String pStato = pDoc.getString("stato");
                                if (pStato != null) pOther.setStato(pStato);
                                pOther.setPaypalHandle(pDoc.getString("paypalHandle"));
                                pOther.setRevolutHandle(pDoc.getString("revolutHandle"));
                                partiScaricati.add(pOther);
                            }
                        }
                    }

                    String mioPartId = null;
                    if (!giaPresente) {
                        mioPartId = UUID.randomUUID().toString();
                        Partecipante mioPartecipante = new Partecipante(mioPartId, groupId, currentNome, currentEmail, SyncStatus.SYNCED);
                        mioPartecipante.setUserId(currentUid);
                        partiScaricati.add(mioPartecipante);

                        Map<String, Object> myData = new HashMap<>();
                        myData.put("nome", currentNome);
                        myData.put("email", currentEmail);
                        myData.put("userId", currentUid);
                        myData.put("isAutenticato", currentUser != null && !currentUser.isAnonymous());

                        groupDoc.getReference().collection("participants").document(mioPartId)
                                .set(myData);
                    } else if (existingDocToUpdate != null) {
                        Map<String, Object> patch = new HashMap<>();
                        patch.put("userId", currentUid);
                        patch.put("stato", Partecipante.STATO_ATTIVO);
                        if (currentEmail != null) patch.put("email", currentEmail);
                        patch.put("isAutenticato", currentUser != null && !currentUser.isAnonymous());
                        if (nomePersonalizzato != null && !nomePersonalizzato.trim().isEmpty()) {
                            patch.put("nome", Partecipante.pulisciNome(nomePersonalizzato));
                        }
                        existingDocToUpdate.getReference().update(patch);
                    }

                    String myJoinedPartId = !giaPresente ? mioPartId : (existingDocToUpdate != null ? existingDocToUpdate.getId() : null);
                    if (myJoinedPartId != null) {
                        com.example.paripariapp.data.repository.UserPreferencesRepository.getInstance(context)
                                .setMyParticipantId(groupId, myJoinedPartId);
                    }

                    if (currentUid != null && !currentUid.trim().isEmpty()) {
                        groupDoc.getReference().update("membriUids", FieldValue.arrayUnion(currentUid))
                                .addOnFailureListener(e -> Log.d(TAG, "Aggiornamento membriUids su join non bloccante: " + e.getMessage()));
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
                                        String eScontrino = eDoc.getString("scontrinoUrl");
                                        String eScontrinoJson = eDoc.getString("scontrinoJson");

                                        if (eTit != null && eImp != null) {
                                            Spesa sDownload = new Spesa(
                                                    eDoc.getId(),
                                                    groupId,
                                                    eTit,
                                                    eImp,
                                                    eVal != null ? eVal : "EUR",
                                                    eData != null ? eData : System.currentTimeMillis(),
                                                    eCat != null ? eCat : "Generale",
                                                    ePagato != null ? ePagato : "",
                                                    eScontrino,
                                                    SyncStatus.SYNCED
                                            );
                                            sDownload.setScontrinoJson(eScontrinoJson);
                                            speseScaricate.add(sDownload);

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
