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

    public boolean haPartecipatoASpese(String schedaId, String partecipanteId) {
        Spesa spesaPagata = spesaDao.getPrimaSpesaPagataDaPartecipanteSync(schedaId, partecipanteId);
        if (spesaPagata != null) return true;
        SpesaPartecipante quota = spesaDao.getPrimaQuotaPartecipanteSync(schedaId, partecipanteId);
        return quota != null && Math.abs(quota.getQuota()) > 0.001;
    }

    public void eliminaPartecipanteDefinitivamente(String schedaId, String partecipanteId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            partecipanteDao.deleteById(partecipanteId);
            syncManager.deletePartecipanteDefinitivamente(schedaId, partecipanteId);
        });
    }

    public void esciDalGruppo(String schedaId, String partecipanteId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<Partecipante> partecipantiAttivi = partecipanteDao.getPartecipantiAttiviBySchedaSync(schedaId);

            int altriAutenticati = 0;
            if (partecipantiAttivi != null) {
                for (Partecipante p : partecipantiAttivi) {
                    if (!p.getId().equals(partecipanteId) && p.isAutenticato()) {
                        altriAutenticati++;
                    }
                }
            }

            // SE NON CI SONO ALTRI UTENTI REALI AUTENTICATI NEL GRUPPO:
            // L'uscita del capogruppo cancella il gruppo sia dal DB locale che da Firestore!
            if (altriAutenticati == 0) {
                syncManager.eliminaGruppoDefinitivamente(schedaId);
                spesaDao.deleteBySchedaId(schedaId);
                partecipanteDao.deleteBySchedaId(schedaId);
                schedaDao.deleteById(schedaId);
                return;
            }

            Scheda scheda = schedaDao.getSchedaById(schedaId);
            Partecipante pUscito = partecipanteDao.getPartecipanteById(partecipanteId);

            if (pUscito != null) {
                FirebaseUser currentUser = auth.getCurrentUser();
                boolean isSelf = Partecipante.isCurrentUserParticipant(pUscito, currentUser);
                boolean eLocale = !pUscito.isAutenticato();
                boolean haSpese = haPartecipatoASpese(schedaId, partecipanteId);

                if (eLocale) {
                    if (haSpese) {
                        // Membro locale con spese: archiviazione silenziosa senza mostrare tra ex membri
                        pUscito.setStato(Partecipante.STATO_ARCHIVIATO);
                        pUscito.setUserId(null);
                        pUscito.setSyncStatus(SyncStatus.PENDING_UPDATE);
                        partecipanteDao.update(pUscito);
                        syncManager.disattivaMembroLocale(schedaId, partecipanteId);
                    } else {
                        // Membro locale senza spese: eliminazione definitiva
                        partecipanteDao.deleteById(partecipanteId);
                        syncManager.deletePartecipanteDefinitivamente(schedaId, partecipanteId);
                    }
                } else {
                    // Membro autenticato reale: passa a USCITO (Ex Membro)
                    if (isSelf) {
                        syncManager.detachSubcollectionListeners(schedaId);
                        try {
                            com.google.firebase.messaging.FirebaseMessaging.getInstance().unsubscribeFromTopic("group_" + schedaId);
                        } catch (Exception ignored) {}
                    }

                    pUscito.setStato(Partecipante.STATO_USCITO);
                    pUscito.setPreviousUserId(currentUser != null ? currentUser.getUid() : null);
                    pUscito.setUserId(null);
                    pUscito.setSyncStatus(SyncStatus.PENDING_UPDATE);
                    partecipanteDao.update(pUscito);
                    syncManager.esciDalGruppo(schedaId, partecipanteId);

                    if (isSelf) {
                        spesaDao.deleteBySchedaId(schedaId);
                        partecipanteDao.deleteBySchedaId(schedaId);
                        schedaDao.deleteById(schedaId);
                    }
                }

                // Passaggio creatore al primo membro attivo autenticato
                if (scheda != null && scheda.getCreatoreId() != null && scheda.getCreatoreId().equals(partecipanteId)) {
                    List<Partecipante> attiviRimasti = partecipanteDao.getPartecipantiAttiviBySchedaSync(schedaId);
                    if (attiviRimasti != null) {
                        for (Partecipante p : attiviRimasti) {
                            if (!p.getId().equals(partecipanteId) && p.isAutenticato()) {
                                scheda.setCreatoreId(p.getId());
                                scheda.setSyncStatus(SyncStatus.PENDING_UPDATE);
                                schedaDao.update(scheda);
                                break;
                            }
                        }
                    }
                }
            }
        });
    }

    public void disattivaMembro(String schedaId, String partecipanteId) {
        esciDalGruppo(schedaId, partecipanteId);
    }

    public void riattivaMembro(String schedaId, String partecipanteId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Partecipante p = partecipanteDao.getPartecipanteById(partecipanteId);
            if (p != null) {
                p.setStato(Partecipante.STATO_ATTIVO);
                FirebaseUser currentUser = auth.getCurrentUser();
                if (p.getPreviousUserId() != null && currentUser != null && currentUser.getUid().equals(p.getPreviousUserId())) {
                    p.setUserId(currentUser.getUid());
                }
                p.setSyncStatus(SyncStatus.PENDING_UPDATE);
                partecipanteDao.update(p);

                if (syncManager.isConnected() && currentUser != null) {
                    syncManager.uploadPartecipante(p);
                }
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
                        String myPartId = Partecipante.findCurrentUserId(parti, currentUser, UserPreferencesRepository.getInstance(application), s.getId());
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

                String mioId = Partecipante.findCurrentUserId(parti, currentUser, UserPreferencesRepository.getInstance(application), idScheda);

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
        String mioId = Partecipante.findCurrentUserId(parti, currentUser, UserPreferencesRepository.getInstance(application), schedaId);
        if (mioId != null && trasferimenti != null) {
            for (TrasferimentoSaldo t : trasferimenti) {
                if ((t.getDaPartecipanteId().equals(mioId) || t.getAPartecipanteId().equals(mioId)) && t.getImporto() > 0.001) {
                    return true;
                }
            }
        }
        return false;
    }

    public interface OnImportCsvCallback {
        void onSuccess(String schedaId, String titolo);
        void onError(String errore);
    }

    public void importaSchedaDaCsv(android.content.Context context, android.net.Uri csvUri, OnImportCsvCallback callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            String schedaId = java.util.UUID.randomUUID().toString();
            com.example.paripariapp.util.ImportatoreCsvUtil.RisultatoImportazione res =
                    com.example.paripariapp.util.ImportatoreCsvUtil.analizzaCsv(context, csvUri, schedaId);

            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            if (res == null || res.speseConQuote.isEmpty()) {
                mainHandler.post(() -> callback.onError("Impossibile leggere il file CSV o formato non valido"));
                return;
            }

            Scheda scheda = Scheda.createNew(res.nomeScheda, "", res.valuta, null);
            scheda.setId(schedaId);

            if (res.partecipanti != null && !res.partecipanti.isEmpty()) {
                scheda.setCreatoreId(res.partecipanti.get(0).getId());
                UserPreferencesRepository.getInstance(context)
                        .setMyParticipantId(schedaId, res.partecipanti.get(0).getId());
            }

            insertScheda(scheda, res.partecipanti);

            for (com.example.paripariapp.util.ImportatoreCsvUtil.SpesaConQuote sq : res.speseConQuote) {
                insertSpesaConQuote(sq.spesa, sq.quote);
            }

            mainHandler.post(() -> callback.onSuccess(schedaId, res.nomeScheda));
        });
    }

    public interface OnUploadCallback {
        void onSuccess(String url);
        void onError(String errore);
    }

    public void aggiornaIconaScheda(String schedaId, String iconaUrl) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Scheda s = schedaDao.getSchedaById(schedaId);
            if (s != null) {
                s.setIconaUrl(iconaUrl);
                s.setDataAggiornamento(System.currentTimeMillis());
                schedaDao.insert(s);
                syncManager.uploadScheda(s, partecipanteDao.getPartecipantiBySchedaSync(schedaId));
            }
        });
    }

    public void uploadIconaScheda(android.content.Context context, android.net.Uri fileUri, String schedaId, OnUploadCallback callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            byte[] imageBytes = ridimensionaEComprimiImmagine(context, fileUri);
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            if (imageBytes == null) {
                mainHandler.post(() -> callback.onError("Impossibile elaborare l'immagine selezionata"));
                return;
            }

            com.google.firebase.storage.StorageReference ref =
                    getStorageInstance().getReference()
                            .child("group_icons/" + schedaId + ".jpg");

            ref.putBytes(imageBytes)
                    .continueWithTask(task -> {
                        if (!task.isSuccessful()) {
                            if (task.getException() != null) {
                                throw task.getException();
                            } else {
                                throw new Exception("Upload fallito. Verifica le regole di Firebase Storage.");
                            }
                        }
                        return ref.getDownloadUrl();
                    })
                    .addOnSuccessListener(downloadUri -> {
                        String url = downloadUri.toString();
                        aggiornaIconaScheda(schedaId, url);
                        mainHandler.post(() -> callback.onSuccess(url));
                    })
                    .addOnFailureListener(e -> mainHandler.post(() -> callback.onError(e != null && e.getMessage() != null ? e.getMessage() : "Errore durante l'upload")));
        });
    }

    public void aggiornaAvatarUtente(String photoUrl) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                String uid = user.getUid();
                List<Partecipante> partecipanti = partecipanteDao.getAllPartecipantiSync();
                if (partecipanti != null) {
                    for (Partecipante p : partecipanti) {
                        if (uid.equals(p.getUserId())) {
                            p.setPhotoUrl(photoUrl);
                            partecipanteDao.insert(p);
                            syncManager.uploadPartecipante(p);
                        }
                    }
                }
                java.util.Map<String, Object> userData = new java.util.HashMap<>();
                userData.put("photoUrl", photoUrl != null ? photoUrl : "");
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("users").document(uid)
                        .set(userData, com.google.firebase.firestore.SetOptions.merge());
            }
        });
    }

    private com.google.firebase.storage.FirebaseStorage getStorageInstance() {
        try {
            return com.google.firebase.storage.FirebaseStorage.getInstance("gs://paripari-app-2026.firebasestorage.app");
        } catch (Exception e) {
            return com.google.firebase.storage.FirebaseStorage.getInstance();
        }
    }

    public void uploadAvatarUtente(android.content.Context context, android.net.Uri fileUri, OnUploadCallback callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            if (user == null) {
                mainHandler.post(() -> callback.onError("Utente non autenticato"));
                return;
            }

            byte[] imageBytes = ridimensionaEComprimiImmagine(context, fileUri);
            if (imageBytes == null) {
                mainHandler.post(() -> callback.onError("Impossibile elaborare l'immagine profilata"));
                return;
            }

            com.google.firebase.storage.StorageReference ref =
                    getStorageInstance().getReference()
                            .child("avatars/" + user.getUid() + ".jpg");

            ref.putBytes(imageBytes)
                    .continueWithTask(task -> {
                        if (!task.isSuccessful()) {
                            if (task.getException() != null) {
                                throw task.getException();
                            } else {
                                throw new Exception("Upload fallito. Verifica le regole di Firebase Storage.");
                            }
                        }
                        return ref.getDownloadUrl();
                    })
                    .addOnSuccessListener(downloadUri -> {
                        String url = downloadUri.toString();
                        aggiornaAvatarUtente(url);
                        mainHandler.post(() -> callback.onSuccess(url));
                    })
                    .addOnFailureListener(e -> mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "Errore durante l'upload")));
        });
    }

    private byte[] ridimensionaEComprimiImmagine(android.content.Context context, android.net.Uri fileUri) {
        try (java.io.InputStream is = context.getContentResolver().openInputStream(fileUri)) {
            android.graphics.Bitmap bitmapOriginal = android.graphics.BitmapFactory.decodeStream(is);
            if (bitmapOriginal == null) return null;

            int maxDim = 1024;
            int width = bitmapOriginal.getWidth();
            int height = bitmapOriginal.getHeight();

            if (width > maxDim || height > maxDim) {
                float ratio = Math.min((float) maxDim / width, (float) maxDim / height);
                width = Math.round(width * ratio);
                height = Math.round(height * ratio);
                bitmapOriginal = android.graphics.Bitmap.createScaledBitmap(bitmapOriginal, width, height, true);
            }

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            bitmapOriginal.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, baos);
            return baos.toByteArray();
        } catch (Exception e) {
            android.util.Log.e("PariPariRepository", "Errore compressione immagine", e);
            return null;
        }
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