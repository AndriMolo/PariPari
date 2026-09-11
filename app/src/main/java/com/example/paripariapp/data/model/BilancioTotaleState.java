package com.example.paripariapp.data.model;

import java.util.List;

public class BilancioTotaleState {
    private final double daRicevere;
    private final double daDare;
    private final List<BilancioPersonaItem> listaVoci;

    public BilancioTotaleState(double daRicevere, double daDare, List<BilancioPersonaItem> listaVoci) {
        this.daRicevere = daRicevere;
        this.daDare = daDare;
        this.listaVoci = listaVoci;
    }

    public double getDaRicevere() { return daRicevere; }
    public double getDaDare() { return daDare; }
    public List<BilancioPersonaItem> getListaVoci() { return listaVoci; }
}