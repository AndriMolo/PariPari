package com.example.paripariapp.util;

import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.data.model.SpesaPartecipante;
import com.example.paripariapp.data.model.TrasferimentoSaldo;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CalcolatoreSaldi {

    public static class BilancioMembro {
        private final String partecipanteId;
        private final String nomePartecipante;
        private final double saldoNetto;
        private final String valuta;

        public BilancioMembro(String partecipanteId, String nomePartecipante, double saldoNetto, String valuta) {
            this.partecipanteId = partecipanteId;
            this.nomePartecipante = nomePartecipante;
            this.saldoNetto = saldoNetto;
            this.valuta = valuta;
        }

        public String getPartecipanteId() { return partecipanteId; }
        public String getNomePartecipante() { return nomePartecipante; }
        public double getSaldoNetto() { return saldoNetto; }
        public String getValuta() { return valuta; }
    }

    public static Map<String, Double> calcolaMapBilanci(
            List<Partecipante> partecipanti,
            List<Spesa> spese,
            List<SpesaPartecipante> quote
    ) {
        Map<String, Double> bilanci = new HashMap<>();
        if (partecipanti == null || partecipanti.isEmpty()) {
            return bilanci;
        }

        for (Partecipante p : partecipanti) {
            bilanci.put(p.getId(), 0.0);
        }

        if (spese == null || spese.isEmpty()) {
            return bilanci;
        }

        Map<String, Spesa> spesaMap = new HashMap<>();

        // 1. Aggiunge gli importi anticipati da ciascuno (+ credito)
        for (Spesa s : spese) {
            spesaMap.put(s.getId(), s);
            String pagatoreId = s.getPagatoDaId();
            if (bilanci.containsKey(pagatoreId)) {
                Double curr = bilanci.get(pagatoreId);
                bilanci.put(pagatoreId, (curr != null ? curr : 0.0) + s.getImporto());
            }
        }

        // 2. Sottrae le quote dovute da ciascuno (- debito) tenendo conto di quanto già pagato
        if (quote != null && !quote.isEmpty()) {
            for (SpesaPartecipante q : quote) {
                String debitoreId = q.getPartecipanteId();
                double quotaResidua = q.getQuota() - q.getQuotaPagata();
                if (bilanci.containsKey(debitoreId)) {
                    Double curr = bilanci.get(debitoreId);
                    bilanci.put(debitoreId, (curr != null ? curr : 0.0) - quotaResidua);
                }
                Spesa s = spesaMap.get(q.getSpesaId());
                if (s != null && q.getQuotaPagata() > 0) {
                    String pagatoreId = s.getPagatoDaId();
                    if (bilanci.containsKey(pagatoreId)) {
                        Double curr = bilanci.get(pagatoreId);
                        bilanci.put(pagatoreId, (curr != null ? curr : 0.0) - q.getQuotaPagata());
                    }
                }
            }
        } else {
            // Divisione equa di default se non sono presenti quote esplicite
            for (Spesa s : spese) {
                double quotaEqua = s.getImporto() / partecipanti.size();
                for (Partecipante p : partecipanti) {
                    Double curr = bilanci.get(p.getId());
                    bilanci.put(p.getId(), (curr != null ? curr : 0.0) - quotaEqua);
                }
            }
        }

        return bilanci;
    }

    public static List<BilancioMembro> calcolaListaBilanciMembri(
            List<Partecipante> partecipanti,
            List<Spesa> spese,
            List<SpesaPartecipante> quote,
            String valutaPredefinita
    ) {
        List<BilancioMembro> lista = new ArrayList<>();
        if (partecipanti == null || partecipanti.isEmpty()) {
            return lista;
        }

        Map<String, Double> mapBilanci = calcolaMapBilanci(partecipanti, spese, quote);

        for (Partecipante p : partecipanti) {
            Double val = mapBilanci.get(p.getId());
            double saldoRaw = val != null ? val : 0.0;
            double saldoArrotondato = Math.round(saldoRaw * 100.0) / 100.0;
            lista.add(new BilancioMembro(p.getId(), p.getNome(), saldoArrotondato, valutaPredefinita != null ? valutaPredefinita : "EUR"));
        }

        return lista;
    }

    public static List<TrasferimentoSaldo> calcolaTrasferimenti(
            List<Partecipante> partecipanti,
            List<Spesa> spese,
            List<SpesaPartecipante> quote,
            String valutaPredefinita
    ) {
        List<TrasferimentoSaldo> trasferimenti = new ArrayList<>();
        if (partecipanti == null || partecipanti.isEmpty() || spese == null || spese.isEmpty()) {
            return trasferimenti;
        }

        Map<String, String> nomiMap = new HashMap<>();
        for (Partecipante p : partecipanti) {
            nomiMap.put(p.getId(), p.getNome());
        }

        Map<String, Double> bilanci = calcolaMapBilanci(partecipanti, spese, quote);

        // 3. Separa debitori e creditori
        List<Map.Entry<String, Double>> debitori = new ArrayList<>();
        List<Map.Entry<String, Double>> creditori = new ArrayList<>();

        for (Map.Entry<String, Double> entry : bilanci.entrySet()) {
            double saldo = Math.round(entry.getValue() * 100.0) / 100.0;
            if (saldo < -0.001) {
                debitori.add(new AbstractMap.SimpleEntry<>(entry.getKey(), -saldo));
            } else if (saldo > 0.001) {
                creditori.add(new AbstractMap.SimpleEntry<>(entry.getKey(), saldo));
            }
        }

        // 4. Algoritmo greedy per minimizzare il numero di scambi
        int iDeb = 0;
        int iCred = 0;

        while (iDeb < debitori.size() && iCred < creditori.size()) {
            Map.Entry<String, Double> deb = debitori.get(iDeb);
            Map.Entry<String, Double> cred = creditori.get(iCred);

            double importoMinimo = Math.min(deb.getValue(), cred.getValue());
            importoMinimo = Math.round(importoMinimo * 100.0) / 100.0;

            if (importoMinimo > 0.001) {
                String nomeDa = nomiMap.getOrDefault(deb.getKey(), "—");
                String nomeA = nomiMap.getOrDefault(cred.getKey(), "—");

                trasferimenti.add(new TrasferimentoSaldo(
                        deb.getKey(),
                        nomeDa,
                        cred.getKey(),
                        nomeA,
                        importoMinimo,
                        valutaPredefinita != null ? valutaPredefinita : "EUR"
                ));
            }

            deb.setValue(deb.getValue() - importoMinimo);
            cred.setValue(cred.getValue() - importoMinimo);

            if (deb.getValue() < 0.01) iDeb++;
            if (cred.getValue() < 0.01) iCred++;
        }

        return trasferimenti;
    }
}
