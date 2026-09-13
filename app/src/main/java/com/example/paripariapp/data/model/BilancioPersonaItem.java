package com.example.paripariapp.data.model;

public class BilancioPersonaItem {
    private final String nomePersona;
    private final String nomeGruppo;
    private final double importo; // Positivo: deve a te. Negativo: devi dare tu.
    private final String valuta;
    private final String schedaId;
    private final TrasferimentoSaldo trasferimentoSaldo;

    public BilancioPersonaItem(String nomePersona, String nomeGruppo, double importo, String valuta, String schedaId, TrasferimentoSaldo trasferimentoSaldo) {
        this.nomePersona = nomePersona;
        this.nomeGruppo = nomeGruppo;
        this.importo = importo;
        this.valuta = valuta;
        this.schedaId = schedaId;
        this.trasferimentoSaldo = trasferimentoSaldo;
    }

    public String getNomePersona() { return nomePersona; }
    public String getNomeGruppo() { return nomeGruppo; }
    public double getImporto() { return importo; }
    public String getValuta() { return valuta; }
    public boolean isCredito() { return importo > 0; }
    public String getSchedaId() { return schedaId; }
    public TrasferimentoSaldo getTrasferimentoSaldo() { return trasferimentoSaldo; }
}
