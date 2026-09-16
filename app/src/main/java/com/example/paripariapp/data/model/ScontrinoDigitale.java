package com.example.paripariapp.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Modello strutturato per la memorizzazione digitale di uno scontrino OCR.
 * Occupazione stimata su Firestore: < 2 KB in formato JSON.
 */
public class ScontrinoDigitale implements Serializable {

    private String esercente;
    private String dataFormatted;
    private Long timestamp;
    private Double totale;
    private Double subtotale;
    private String valuta;
    private String metodoPagamento;
    private String piva;
    private List<VoceScontrino> voci = new ArrayList<>();
    private String testoGreggio;

    public static class VoceScontrino implements Serializable {
        private String descrizione;
        private double quantita;
        private double prezzoUnitario;
        private double prezzoTotale;

        public VoceScontrino() {
            this.quantita = 1.0;
        }

        public VoceScontrino(String descrizione, double quantita, double prezzoUnitario, double prezzoTotale) {
            this.descrizione = descrizione != null ? descrizione.trim() : "";
            this.quantita = quantita > 0 ? quantita : 1.0;
            this.prezzoUnitario = prezzoUnitario;
            this.prezzoTotale = prezzoTotale;
        }

        public String getDescrizione() {
            return descrizione;
        }

        public void setDescrizione(String descrizione) {
            this.descrizione = descrizione;
        }

        public double getQuantita() {
            return quantita;
        }

        public void setQuantita(double quantita) {
            this.quantita = quantita;
        }

        public double getPrezzoUnitario() {
            return prezzoUnitario;
        }

        public void setPrezzoUnitario(double prezzoUnitario) {
            this.prezzoUnitario = prezzoUnitario;
        }

        public double getPrezzoTotale() {
            return prezzoTotale;
        }

        public void setPrezzoTotale(double prezzoTotale) {
            this.prezzoTotale = prezzoTotale;
        }

        public JSONObject toJsonObject() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("descrizione", descrizione);
                obj.put("quantita", quantita);
                obj.put("prezzoUnitario", prezzoUnitario);
                obj.put("prezzoTotale", prezzoTotale);
                return obj;
            } catch (Exception e) {
                return new JSONObject();
            }
        }

        public static VoceScontrino fromJsonObject(JSONObject obj) {
            if (obj == null) return null;
            String desc = obj.optString("descrizione", "");
            double qta = obj.optDouble("quantita", 1.0);
            double pu = obj.optDouble("prezzoUnitario", 0.0);
            double pt = obj.optDouble("prezzoTotale", pu);
            return new VoceScontrino(desc, qta, pu, pt);
        }
    }

    public ScontrinoDigitale() {
        this.valuta = "EUR";
        this.voci = new ArrayList<>();
    }

    public String getEsercente() {
        return esercente;
    }

    public void setEsercente(String esercente) {
        this.esercente = esercente;
    }

    public String getDataFormatted() {
        return dataFormatted;
    }

    public void setDataFormatted(String dataFormatted) {
        this.dataFormatted = dataFormatted;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public Double getTotale() {
        return totale;
    }

    public void setTotale(Double totale) {
        this.totale = totale;
    }

    public Double getSubtotale() {
        return subtotale;
    }

    public void setSubtotale(Double subtotale) {
        this.subtotale = subtotale;
    }

    public String getValuta() {
        return valuta != null ? valuta : "EUR";
    }

    public void setValuta(String valuta) {
        this.valuta = valuta;
    }

    public String getMetodoPagamento() {
        return metodoPagamento;
    }

    public void setMetodoPagamento(String metodoPagamento) {
        this.metodoPagamento = metodoPagamento;
    }

    public String getPiva() {
        return piva;
    }

    public void setPiva(String piva) {
        this.piva = piva;
    }

    @NonNull
    public List<VoceScontrino> getVoci() {
        if (voci == null) voci = new ArrayList<>();
        return voci;
    }

    public void setVoci(List<VoceScontrino> voci) {
        this.voci = voci != null ? voci : new ArrayList<>();
    }

    public void addVoce(@NonNull VoceScontrino voce) {
        getVoci().add(voce);
    }

    public String getTestoGreggio() {
        return testoGreggio;
    }

    public void setTestoGreggio(String testoGreggio) {
        this.testoGreggio = testoGreggio;
    }

    /**
     * Calcola la somma aritmetica di tutte le singole voci presenti.
     */
    public double calcolaSommaVoci() {
        if (voci == null || voci.isEmpty()) return 0.0;
        double somma = 0.0;
        for (VoceScontrino v : voci) {
            somma += v.getPrezzoTotale();
        }
        return Math.round(somma * 100.0) / 100.0;
    }

    /**
     * Restituisce true se la somma delle voci quadra esattamente con il totale rilevato (tolleranza 0.05€).
     */
    public boolean isQuadrato() {
        if (totale == null || totale <= 0.0) return false;
        double somma = calcolaSommaVoci();
        return Math.abs(somma - totale) <= 0.05;
    }

    /**
     * Differenza tra totale dichiarato e somma voci (positivo se mancano voci o ci sono costi extra).
     */
    public double getDiscrepanza() {
        if (totale == null) return 0.0;
        return Math.round((totale - calcolaSommaVoci()) * 100.0) / 100.0;
    }

    /**
     * Serializza l'oggetto in formato JSON compatto per Firestore / Room.
     */
    @NonNull
    public String toJson() {
        try {
            JSONObject obj = new JSONObject();
            if (esercente != null) obj.put("esercente", esercente);
            if (dataFormatted != null) obj.put("data", dataFormatted);
            if (timestamp != null) obj.put("timestamp", timestamp);
            if (totale != null) obj.put("totale", totale);
            if (subtotale != null) obj.put("subtotale", subtotale);
            obj.put("valuta", getValuta());
            if (metodoPagamento != null) obj.put("metodoPagamento", metodoPagamento);
            if (piva != null) obj.put("piva", piva);

            JSONArray arrayVoci = new JSONArray();
            if (voci != null) {
                for (VoceScontrino v : voci) {
                    arrayVoci.put(v.toJsonObject());
                }
            }
            obj.put("voci", arrayVoci);
            return obj.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * Ricostruisce un'istanza di ScontrinoDigitale a partire da una stringa JSON.
     */
    @Nullable
    public static ScontrinoDigitale fromJson(@Nullable String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) return null;
        try {
            JSONObject obj = new JSONObject(jsonStr);
            ScontrinoDigitale s = new ScontrinoDigitale();
            if (obj.has("esercente")) s.setEsercente(obj.getString("esercente"));
            if (obj.has("data")) s.setDataFormatted(obj.getString("data"));
            if (obj.has("timestamp")) s.setTimestamp(obj.getLong("timestamp"));
            if (obj.has("totale")) s.setTotale(obj.getDouble("totale"));
            if (obj.has("subtotale")) s.setSubtotale(obj.getDouble("subtotale"));
            if (obj.has("valuta")) s.setValuta(obj.getString("valuta"));
            if (obj.has("metodoPagamento")) s.setMetodoPagamento(obj.getString("metodoPagamento"));
            if (obj.has("piva")) s.setPiva(obj.getString("piva"));

            if (obj.has("voci")) {
                JSONArray arr = obj.getJSONArray("voci");
                List<VoceScontrino> lista = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    VoceScontrino v = VoceScontrino.fromJsonObject(arr.getJSONObject(i));
                    if (v != null) lista.add(v);
                }
                s.setVoci(lista);
            }
            return s;
        } catch (Exception e) {
            return null;
        }
    }
}
