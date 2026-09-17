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

    @Test
    public void testSaldiConTassoCambioCongelato() {
        Partecipante alice = new Partecipante("p1", "s1", "Alice", "alice@test.com", SyncStatus.SYNCED);
        Partecipante bob = new Partecipante("p2", "s1", "Bob", "bob@test.com", SyncStatus.SYNCED);
        List<Partecipante> partecipanti = Arrays.asList(alice, bob);

        // Alice paga 100 USD con tasso congelato 0.85 -> 85 EUR
        Spesa spesaUsd = new Spesa("sp_usd", "s1", "Pranzo NY", 100.0, "USD", System.currentTimeMillis(), "Cibo", "p1", null, SyncStatus.SYNCED);
        spesaUsd.setTassoCambio(0.85);

        List<SpesaPartecipante> quote = new ArrayList<>();
        // In USD, 50 ciascuno
        quote.add(new SpesaPartecipante("sp_usd", "p1", 50.0, 0.0, SyncStatus.SYNCED));
        quote.add(new SpesaPartecipante("sp_usd", "p2", 50.0, 0.0, SyncStatus.SYNCED));

        List<Spesa> spese = new ArrayList<>();
        spese.add(spesaUsd);

        // Valuta gruppo EUR: 100 * 0.85 = 85 EUR. Quote: 50 * 0.85 = 42.5 ciascuno.
        // Bilancio Alice: +85 - 42.5 = +42.5 EUR
        // Bilancio Bob: -42.5 EUR
        Map<String, Double> bilanci = CalcolatoreSaldi.calcolaMapBilanci(partecipanti, spese, quote, "EUR");
        assertEquals(42.5, bilanci.get("p1"), 0.001);
        assertEquals(-42.5, bilanci.get("p2"), 0.001);

        List<TrasferimentoSaldo> trasferimenti = CalcolatoreSaldi.calcolaTrasferimenti(partecipanti, spese, quote, "EUR");
        assertEquals(1, trasferimenti.size());
        assertEquals(42.5, trasferimenti.get(0).getImporto(), 0.001);
        assertEquals("p2", trasferimenti.get(0).getDaPartecipanteId());
        assertEquals("p1", trasferimenti.get(0).getAPartecipanteId());
    }

    @Test
    public void testSaldiConQuoteMancantiIsolate() {
        Partecipante alice = new Partecipante("p1", "s1", "Alice", "alice@test.com", SyncStatus.SYNCED);
        Partecipante bob = new Partecipante("p2", "s1", "Bob", "bob@test.com", SyncStatus.SYNCED);
        List<Partecipante> partecipanti = Arrays.asList(alice, bob);

        // Spesa 1: Alice paga 20 EUR con quote definite (10 ciascuno)
        Spesa spesa1 = new Spesa("sp1", "s1", "Spesa 1", 20.0, "EUR", System.currentTimeMillis(), "Cibo", "p1", null, SyncStatus.SYNCED);
        List<SpesaPartecipante> quote = new ArrayList<>();
        quote.add(new SpesaPartecipante("sp1", "p1", 10.0, 0.0, SyncStatus.SYNCED));
        quote.add(new SpesaPartecipante("sp1", "p2", 10.0, 0.0, SyncStatus.SYNCED));

        // Spesa 2: Bob paga 30 EUR, ma per gara asincrona le quote non sono ancora arrivate (lista quote per sp2 vuota)
        Spesa spesa2 = new Spesa("sp2", "s1", "Spesa 2", 30.0, "EUR", System.currentTimeMillis(), "Cibo", "p2", null, SyncStatus.SYNCED);

        List<Spesa> spese = Arrays.asList(spesa1, spesa2);

        // Con il raggruppamento per-spesa, spesa2 fa fallback equo (15 ciascuno) senza corrompere spesa1!
        // Alice: +20 (pagato sp1) - 10 (quota sp1) - 15 (quota equa sp2) = -5 EUR
        // Bob: +30 (pagato sp2) - 10 (quota sp1) - 15 (quota equa sp2) = +5 EUR
        Map<String, Double> bilanci = CalcolatoreSaldi.calcolaMapBilanci(partecipanti, spese, quote, "EUR");
        assertEquals(-5.0, bilanci.get("p1"), 0.001);
        assertEquals(5.0, bilanci.get("p2"), 0.001);
    }

    @Test
    public void testBilanciMembriFiltraArchiviatiEMantieneSaldoZero() {
        Partecipante alice = new Partecipante("p1", "s1", "Alice", "alice@test.com", SyncStatus.SYNCED);
        Partecipante bob = new Partecipante("p2", "s1", "Bob", "bob@test.com", SyncStatus.SYNCED);
        Partecipante charlie = new Partecipante("p3", "s1", "Charlie", "charlie@test.com", SyncStatus.SYNCED);
        charlie.setStato(Partecipante.STATO_ARCHIVIATO);

        List<Partecipante> partecipanti = Arrays.asList(alice, bob, charlie);
        List<Spesa> spese = new ArrayList<>();
        List<SpesaPartecipante> quote = new ArrayList<>();

        List<CalcolatoreSaldi.BilancioMembro> bilanci = CalcolatoreSaldi.calcolaListaBilanciMembri(partecipanti, spese, quote, "EUR");

        // Alice e Bob devono essere presenti anche se hanno saldo 0.00
        assertEquals(2, bilanci.size());
        assertEquals("p1", bilanci.get(0).getPartecipanteId());
        assertEquals(0.0, bilanci.get(0).getSaldo(), 0.001);
        assertEquals("p2", bilanci.get(1).getPartecipanteId());
        assertEquals(0.0, bilanci.get(1).getSaldo(), 0.001);

        // Charlie (archiviato locale) non deve essere presente nella lista
        boolean contieneCharlie = bilanci.stream().anyMatch(b -> "p3".equals(b.getPartecipanteId()));
        org.junit.Assert.assertFalse(contieneCharlie);
    }

    @Test
    public void testSaldiCalcoloOttimizzatoDaMap() {
        Partecipante alice = new Partecipante("p1", "s1", "Alice", "alice@test.com", SyncStatus.SYNCED);
        Partecipante bob = new Partecipante("p2", "s1", "Bob", "bob@test.com", SyncStatus.SYNCED);
        List<Partecipante> partecipanti = Arrays.asList(alice, bob);

        Map<String, Double> mapBilanci = new java.util.HashMap<>();
        mapBilanci.put("p1", 15.0);
        mapBilanci.put("p2", -15.0);

        List<CalcolatoreSaldi.BilancioMembro> bilanci = CalcolatoreSaldi.calcolaListaBilanciMembri(partecipanti, mapBilanci, "EUR");
        assertEquals(2, bilanci.size());
        assertEquals(15.0, bilanci.get(0).getSaldoNetto(), 0.001);
        assertEquals(-15.0, bilanci.get(1).getSaldoNetto(), 0.001);

        List<TrasferimentoSaldo> trasferimenti = CalcolatoreSaldi.calcolaTrasferimenti(partecipanti, mapBilanci, "EUR");
        assertEquals(1, trasferimenti.size());
        assertEquals(15.0, trasferimenti.get(0).getImporto(), 0.001);
        assertEquals("p2", trasferimenti.get(0).getDaPartecipanteId());
        assertEquals("p1", trasferimenti.get(0).getAPartecipanteId());
    }
}
