package com.example.paripariapp.ui.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.example.paripariapp.R;
import com.example.paripariapp.data.local.PartecipanteDao;
import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.RisultatoSaldi;
import com.example.paripariapp.data.model.Scheda;
import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.data.model.SpesaPartecipante;
import com.example.paripariapp.data.model.SyncStatus;
import com.example.paripariapp.data.repository.PariPariRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SpeseViewModel extends AndroidViewModel {

    private final PariPariRepository repository;
    private final androidx.lifecycle.MediatorLiveData<SpeseUiState> uiState = new androidx.lifecycle.MediatorLiveData<>();
    private List<Scheda> currentSchede = null;
    private Map<String, Integer> currentConteggi = new HashMap<>();

    public SpeseViewModel(@NonNull Application application) {
        super(application);
        this.repository = PariPariRepository.getInstance(application);

        uiState.setValue(SpeseUiState.loading());

        LiveData<List<Scheda>> schedeLive = repository.getAllSchede();
        LiveData<Map<String, Integer>> conteggiLive = getMappaConteggioPartecipanti();

        uiState.addSource(schedeLive, schede -> {
            currentSchede = schede;
            uiState.setValue(new SpeseUiState(false, currentSchede, currentConteggi, null));
        });

        uiState.addSource(conteggiLive, conteggi -> {
            if (conteggi != null) {
                currentConteggi = conteggi;
            }
            uiState.setValue(new SpeseUiState(false, currentSchede, currentConteggi, null));
        });
    }

    /**
     * Espone lo stato unificato della schermata Schede Spese secondo le raccomandazioni
     * di architettura Android (Single Source of Truth per la UI).
     */
    public LiveData<SpeseUiState> getUiState() {
        return uiState;
    }

    // --- SCHEDE ---

    public LiveData<List<Scheda>> getSchede() {
        return repository.getAllSchede();
    }

    public void eliminaScheda(String schedaId) {
        repository.deleteScheda(schedaId);
    }

    public boolean haSaldiInSospeso(String schedaId) {
        return repository.haSaldiInSospeso(schedaId);
    }

    public void verificaSaldiInSospeso(String schedaId, java.util.function.Consumer<Boolean> callback) {
        com.example.paripariapp.data.local.AppDatabase.databaseWriteExecutor.execute(() -> {
            boolean haSaldi = repository.haSaldiInSospeso(schedaId);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (callback != null) {
                    callback.accept(haSaldi);
                }
            });
        });
    }

    public LiveData<List<Spesa>> getStoricoSaldi(String schedaId) {
        return repository.getStoricoSaldi(schedaId);
    }

    public void ripristinaScheda(Scheda scheda, List<Partecipante> partecipanti) {
        repository.insertScheda(scheda, partecipanti);
    }

    public void creaScheda(String nomeScheda, String valuta, List<String> nomiPartecipanti) {
        creaScheda(nomeScheda, valuta, null, nomiPartecipanti);
    }

    public void creaScheda(String nomeScheda, String valuta, String iconaUrl, List<String> nomiPartecipanti) {
        String schedaId = UUID.randomUUID().toString();
        Scheda nuovaScheda = Scheda.createNew(nomeScheda, "", valuta, null);
        nuovaScheda.setId(schedaId);
        if (iconaUrl != null) {
            nuovaScheda.setIconaUrl(iconaUrl);
        }

        List<Partecipante> partecipanti = new ArrayList<>();
        if (nomiPartecipanti != null) {
            for (String nome : nomiPartecipanti) {
                if (nome != null && !nome.trim().isEmpty()) {
                    partecipanti.add(new Partecipante(
                            UUID.randomUUID().toString(),
                            schedaId,
                            nome.trim(),
                            null,
                            SyncStatus.PENDING_INSERT
                    ));
                }
            }
        }

        if (!partecipanti.isEmpty()) {
            nuovaScheda.setCreatoreId(partecipanti.get(0).getId());
            com.example.paripariapp.data.repository.UserPreferencesRepository.getInstance(getApplication())
                    .setMyParticipantId(schedaId, partecipanti.get(0).getId());
        }

        repository.insertScheda(nuovaScheda, partecipanti);
    }

    // --- CONTEGGI PARTECIPANTI ---

    public LiveData<Map<String, Integer>> getMappaConteggioPartecipanti() {
        return Transformations.map(repository.getAllConteggiPartecipanti(), listaTuple -> {
            Map<String, Integer> mappa = new HashMap<>();
            if (listaTuple != null) {
                for (PartecipanteDao.ConteggioPartecipantiTuple tuple : listaTuple) {
                    mappa.put(tuple.scheda_id, tuple.count);
                }
            }
            return mappa;
        });
    }

    // --- PARTECIPANTI, SPESE E QUOTE ---

    public LiveData<List<Partecipante>> getPartecipanti(String schedaId) {
        return repository.getPartecipanti(schedaId);
    }

    public LiveData<List<Spesa>> getSpese(String schedaId) {
        return repository.getSpese(schedaId);
    }

    public LiveData<List<SpesaPartecipante>> getQuoteDellaScheda(String schedaId) {
        return repository.getQuoteDellaScheda(schedaId);
    }

    public LiveData<RisultatoSaldi> getRisultatoSaldi() {
        return repository.getRisultatoSaldi();
    }

    // --- PREFERENZE ---

    public String getDefaultCurrency() {
        return com.example.paripariapp.data.repository.UserPreferencesRepository.getInstance(getApplication()).getDefaultCurrency();
    }

    public void eliminaScheda(Scheda scheda) {
        if (scheda != null) {
            eliminaScheda(scheda.getId());
        }
    }
    public void ripristinaScheda(Scheda scheda) {
        if (scheda != null) {
            repository.insertScheda(scheda, null);
        }
    }
    public void registraPagamento(String schedaId, String daId, String daNome, String aId, String aNome, double importo, String valuta) {
        registraRimborsoConCategoria(schedaId, daId, daNome, aId, aNome, importo, valuta, "SETTLE_UP", "Pareggio");
    }

    public void registraRimborso(String schedaId, String daId, String daNome, String aId, String aNome, double importo, String valuta, @Nullable String descrizione) {
        registraRimborsoConCategoria(schedaId, daId, daNome, aId, aNome, importo, valuta, descrizione, "Rimborso");
    }

    public void registraRimborsoConCategoria(String schedaId, String daId, String daNome, String aId, String aNome, double importo, String valuta, @Nullable String descrizione, String categoria) {
        String spesaId = UUID.randomUUID().toString();
        String pulitoDa = Partecipante.pulisciNome(daNome);
        String pulitoA = Partecipante.pulisciNome(aNome);
        String defaultMembro = getApplication().getString(R.string.membro_default);
        String titolo = getApplication().getString(
                R.string.titolo_rimborso_formattato,
                pulitoDa.isEmpty() ? defaultMembro : pulitoDa,
                pulitoA.isEmpty() ? defaultMembro : pulitoA
        );

        // 1. Spesa fittizia di pareggio
        Spesa pagamento = new Spesa(
                spesaId,
                schedaId,
                titolo,
                importo,
                valuta != null ? valuta : "EUR",
                System.currentTimeMillis(),
                categoria != null ? categoria : "Pareggio",
                daId, // Chi paga realmente
                descrizione, // Memorizzato in scontrino_url
                SyncStatus.PENDING_INSERT
        );

        // 2. La quota appartiene al 100% a chi riceve
        List<SpesaPartecipante> quote = new ArrayList<>();
        quote.add(new SpesaPartecipante(
                spesaId,
                aId, // Chi riceve il denaro
                importo,
                SyncStatus.PENDING_INSERT
        ));

        repository.insertSpesaConQuote(pagamento, quote);
    }

    public void eliminaSpesa(String spesaId, String schedaId) {
        repository.deleteSpesa(spesaId, schedaId);
    }

    public void recuperaAnteprimaGruppo(String codice, com.example.paripariapp.data.remote.FirestoreSyncManager.OnPreviewGruppoCallback callback) {
        repository.recuperaAnteprimaGruppo(codice, callback);
    }

    public void uniscitiAScheda(String codice, PariPariRepository.OnJoinSchedaCallback callback) {
        repository.uniscitiASchedaTramiteCodice(codice, null, callback);
    }

    public void uniscitiASchedaConNome(String codice, String nomePersonalizzato, PariPariRepository.OnJoinSchedaCallback callback) {
        repository.uniscitiASchedaTramiteCodice(codice, nomePersonalizzato, callback);
    }

    public void uniscitiASchedaConClaim(String codice, @Nullable String claimedPartecipanteId,
                                        @Nullable String nomePersonalizzato,
                                        PariPariRepository.OnJoinSchedaCallback callback) {
        repository.uniscitiASchedaTramiteCodice(codice, claimedPartecipanteId, nomePersonalizzato, callback);
    }

    public void importaSchedaDaCsv(android.content.Context context, android.net.Uri csvUri, PariPariRepository.OnImportCsvCallback callback) {
        repository.importaSchedaDaCsv(context, csvUri, callback);
    }

    public PariPariRepository getRepository() {
        return repository;
    }
}