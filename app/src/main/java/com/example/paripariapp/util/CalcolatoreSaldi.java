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
        Map<String, Double> bilanci = new HashMap<>();

        for (Partecipante p : partecipanti) {
            nomiMap.put(p.getId(), p.getNome());
            bilanci.put(p.getId(), 0.0);
        }

        Map<String, Spesa> spesaMap = new HashMap<>();

        // 1. Aggiunge gli importi anticipati da ciascuno (+ credito)
        for (Spesa s : spese) {
            spesaMap.put(s.getId(), s);
            String pagatoreId = s.getPagatoDaId();
            if (bilanci.containsKey(pagatoreId)) {
                bilanci.put(pagatoreId, bilanci.get(pagatoreId) + s.getImporto());
            }
        }

        // 2. Sottrae le quote dovute da ciascuno (- debito) tenendo conto di quanto già pagato
        if (quote != null && !quote.isEmpty()) {
            for (SpesaPartecipante q : quote) {
                String debitoreId = q.getPartecipanteId();
                double quotaResidua = q.getQuota() - q.getQuotaPagata();
                if (bilanci.containsKey(debitoreId)) {
                    bilanci.put(debitoreId, bilanci.get(debitoreId) - quotaResidua);
                }
                Spesa s = spesaMap.get(q.getSpesaId());
                if (s != null && q.getQuotaPagata() > 0) {
                    String pagatoreId = s.getPagatoDaId();
                    if (bilanci.containsKey(pagatoreId)) {
                        bilanci.put(pagatoreId, bilanci.get(pagatoreId) - q.getQuotaPagata());
                    }
                }
            }
        } else {
            // Divisione equa di default se non sono presenti quote esplicite
            for (Spesa s : spese) {
                double quotaEqua = s.getImporto() / partecipanti.size();
                for (Partecipante p : partecipanti) {
                    bilanci.put(p.getId(), bilanci.get(p.getId()) - quotaEqua);
                }
            }
        }

        // 3. Separa debitori e creditori
        List<Map.Entry<String, Double>> debitori = new ArrayList<>();
        List<Map.Entry<String, Double>> creditori = new ArrayList<>();

        for (Map.Entry<String, Double> entry : bilanci.entrySet()) {
            double saldo = Math.round(entry.getValue() * 100.0) / 100.0;
            if (saldo < -0.01) {
                debitori.add(new AbstractMap.SimpleEntry<>(entry.getKey(), -saldo));
            } else if (saldo > 0.01) {
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

            if (importoMinimo > 0.009) {
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
