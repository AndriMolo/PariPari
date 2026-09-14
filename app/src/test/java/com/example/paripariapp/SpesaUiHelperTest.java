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
import java.util.List;
import java.util.Map;

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
        // 3.33 + 3.33 + 3.34 = 10.00
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
}
