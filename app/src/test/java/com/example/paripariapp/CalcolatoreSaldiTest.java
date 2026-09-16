package com.example.paripariapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.data.model.SpesaPartecipante;
import com.example.paripariapp.data.model.SyncStatus;
import com.example.paripariapp.data.model.TrasferimentoSaldo;
import com.example.paripariapp.util.CalcolatoreSaldi;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class CalcolatoreSaldiTest {

    @Test
    public void testSaldiConRimborso() {
        Partecipante alice = new Partecipante("p1", "s1", "Alice", "alice@test.com", SyncStatus.SYNCED);
        Partecipante bob = new Partecipante("p2", "s1", "Bob", "bob@test.com", SyncStatus.SYNCED);
        List<Partecipante> partecipanti = Arrays.asList(alice, bob);

        // Spesa: Alice paga 20 EUR per entrambi (10 ciascuno)
        Spesa spesa1 = new Spesa("sp1", "s1", "Cena", 20.0, "EUR", System.currentTimeMillis(), "Cibo", "p1", null, SyncStatus.SYNCED);
        List<SpesaPartecipante> quote = new ArrayList<>();
        quote.add(new SpesaPartecipante("sp1", "p1", 10.0, 0.0, SyncStatus.SYNCED));
        quote.add(new SpesaPartecipante("sp1", "p2", 10.0, 0.0, SyncStatus.SYNCED));

        List<Spesa> spese = new ArrayList<>();
        spese.add(spesa1);

        // Prima del rimborso: Bob deve 10 ad Alice
        Map<String, Double> bilanciPrima = CalcolatoreSaldi.calcolaMapBilanci(partecipanti, spese, quote);
        assertEquals(10.0, bilanciPrima.get("p1"), 0.001);
        assertEquals(-10.0, bilanciPrima.get("p2"), 0.001);

        List<TrasferimentoSaldo> trasferimentiPrima = CalcolatoreSaldi.calcolaTrasferimenti(partecipanti, spese, quote, "EUR");
        assertEquals(1, trasferimentiPrima.size());
        assertEquals("p2", trasferimentiPrima.get(0).getDaPartecipanteId());
        assertEquals("p1", trasferimentiPrima.get(0).getAPartecipanteId());
        assertEquals(10.0, trasferimentiPrima.get(0).getImporto(), 0.001);

        // Ora Bob rimborsa Alice 10 EUR
        Spesa rimborso = new Spesa("sp2", "s1", "Rimborso: da Bob a Alice", 10.0, "EUR", System.currentTimeMillis(), "Pareggio", "p2", null, SyncStatus.SYNCED);
        quote.add(new SpesaPartecipante("sp2", "p1", 10.0, 0.0, SyncStatus.SYNCED));
        spese.add(rimborso);

        Map<String, Double> bilanciDopo = CalcolatoreSaldi.calcolaMapBilanci(partecipanti, spese, quote);
        assertEquals(0.0, bilanciDopo.get("p1"), 0.001);
        assertEquals(0.0, bilanciDopo.get("p2"), 0.001);

        List<TrasferimentoSaldo> trasferimentiDopo = CalcolatoreSaldi.calcolaTrasferimenti(partecipanti, spese, quote, "EUR");
        assertTrue(trasferimentiDopo.isEmpty());
    }
}
