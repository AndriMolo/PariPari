package com.example.paripariapp.data.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Modello dati per il risultato aggregato del calcolo dei saldi
 * su tutte le schede dell'utente.
 */
public class RisultatoSaldi {
    private final double totaleRicevere;
    private final double totaleDare;
    private final List<BilancioPersonaItem> bilanci;

    public RisultatoSaldi(double totaleRicevere, double totaleDare, List<BilancioPersonaItem> bilanci) {
        this.totaleRicevere = totaleRicevere;
        this.totaleDare = totaleDare;
        this.bilanci = bilanci != null ? Collections.unmodifiableList(new ArrayList<>(bilanci)) : Collections.emptyList();
    }

    public double getTotaleRicevere() {
        return totaleRicevere;
    }

    public double getTotaleDare() {
        return totaleDare;
    }

    public List<BilancioPersonaItem> getBilanci() {
        return bilanci;
    }
}
