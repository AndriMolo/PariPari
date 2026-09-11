package com.example.paripariapp.data.model;

import androidx.room.Embedded;

public class SpesaConDettagli {

    @Embedded
    public Spesa spesa;

    public String nomePagatore;
    public int numeroPartecipanti;

    public Spesa getSpesa() {
        return spesa;
    }

    public String getNomePagatore() {
        return nomePagatore != null ? nomePagatore : "Sconosciuto";
    }

    public int getNumeroPartecipanti() {
        return numeroPartecipanti;
    }
}