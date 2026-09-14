package com.example.paripariapp.data.repository;

import android.app.Application;

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
import com.example.paripariapp.data.remote.FirestoreSyncManager;
import com.example.paripariapp.util.CalcolatoreSaldi;
import com.example.paripariapp.util.CodiceInvitoUtil;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository centrale (Single Source of Truth) dell'applicazione.
 * Coordina l'accesso ai dati persistiti localmente con Room e delega
 * la sincronizzazione e la gestione dei listener remoti a FirestoreSyncManager.
 */
public class PariPariRepository {

    private static final String TAG = "PariPariRepository";
    private static volatile PariPariRepository INSTANCE;

    private final Application application;
    private final SchedaDao schedaDao;
    private final PartecipanteDao partecipanteDao;
    private final SpesaDao spesaDao;
    private final FirebaseAuth auth;
    private final FirestoreSyncManager syncManager;

    private final MediatorLiveData<RisultatoSaldi> risultatoSaldiLiveData = new MediatorLiveData<>();
    private boolean saldiSourcesInitialized = false;

    private PariPariRepository(Application application) {
        this.application = application;
        AppDatabase db = AppDatabase.getInstance(application);
        this.schedaDao = db.schedaDao();
        this.partecipanteDao = db.partecipanteDao();
        this.spesaDao = db.spesaDao();
        this.auth = FirebaseAuth.getInstance();

        this.syncManager = new FirestoreSyncManager(application, schedaDao, partecipanteDao, spesaDao);

        AppDatabase.databaseWriteExecutor.execute(() -> partecipanteDao.deletePlaceholderPartecipanti());

        auth.addAuthStateListener(firebaseAuth -> {
            FirebaseUser user = firebaseAuth.getCurrentUser();
            if (user != null) {
                if (syncManager.isConnected()) {
                    syncManager.syncPendingData();
                    syncManager.startRealtimeSync();
                }
            } else {
                syncManager.stopRealtimeSync();
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
            if (syncManager.isConnected() && auth.getCurrentUser() != null) {
                syncManager.uploadScheda(scheda, partecipanti);
            }
        });
    }

    public void updateTitoloScheda(String schedaId, String nuovoTitolo) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            long adesso = System.currentTimeMillis();
            schedaDao.updateTitolo(schedaId, nuovoTitolo, adesso, SyncStatus.PENDING_UPDATE);
            syncManager.updateTitoloScheda(schedaId, nuovoTitolo, adesso);
        });
    }

    public void deleteScheda(String schedaId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            syncManager.deleteScheda(schedaId);

            // Pulizia transazionale/a cascata nel database Room locale
            spesaDao.deleteQuoteBySchedaId(schedaId);
            spesaDao.deleteBySchedaId(schedaId);
            partecipanteDao.deleteBySchedaId(schedaId);
            schedaDao.deleteById(schedaId);
        });
    }

    public LiveData<Scheda> getSchedaById(String schedaId) {
        return schedaDao.getSchedaByIdLive(schedaId);
    }

    public LiveData<List<Partecipante>> getPartecipanti(String schedaId) {
        if (auth.getCurrentUser() != null && syncManager.isConnected()) {
            syncManager.attachSubcollectionListeners(schedaId);
        }
        return partecipanteDao.getPartecipantiBySchedaLive(schedaId);
    }

    public void insertPartecipante(Partecipante partecipante) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            partecipanteDao.insert(partecipante);
            if (syncManager.isConnected() && auth.getCurrentUser() != null) {
                syncManager.uploadPartecipante(partecipante);
            }
        });
    }

    public void deletePartecipante(String partecipanteId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Partecipante p = partecipanteDao.getPartecipanteById(partecipanteId);
            spesaDao.deleteQuoteByPartecipanteId(partecipanteId);
            partecipanteDao.deleteById(partecipanteId);

            if (p != null) {
                syncManager.deletePartecipante(partecipanteId, p.getSchedaId());
            }
        });
    }

    public void deleteSchedaLocale(String schedaId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            spesaDao.deleteQuoteBySchedaId(schedaId);
            spesaDao.deleteBySchedaId(schedaId);
            partecipanteDao.deleteBySchedaId(schedaId);
            schedaDao.deleteById(schedaId);
        });
    }

    public void esciDalGruppo(String schedaId, String partecipanteId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            syncManager.detachSubcollectionListeners(schedaId);

            // 1. Prima nel DB locale (Room): gestiamo lo stato, il creatore o eventuale cancellazione se vuoto
            Scheda scheda = schedaDao.getSchedaById(schedaId);
            List<Partecipante> partecipanti = partecipanteDao.getPartecipantiBySchedaSync(schedaId);

            if (scheda != null && partecipanti != null) {
                boolean eraCreatore = (scheda.getCreatoreId() != null && scheda.getCreatoreId().equals(partecipanteId));

                Partecipante pUscito = partecipanteDao.getPartecipanteById(partecipanteId);
                if (pUscito != null) {
                    pUscito.setStato(Partecipante.STATO_USCITO);
                    pUscito.setPreviousUserId(auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null);
                    pUscito.setUserId(null);
                    pUscito.setSyncStatus(SyncStatus.PENDING_UPDATE);
                    partecipanteDao.update(pUscito);
                }

                if (eraCreatore) {
                    String nuovoCreatoreId = null;
                    // Cerca un altro utente autenticato
                    for (Partecipante p : partecipanti) {
                        if (!p.getId().equals(partecipanteId) && p.isAttivo() && p.isAutenticato()) {
                            nuovoCreatoreId = p.getId();
                            break;
                        }
                    }
                    if (nuovoCreatoreId == null) {
                        // Cerca qualsiasi altro partecipante attivo
                        for (Partecipante p : partecipanti) {
                            if (!p.getId().equals(partecipanteId) && p.isAttivo()) {
                                nuovoCreatoreId = p.getId();
                                break;
                            }
                        }
                    }

                    if (nuovoCreatoreId != null) {
                        scheda.setCreatoreId(nuovoCreatoreId);
                        scheda.setSyncStatus(SyncStatus.PENDING_UPDATE);
                        schedaDao.update(scheda);
                    } else {
                        // Nessun altro utente rimasto -> cancella definitivamente la scheda
                        spesaDao.deleteQuoteBySchedaId(schedaId);
                        spesaDao.deleteBySchedaId(schedaId);
                        partecipanteDao.deleteBySchedaId(schedaId);
                        schedaDao.deleteById(schedaId);
                    }
                }
            }

            // 2. Poi su Firestore
            if (auth.getCurrentUser() != null && syncManager.isConnected()) {
                syncManager.esciDalGruppo(schedaId, partecipanteId);
            }
        });
    }

    public void aggiornaNomePartecipante(String partecipanteId, String nuovoNome) {
        if (partecipanteId == null || nuovoNome == null || nuovoNome.trim().isEmpty()) return;
        final String nomePulito = nuovoNome.trim();

        AppDatabase.databaseWriteExecutor.execute(() -> {
            Partecipante p = partecipanteDao.getPartecipanteById(partecipanteId);
            if (p != null) {
                p.setNome(nomePulito);
                p.setSyncStatus(SyncStatus.PENDING_UPDATE);
                partecipanteDao.update(p);

                if (syncManager.isConnected() && auth.getCurrentUser() != null) {
                    syncManager.uploadPartecipante(p);
                }
            }
        });
    }

    public void aggiornaNomeUtenteInTuttiIGruppi(String nuovoNome) {
        if (nuovoNome == null || nuovoNome.trim().isEmpty()) return;
        final String nomePulito = nuovoNome.trim();

        AppDatabase.databaseWriteExecutor.execute(() -> {
            FirebaseUser currentUser = auth.getCurrentUser();
            List<Scheda> schede = schedaDao.getAllSchedeSync();
            if (schede != null) {
                for (Scheda s : schede) {
                    List<Partecipante> parti = partecipanteDao.getPartecipantiBySchedaSync(s.getId());
                    if (parti != null) {
                        for (Partecipante p : parti) {
                            if (Partecipante.isCurrentUserParticipant(p, currentUser)) {
                                p.setNome(nomePulito);
                                p.setSyncStatus(SyncStatus.PENDING_UPDATE);
                                partecipanteDao.update(p);
                                if (syncManager.isConnected() && currentUser != null) {
                                    syncManager.uploadPartecipante(p);
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    public void aggiornaPaymentHandlesInTuttiIGruppi(String paypalHandle, String revolutHandle) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            FirebaseUser currentUser = auth.getCurrentUser();
            List<Scheda> schede = schedaDao.getAllSchedeSync();
            if (schede != null) {
                for (Scheda s : schede) {
                    List<Partecipante> parti = partecipanteDao.getPartecipantiBySchedaSync(s.getId());
                    if (parti != null) {
                        String myPartId = Partecipante.findCurrentUserId(parti, currentUser);
                        for (Partecipante p : parti) {
                            if (p.getId().equals(myPartId) || Partecipante.isCurrentUserParticipant(p, currentUser)) {
                                p.setPaypalHandle(paypalHandle);
                                p.setRevolutHandle(revolutHandle);
                                p.setSyncStatus(SyncStatus.PENDING_UPDATE);
                                partecipanteDao.update(p);
                                if (syncManager.isConnected() && currentUser != null) {
                                    syncManager.uploadPartecipante(p);
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    public LiveData<List<Spesa>> getSpese(String schedaId) {
        if (auth.getCurrentUser() != null && syncManager.isConnected()) {
            syncManager.attachSubcollectionListeners(schedaId);
        }
        return spesaDao.getSpeseBySchedaLive(schedaId);
    }

    public LiveData<List<SpesaConDettagli>> getSpeseConDettagli(String schedaId) {
        if (auth.getCurrentUser() != null && syncManager.isConnected()) {
            syncManager.attachSubcollectionListeners(schedaId);
        }
        return spesaDao.getSpeseConDettagliBySchedaLive(schedaId);
    }

    public LiveData<Double> getTotaleSpeseByScheda(String schedaId) {
        MediatorLiveData<Double> totaleLiveData = new MediatorLiveData<>();
        LiveData<Scheda> schedaLive = schedaDao.getSchedaByIdLive(schedaId);
        LiveData<List<Spesa>> speseLive = spesaDao.getSpeseBySchedaLive(schedaId);

        Runnable recalculate = () -> {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                Scheda scheda = schedaLive.getValue();
                List<Spesa> spese = speseLive.getValue();
                if (scheda == null || spese == null) {
                    totaleLiveData.postValue(0.0);
                    return;
                }
                String valutaScheda = scheda.getValutaPredefinita() != null ? scheda.getValutaPredefinita() : "EUR";
                double totaleConvertito = 0.0;
                for (Spesa s : spese) {
                    if (s != null && !com.example.paripariapp.util.CategoriaUtil.isCategoriaSaldi(s.getCategoria())) {
                        String valutaSpesa = s.getValuta();
                        totaleConvertito += CalcolatoreSaldi.convertiValuta(s.getImporto(), valutaSpesa, valutaScheda, application);
                    }
                }
                totaleLiveData.postValue(totaleConvertito);
            });
        };

        totaleLiveData.addSource(schedaLive, s -> recalculate.run());
        totaleLiveData.addSource(speseLive, sp -> recalculate.run());

        return totaleLiveData;
    }

    public LiveData<Spesa> getSpesaById(String spesaId) {
        return spesaDao.getSpesaByIdLive(spesaId);
    }

    public void insertSpesaConQuote(Spesa spesa, List<SpesaPartecipante> quote) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            spesaDao.insertSpesaConQuoteTransaction(spesa, quote);
            if (syncManager.isConnected() && auth.getCurrentUser() != null) {
                syncManager.uploadSpesaConQuote(spesa, quote);
            }
        });
    }

    public void insertSpesa(Spesa spesa, List<SpesaPartecipante> quote) {
        insertSpesaConQuote(spesa, quote);
    }

    public void deleteSpesa(String spesaId, String schedaId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            spesaDao.deleteSpesaTransaction(spesaId);
            syncManager.deleteSpesa(spesaId, schedaId);
        });
    }

    public LiveData<List<SpesaPartecipante>> getQuoteBySpesa(String spesaId) {
        return spesaDao.getQuoteBySpesaLive(spesaId);
    }

    public LiveData<List<SpesaPartecipante>> getQuoteDellaScheda(String schedaId) {
        return spesaDao.getTutteQuoteBySchedaLive(schedaId);
    }

    public void aggiornaSpesaConQuote(Spesa spesa, List<SpesaPartecipante> quote) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            spesa.setSyncStatus(SyncStatus.PENDING_UPDATE);
            if (quote != null && !quote.isEmpty()) {
                for (SpesaPartecipante q : quote) {
                    q.setSyncStatus(SyncStatus.PENDING_UPDATE);
                }
            }
            spesaDao.aggiornaSpesaConQuoteTransaction(spesa, quote);
            if (syncManager.isConnected() && auth.getCurrentUser() != null) {
                syncManager.uploadSpesaConQuote(spesa, quote);
            }
        });
    }

    public LiveData<List<Spesa>> getStoricoSaldi(String schedaId) {
        if (schedaId != null && !schedaId.isEmpty()) {
            return spesaDao.getSaldiBySchedaLive(schedaId);
        } else {
            return spesaDao.getTuttiSaldiLive();
        }
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

            FirebaseUser currentUser = auth.getCurrentUser();

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

                List<TrasferimentoSaldo> trasferimenti = CalcolatoreSaldi.calcolaTrasferimenti(parti, spese, quote, valuta, application);

                String mioId = Partecipante.findCurrentUserId(parti, currentUser);

                if (mioId != null) {
                    for (TrasferimentoSaldo t : trasferimenti) {
                        if (t.getDaPartecipanteId().equals(mioId)) {
                            totaleDare += t.getImporto();
                            bilanci.add(new BilancioPersonaItem(t.getAPartecipanteNome(), titoloScheda, -t.getImporto(), valuta, idScheda, t));
                        } else if (t.getAPartecipanteId().equals(mioId)) {
                            totaleRicevere += t.getImporto();
                            bilanci.add(new BilancioPersonaItem(t.getDaPartecipanteNome(), titoloScheda, t.getImporto(), valuta, idScheda, t));
                        }
                    }
                }
            }

            risultatoSaldiLiveData.postValue(new RisultatoSaldi(totaleRicevere, totaleDare, bilanci));
        });
    }

    public boolean haSaldiInSospeso(String schedaId) {
        if (schedaId == null || schedaId.isEmpty()) return false;
        List<Partecipante> parti = partecipanteDao.getPartecipantiBySchedaSync(schedaId);
        List<Spesa> spese = spesaDao.getSpeseBySchedaSync(schedaId);
        List<SpesaPartecipante> quote = spesaDao.getTutteQuoteBySchedaSync(schedaId);

        if (parti == null || parti.isEmpty() || spese == null || spese.isEmpty()) {
            return false;
        }

        Scheda scheda = schedaDao.getSchedaById(schedaId);
        String valuta = (scheda != null && scheda.getValutaPredefinita() != null) ? scheda.getValutaPredefinita() : "EUR";
        List<TrasferimentoSaldo> trasferimenti = CalcolatoreSaldi.calcolaTrasferimenti(parti, spese, quote, valuta, application);
        FirebaseUser currentUser = auth.getCurrentUser();
        String mioId = Partecipante.findCurrentUserId(parti, currentUser);
        if (mioId != null && trasferimenti != null) {
            for (TrasferimentoSaldo t : trasferimenti) {
                if ((t.getDaPartecipanteId().equals(mioId) || t.getAPartecipanteId().equals(mioId)) && t.getImporto() > 0.001) {
                    return true;
                }
            }
        }
        return false;
    }

    public void assicuraCodiceInvito(Scheda scheda) {
        syncManager.assicuraCodiceInvito(scheda);
    }

    public interface OnJoinSchedaCallback extends FirestoreSyncManager.OnJoinSchedaCallback {}
    public interface OnPreviewGruppoCallback extends FirestoreSyncManager.OnPreviewGruppoCallback {}
    public static class GruppoPreview extends FirestoreSyncManager.GruppoPreview {
        public GruppoPreview(String groupId, String titolo, String descrizione, String valutaPredefinita, List<com.example.paripariapp.data.model.MembroGruppoPreview> membri) {
            super(groupId, titolo, descrizione, valutaPredefinita, membri);
        }
    }

    public void recuperaAnteprimaGruppo(String codice, FirestoreSyncManager.OnPreviewGruppoCallback callback) {
        syncManager.recuperaAnteprimaGruppo(codice, callback);
    }

    public void uniscitiASchedaTramiteCodice(String codice, OnJoinSchedaCallback callback) {
        syncManager.uniscitiASchedaTramiteCodice(codice, null, null, callback);
    }

    public void uniscitiASchedaTramiteCodice(String codice, @Nullable String nomePersonalizzato, OnJoinSchedaCallback callback) {
        syncManager.uniscitiASchedaTramiteCodice(codice, null, nomePersonalizzato, callback);
    }

    public void uniscitiASchedaTramiteCodice(String codice, @Nullable String claimedPartecipanteId,
                                            @Nullable String nomePersonalizzato, OnJoinSchedaCallback callback) {
        syncManager.uniscitiASchedaTramiteCodice(codice, claimedPartecipanteId, nomePersonalizzato, callback);
    }

    public void startRealtimeSync() {
        syncManager.startRealtimeSync();
    }

    public void stopRealtimeSync() {
        syncManager.stopRealtimeSync();
    }

    public void syncPendingData() {
        syncManager.syncPendingData();
    }
}