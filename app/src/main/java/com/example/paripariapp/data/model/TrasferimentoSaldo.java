package com.example.paripariapp.data.model;

import java.util.Objects;

/**
 * Modello che rappresenta un debito/credito calcolato tra due partecipanti.
 */
public class TrasferimentoSaldo {
    private final String daId;
    private final String daNome;
    private final String aId;
    private final String aNome;
    private final double importo;
    private final String valuta;

    public TrasferimentoSaldo(String daId, String daNome, String aId, String aNome, double importo, String valuta) {
        this.daId = daId;
        this.daNome = daNome;
        this.aId = aId;
        this.aNome = aNome;
        this.importo = importo;
        this.valuta = valuta;
    }

    // Getter con nomenclatura estesa (richiesti da CalcolatoreSaldi / SaldoAdapter)
    public String getDaPartecipanteId() {
        return daId;
    }

    public String getAPartecipanteId() {
        return aId;
    }

    public String getDaPartecipanteNome() {
        return daNome;
    }

    public String getAPartecipanteNome() {
        return aNome;
    }

    // Getter con nomenclatura compatta (usati in SaldiFragment)
    public String getDaId() {
        return daId;
    }

    public String getDaNome() {
        return daNome;
    }

    public String getAId() {
        return aId;
    }

    public String getANome() {
        return aNome;
    }

    public double getImporto() {
        return importo;
    }

    public String getValuta() {
        return valuta;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrasferimentoSaldo that = (TrasferimentoSaldo) o;
        return Double.compare(that.importo, importo) == 0 &&
                Objects.equals(daId, that.daId) &&
                Objects.equals(daNome, that.daNome) &&
                Objects.equals(aId, that.aId) &&
                Objects.equals(aNome, that.aNome) &&
                Objects.equals(valuta, that.valuta);
    }

    @Override
    public int hashCode() {
        return Objects.hash(daId, daNome, aId, aNome, importo, valuta);
    }
}