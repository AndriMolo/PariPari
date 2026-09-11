package com.example.paripariapp.ui.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.data.model.SpesaPartecipante;
import com.example.paripariapp.data.model.SyncStatus;
import com.example.paripariapp.data.repository.PariPariRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel per il dettaglio di una singola scheda spese.
 * Gestisce la lista delle spese, il totale aggregato, i partecipanti
 * e l'aggiunta di nuove spese divise tra i membri del gruppo.
 */
public class DettaglioSchedaViewModel extends AndroidViewModel {

    private final PariPariRepository repository;

    public DettaglioSchedaViewModel(@NonNull Application application) {
        super(application);
        this.repository = PariPariRepository.getInstance(application);
    }

    public LiveData<List<Spesa>> getSpese(String schedaId) {
        return repository.getSpese(schedaId);
    }

    public LiveData<Double> getTotaleSpese(String schedaId) {
        return repository.getTotaleSpeseByScheda(schedaId);
    }

    public LiveData<List<Partecipante>> getPartecipanti(String schedaId) {
        return repository.getPartecipanti(schedaId);
    }

    public void aggiornaTitoloScheda(String schedaId, String nuovoTitolo) {
        repository.updateTitoloScheda(schedaId, nuovoTitolo);
    }

    public void eliminaScheda(String schedaId) {
        repository.deleteScheda(schedaId);
    }

    public void eliminaPartecipante(String partecipanteId) {
        repository.deletePartecipante(partecipanteId);
    }

    /**
     * Aggiunge una nuova spesa e suddivide equamente l'importo tra tutti i partecipanti della scheda.
     */
    public void aggiungiSpesa(String schedaId, String titolo, double importo, String valuta,
                              String pagatoDaId, List<Partecipante> partecipanti) {
        if (schedaId == null || titolo == null || titolo.trim().isEmpty() || importo <= 0 || pagatoDaId == null) {
            return;
        }

        Spesa spesa = Spesa.createNew(
                schedaId,
                titolo.trim(),
                importo,
                valuta,
                "Generale",
                pagatoDaId,
                null
        );

        List<SpesaPartecipante> quote = new ArrayList<>();
        if (partecipanti != null && !partecipanti.isEmpty()) {
            double quotaEqua = importo / partecipanti.size();
            for (Partecipante p : partecipanti) {
                quote.add(new SpesaPartecipante(spesa.getId(), p.getId(), quotaEqua, SyncStatus.PENDING_INSERT));
            }
        }

        repository.insertSpesa(spesa, quote);
    }
    public void inserisciSpesaConQuote(Spesa spesa, List<SpesaPartecipante> quote) {
        repository.insertSpesa(spesa, quote);
    }

    public void aggiungiPartecipante(Partecipante partecipante) {
        repository.insertPartecipante(partecipante);
    }
}