package com.example.paripariapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.paripariapp.util.CodiceInvitoUtil;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class CodiceInvitoUtilTest {

    @Test
    public void testGeneraCodice_lunghezzaECaratteri() {
        String caratteriPermessi = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

        for (int i = 0; i < 100; i++) {
            String codice = CodiceInvitoUtil.generaCodice();
            assertNotNull(codice);
            assertEquals(6, codice.length());

            for (char c : codice.toCharArray()) {
                assertTrue("Carattere non valido nel codice: " + c, caratteriPermessi.indexOf(c) >= 0);
                assertFalse("Il codice contiene caratteri ambigui: " + c, c == '0' || c == 'O' || c == '1' || c == 'I');
            }
        }
    }

    @Test
    public void testGeneraCodice_unicita() {
        Set<String> codici = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            codici.add(CodiceInvitoUtil.generaCodice());
        }
        // Con 32^6 combinazioni (più di 1 miliardo), 500 codici devono essere tutti unici
        assertEquals(500, codici.size());
    }

    @Test
    public void testNormalizzaCodice() {
        assertEquals("ABC234", CodiceInvitoUtil.normalizzaCodice("abc234"));
        assertEquals("ABC234", CodiceInvitoUtil.normalizzaCodice("  a b c 2 3 4  "));
        assertEquals("XYZ789", CodiceInvitoUtil.normalizzaCodice("xyz789"));
        assertEquals("", CodiceInvitoUtil.normalizzaCodice(null));
        assertEquals("", CodiceInvitoUtil.normalizzaCodice("   "));
    }
}
