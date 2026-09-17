package com.example.paripariapp.util;

import android.content.Context;

import androidx.annotation.Nullable;

import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.data.model.SpesaPartecipante;
import com.example.paripariapp.data.model.TrasferimentoSaldo;
import com.example.paripariapp.data.repository.CurrencyRepository;

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
        public double getSaldo() { return saldoNetto; }
        public String getValuta() { return valuta; }
    }

    public static double convertiValuta(double importo, String valutaOrigine, String valutaDestinazione, @Nullable Context context) {
        if (valutaOrigine == null || valutaDestinazione == null || valutaOrigine.equalsIgnoreCase(valutaDestinazione)) {
            return importo;
        }
        if (context == null) {
            return importo;
        }
        CurrencyRepository repo = CurrencyRepository.getInstance(context.getApplicationContext());
        double rateOrigine = repo.getRate(valutaOrigine);
        double rateDestinazione = repo.getRate(valutaDestinazione);
        return (importo / rateOrigine) * rateDestinazione;
    }

    public static double convertiImportoSpesa(double importo, @Nullable Spesa s, String valutaDestinazione, @Nullable Context context) {
        if (s == null) {
            return importo;
        }
        String valutaOrigine = s.getValuta();
        if (valutaOrigine == null || valutaDestinazione == null || valutaOrigine.equalsIgnoreCase(valutaDestinazione)) {
            return importo;
        }
        // Se la spesa ha un tasso congelato valido salvato all'inserimento, usalo prioritariamente
        if (s.getTassoCambio() > 0.0) {
            return importo * s.getTassoCambio();
        }
        // Fallback dinamico dal repository tassi se context disponibile
        return convertiValuta(importo, valutaOrigine, valutaDestinazione, context);
    }

    public static Map<String, Double> calcolaMapBilanci(
            List<Partecipante> partecipanti,
            List<Spesa> spese,
            List<SpesaPartecipante> quote
    ) {
        return calcolaMapBilanci(partecipanti, spese, quote, "EUR", null);
    }

    public static Map<String, Double> calcolaMapBilanci(
            List<Partecipante> partecipanti,
            List<Spesa> spese,
            List<SpesaPartecipante> quote,
            String valutaPredefinita
    ) {
        return calcolaMapBilanci(partecipanti, spese, quote, valutaPredefinita, null);
    }

    public static Map<String, Double> calcolaMapBilanci(
            List<Partecipante> partecipanti,
            List<Spesa> spese,
            List<SpesaPartecipante> quote,
            String valutaPredefinita,
            @Nullable Context context
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

        String valutaDef = valutaPredefinita != null ? valutaPredefinita : "EUR";

        // Mappa delle quote raggruppate per spesaId con capacità pre-allocata
        int capacity = quote != null ? Math.max(16, quote.size() * 4 / 3 + 1) : 16;
        Map<String, List<SpesaPartecipante>> quotePerSpesa = new HashMap<>(capacity);
        if (quote != null) {
            for (SpesaPartecipante q : quote) {
                if (q != null && q.getSpesaId() != null) {
                    quotePerSpesa.computeIfAbsent(q.getSpesaId(), k -> new ArrayList<>()).add(q);
                }
            }
        }

        // Singola passata: accredito anticipo pagatore + addebito quote
        for (Spesa s : spese) {
            if (s == null) continue;
            String pagatoreId = s.getPagatoDaId();
            double importoConv = convertiImportoSpesa(s.getImporto(), s, valutaDef, context);
            if (pagatoreId != null && bilanci.containsKey(pagatoreId)) {
                Double curr = bilanci.get(pagatoreId);
                bilanci.put(pagatoreId, (curr != null ? curr : 0.0) + importoConv);
            }

            List<SpesaPartecipante> quoteDellaSpesa = quotePerSpesa.get(s.getId());
            if (quoteDellaSpesa != null && !quoteDellaSpesa.isEmpty()) {
                for (SpesaPartecipante q : quoteDellaSpesa) {
                    if (q == null) continue;
                    String debitoreId = q.getPartecipanteId();
                    double quotaResidua = q.getQuota() - q.getQuotaPagata();
                    double quotaResiduaConv = convertiImportoSpesa(quotaResidua, s, valutaDef, context);
                    if (debitoreId != null && bilanci.containsKey(debitoreId)) {
                        Double curr = bilanci.get(debitoreId);
                        bilanci.put(debitoreId, (curr != null ? curr : 0.0) - quotaResiduaConv);
                    }
                    if (q.getQuotaPagata() > 0 && pagatoreId != null && bilanci.containsKey(pagatoreId)) {
                        double quotaPagataConv = convertiImportoSpesa(q.getQuotaPagata(), s, valutaDef, context);
                        Double curr = bilanci.get(pagatoreId);
                        bilanci.put(pagatoreId, (curr != null ? curr : 0.0) - quotaPagataConv);
                    }
                }
            } else {
                // Fallback equo per-spesa se la singola spesa non ha quote registrate
                double quotaEqua = importoConv / partecipanti.size();
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
            Map<String, Double> mapBilanci,
            String valutaPredefinita
    ) {
        List<BilancioMembro> lista = new ArrayList<>();
        if (partecipanti == null || partecipanti.isEmpty() || mapBilanci == null) {
            return lista;
        }

        String valuta = valutaPredefinita != null ? valutaPredefinita : "EUR";
        for (Partecipante p : partecipanti) {
            if (p.isArchiviatoLocale()) {
                continue;
            }
            Double val = mapBilanci.get(p.getId());
            double saldoRaw = val != null ? val : 0.0;
            double saldoArrotondato = Math.round(saldoRaw * 100.0) / 100.0;
            lista.add(new BilancioMembro(p.getId(), p.getNome(), saldoArrotondato, valuta));
        }

        return lista;
    }

    public static List<BilancioMembro> calcolaListaBilanciMembri(
            List<Partecipante> partecipanti,
            List<Spesa> spese,
            List<SpesaPartecipante> quote,
            String valutaPredefinita
    ) {
        return calcolaListaBilanciMembri(partecipanti, spese, quote, valutaPredefinita, null);
    }

    public static List<BilancioMembro> calcolaListaBilanciMembri(
            List<Partecipante> partecipanti,
            List<Spesa> spese,
            List<SpesaPartecipante> quote,
            String valutaPredefinita,
            @Nullable Context context
    ) {
        if (partecipanti == null || partecipanti.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, Double> mapBilanci = calcolaMapBilanci(partecipanti, spese, quote, valutaPredefinita, context);
        return calcolaListaBilanciMembri(partecipanti, mapBilanci, valutaPredefinita);
    }

    public static List<TrasferimentoSaldo> calcolaTrasferimenti(
            List<Partecipante> partecipanti,
            Map<String, Double> bilanci,
            String valutaPredefinita
    ) {
        List<TrasferimentoSaldo> trasferimenti = new ArrayList<>();
        if (partecipanti == null || partecipanti.isEmpty() || bilanci == null || bilanci.isEmpty()) {
            return trasferimenti;
        }

        Map<String, String> nomiMap = new HashMap<>(partecipanti.size() * 4 / 3 + 1);
        for (Partecipante p : partecipanti) {
            nomiMap.put(p.getId(), p.getNome());
        }

        // Separazione debitori e creditori
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

        // Ordinamento decrescente per garantire determinismo e minimizzare le transazioni
        java.util.Collections.sort(debitori, (a, b) -> Double.compare(b.getValue(), a.getValue()));
        java.util.Collections.sort(creditori, (a, b) -> Double.compare(b.getValue(), a.getValue()));

        // Compensazione diretta (greedy) dei debiti e crediti
        int iDeb = 0;
        int iCred = 0;
        String valuta = valutaPredefinita != null ? valutaPredefinita : "EUR";

        while (iDeb < debitori.size() && iCred < creditori.size()) {
            Map.Entry<String, Double> deb = debitori.get(iDeb);
            Map.Entry<String, Double> cred = creditori.get(iCred);

            double importoMinimo = Math.min(deb.getValue(), cred.getValue());
            importoMinimo = Math.round(importoMinimo * 100.0) / 100.0;

            if (importoMinimo > 0.001) {
                String nomeDa = nomiMap.getOrDefault(deb.getKey(), "");
                String nomeA = nomiMap.getOrDefault(cred.getKey(), "");

                trasferimenti.add(new TrasferimentoSaldo(
                        deb.getKey(),
                        nomeDa,
                        cred.getKey(),
                        nomeA,
                        importoMinimo,
                        valuta
                ));
            }

            deb.setValue(deb.getValue() - importoMinimo);
            cred.setValue(cred.getValue() - importoMinimo);

            if (deb.getValue() < 0.01) iDeb++;
            if (cred.getValue() < 0.01) iCred++;
        }

        return trasferimenti;
    }

    public static List<TrasferimentoSaldo> calcolaTrasferimenti(
            List<Partecipante> partecipanti,
            List<Spesa> spese,
            List<SpesaPartecipante> quote,
            String valutaPredefinita
    ) {
        return calcolaTrasferimenti(partecipanti, spese, quote, valutaPredefinita, null);
    }

    public static List<TrasferimentoSaldo> calcolaTrasferimenti(
            List<Partecipante> partecipanti,
            List<Spesa> spese,
            List<SpesaPartecipante> quote,
            String valutaPredefinita,
            @Nullable Context context
    ) {
        if (partecipanti == null || partecipanti.isEmpty() || spese == null || spese.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, Double> bilanci = calcolaMapBilanci(partecipanti, spese, quote, valutaPredefinita, context);
        return calcolaTrasferimenti(partecipanti, bilanci, valutaPredefinita);
    }
}
