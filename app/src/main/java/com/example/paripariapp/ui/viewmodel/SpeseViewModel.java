package com.example.paripariapp.ui.viewmodel;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.example.paripariapp.data.local.PartecipanteDao;
import com.example.paripariapp.data.model.Partecipante;
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

    public SpeseViewModel(@NonNull Application application) {
        super(application);
        this.repository = PariPariRepository.getInstance(application);
    }

    // --- SCHEDE ---

    public LiveData<List<Scheda>> getSchede() {
        return repository.getAllSchede();
    }

    public LiveData<List<Scheda>> getTutteLeSchede() {
        return repository.getAllSchede();
    }

    public void eliminaScheda(String schedaId) {
        repository.deleteScheda(schedaId);
    }

    public void ripristinaScheda(Scheda scheda, List<Partecipante> partecipanti) {
        repository.insertScheda(scheda, partecipanti);
    }

    public void creaScheda(String nomeScheda, String valuta, List<String> nomiPartecipanti) {
        String schedaId = UUID.randomUUID().toString();
        Scheda nuovaScheda = Scheda.createNew(nomeScheda, "", valuta, null);
        nuovaScheda.setId(schedaId);

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

    // --- PREFERENZE ---

    public String getDefaultCurrency() {
        SharedPreferences prefs = getApplication().getSharedPreferences(
                getApplication().getPackageName() + "_preferences",
                Context.MODE_PRIVATE
        );
        return prefs.getString("valuta_predefinita", "EUR");
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
    public void registraPagamento(String schedaId, String daPartecipanteId, String aPartecipanteId, double importo, String valuta) {
        String spesaId = UUID.randomUUID().toString();

        // 1. Spesa fittizia di pareggio
        Spesa pagamento = new Spesa(
                spesaId,
                schedaId,
                "Pareggio conti",
                importo,
                valuta != null ? valuta : "EUR",
                System.currentTimeMillis(),
                "Pareggio",
                daPartecipanteId, // Chi paga realmente
                null,
                SyncStatus.PENDING_INSERT
        );

        // 2. La quota appartiene al 100% a chi riceve
        List<SpesaPartecipante> quote = new ArrayList<>();
        quote.add(new SpesaPartecipante(
                spesaId,
                aPartecipanteId, // Chi riceve il denaro
                importo,
                SyncStatus.PENDING_INSERT
        ));

        repository.insertSpesaConQuote(pagamento, quote);
    }

}