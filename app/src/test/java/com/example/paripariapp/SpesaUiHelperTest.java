package com.example.paripariapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.SpesaPartecipante;
import com.example.paripariapp.data.model.SyncStatus;
import com.example.paripariapp.ui.view.SpesaUiHelper;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SpesaUiHelperTest {

    @Test
    public void testParseImporto() {
        assertEquals(10.50, SpesaUiHelper.parseImporto("10.50"), 0.001);
        assertEquals(10.50, SpesaUiHelper.parseImporto("10,50"), 0.001);
        assertEquals(100.0, SpesaUiHelper.parseImporto(" 100 "), 0.001);
        assertEquals(0.0, SpesaUiHelper.parseImporto(""), 0.001);
        assertEquals(0.0, SpesaUiHelper.parseImporto(null), 0.001);
        assertEquals(0.0, SpesaUiHelper.parseImporto("abc"), 0.001);
    }

    @Test
    public void testIsSommaPercentualiValida() {
        assertTrue(SpesaUiHelper.isSommaPercentualiValida(100.0));
        assertTrue(SpesaUiHelper.isSommaPercentualiValida(99.98));
        assertTrue(SpesaUiHelper.isSommaPercentualiValida(100.04));
        assertFalse(SpesaUiHelper.isSommaPercentualiValida(99.0));
        assertFalse(SpesaUiHelper.isSommaPercentualiValida(101.0));
    }

    @Test
    public void testCalcolaDivisioneEqua_quadraturaCentesimi() {
        Partecipante p1 = new Partecipante("1", "s1", "Alice", null, SyncStatus.SYNCED);
        Partecipante p2 = new Partecipante("2", "s1", "Bob", null, SyncStatus.SYNCED);
        Partecipante p3 = new Partecipante("3", "s1", "Charlie", null, SyncStatus.SYNCED);

        List<Partecipante> tutti = new ArrayList<>();
        tutti.add(p1);
        tutti.add(p2);
        tutti.add(p3);

        List<Partecipante> inclusi = new ArrayList<>(tutti);

        List<SpesaPartecipante> quote = SpesaUiHelper.calcolaDivisioneEqua("spesa1", 10.00, inclusi, tutti, SyncStatus.PENDING_INSERT);

        assertEquals(3, quote.size());
        double somma = 0.0;
        for (SpesaPartecipante q : quote) {
            somma += q.getQuota();
        }
        assertEquals(10.00, somma, 0.0001);
        assertEquals(3.33, quote.get(0).getQuota(), 0.001);
        assertEquals(3.33, quote.get(1).getQuota(), 0.001);
        assertEquals(3.34, quote.get(2).getQuota(), 0.001);
    }

    @Test
    public void testCalcolaDivisioneEqua_conEsclusi() {
        Partecipante p1 = new Partecipante("1", "s1", "Alice", null, SyncStatus.SYNCED);
        Partecipante p2 = new Partecipante("2", "s1", "Bob", null, SyncStatus.SYNCED);
        Partecipante p3 = new Partecipante("3", "s1", "Charlie", null, SyncStatus.SYNCED);

        List<Partecipante> tutti = new ArrayList<>();
        tutti.add(p1);
        tutti.add(p2);
        tutti.add(p3);

        List<Partecipante> inclusi = new ArrayList<>();
        inclusi.add(p1);
        inclusi.add(p2);

        List<SpesaPartecipante> quote = SpesaUiHelper.calcolaDivisioneEqua("spesa1", 20.00, inclusi, tutti, SyncStatus.PENDING_INSERT);

        assertEquals(3, quote.size());
        double somma = 0.0;
        for (SpesaPartecipante q : quote) {
            somma += q.getQuota();
            if (q.getPartecipanteId().equals("3")) {
                assertEquals(0.0, q.getQuota(), 0.001);
            }
        }
        assertEquals(20.00, somma, 0.0001);
    }

    @Test
    public void testCalcolaDivisionePercentuale() {
        Partecipante p1 = new Partecipante("1", "s1", "Alice", null, SyncStatus.SYNCED);
        Partecipante p2 = new Partecipante("2", "s1", "Bob", null, SyncStatus.SYNCED);

        List<Partecipante> tutti = new ArrayList<>();
        tutti.add(p1);
        tutti.add(p2);

        Map<String, Double> percentuali = new HashMap<>();
        percentuali.put("1", 70.0);
        percentuali.put("2", 30.0);

        List<SpesaPartecipante> quote = SpesaUiHelper.calcolaDivisionePercentuale("spesa1", 50.00, tutti, tutti, percentuali, SyncStatus.PENDING_INSERT);

        assertEquals(2, quote.size());
        assertEquals(35.00, quote.get(0).getQuota(), 0.001);
        assertEquals(15.00, quote.get(1).getQuota(), 0.001);
    }

    @Test
    public void testBilanciaPercentuali_scenarioUtente() {
        Partecipante p1 = new Partecipante("1", "s1", "Alice", null, SyncStatus.SYNCED);
        Partecipante p2 = new Partecipante("2", "s1", "Bob", null, SyncStatus.SYNCED);
        Partecipante p3 = new Partecipante("3", "s1", "Charlie", null, SyncStatus.SYNCED);

        List<Partecipante> inclusi = new ArrayList<>();
        inclusi.add(p1);
        inclusi.add(p2);
        inclusi.add(p3);

        Map<String, Double> initial = SpesaUiHelper.calcolaDivisioneInizialePercentuale(inclusi);
        assertEquals(3, initial.size());
        assertEquals(100.0, initial.get("1") + initial.get("2") + initial.get("3"), 0.01);

        // Step 1: Modifica p1 a 60%
        Set<String> locked = new HashSet<>();
        locked.add("1");
        Map<String, Double> step1 = SpesaUiHelper.bilanciaPercentuali(inclusi, "1", 60.0, locked, initial);
        assertEquals(60.0, step1.get("1"), 0.01);
        assertEquals(20.0, step1.get("2"), 0.01);
        assertEquals(20.0, step1.get("3"), 0.01);
        assertEquals(100.0, step1.get("1") + step1.get("2") + step1.get("3"), 0.01);

        // Step 2: Modifica p2 a 30%
        locked.add("2");
        Map<String, Double> step2 = SpesaUiHelper.bilanciaPercentuali(inclusi, "2", 30.0, locked, step1);
        assertEquals(60.0, step2.get("1"), 0.01);
        assertEquals(30.0, step2.get("2"), 0.01);
        assertEquals(10.0, step2.get("3"), 0.01);
        assertEquals(100.0, step2.get("1") + step2.get("2") + step2.get("3"), 0.01);
    }

    @Test
    public void testBilanciaImporti_scenarioUtente() {
        Partecipante p1 = new Partecipante("1", "s1", "Alice", null, SyncStatus.SYNCED);
        Partecipante p2 = new Partecipante("2", "s1", "Bob", null, SyncStatus.SYNCED);
        Partecipante p3 = new Partecipante("3", "s1", "Charlie", null, SyncStatus.SYNCED);

        List<Partecipante> inclusi = new ArrayList<>();
        inclusi.add(p1);
        inclusi.add(p2);
        inclusi.add(p3);

        double total = 90.00;
        Map<String, Double> initial = SpesaUiHelper.calcolaDivisioneInizialeImporto(total, inclusi);
        assertEquals(30.00, initial.get("1"), 0.01);
        assertEquals(30.00, initial.get("2"), 0.01);
        assertEquals(30.00, initial.get("3"), 0.01);

        // Step 1: Modifica p1 a 50.00€
        Set<String> locked = new HashSet<>();
        locked.add("1");
        Map<String, Double> step1 = SpesaUiHelper.bilanciaImporti(total, inclusi, "1", 50.00, locked, initial);
        assertEquals(50.00, step1.get("1"), 0.01);
        assertEquals(20.00, step1.get("2"), 0.01);
        assertEquals(20.00, step1.get("3"), 0.01);
        assertEquals(90.00, step1.get("1") + step1.get("2") + step1.get("3"), 0.01);

        // Step 2: Modifica p2 a 30.00€
        locked.add("2");
        Map<String, Double> step2 = SpesaUiHelper.bilanciaImporti(total, inclusi, "2", 30.00, locked, step1);
        assertEquals(50.00, step2.get("1"), 0.01);
        assertEquals(30.00, step2.get("2"), 0.01);
        assertEquals(10.00, step2.get("3"), 0.01);
        assertEquals(90.00, step2.get("1") + step2.get("2") + step2.get("3"), 0.01);
    }

    @Test
    public void testCalcolaDivisionePerImporto() {
        Partecipante p1 = new Partecipante("1", "s1", "Alice", null, SyncStatus.SYNCED);
        Partecipante p2 = new Partecipante("2", "s1", "Bob", null, SyncStatus.SYNCED);
        Partecipante p3 = new Partecipante("3", "s1", "Charlie", null, SyncStatus.SYNCED);

        List<Partecipante> tutti = new ArrayList<>();
        tutti.add(p1);
        tutti.add(p2);
        tutti.add(p3);

        List<Partecipante> inclusi = new ArrayList<>();
        inclusi.add(p1);
        inclusi.add(p2);

        Map<String, Double> importi = new HashMap<>();
        importi.put("1", 45.00);
        importi.put("2", 15.00);

        List<SpesaPartecipante> quote = SpesaUiHelper.calcolaDivisionePerImporto("spesa1", 60.00, inclusi, tutti, importi, SyncStatus.PENDING_INSERT);
        assertEquals(3, quote.size());
        assertEquals(45.00, quote.get(0).getQuota(), 0.001);
        assertEquals(15.00, quote.get(1).getQuota(), 0.001);
        assertEquals(0.00, quote.get(2).getQuota(), 0.001);
    }
}
