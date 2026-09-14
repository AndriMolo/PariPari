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

    @Test
    public void testGeneraLinkInvito() {
        String link = CodiceInvitoUtil.generaLinkInvito("abc234");
        assertEquals("https://paripari-app-2026.web.app/join?code=ABC234", link);

        String linkConSpazi = CodiceInvitoUtil.generaLinkInvito("  x y z 9 9 9 ");
        assertEquals("https://paripari-app-2026.web.app/join?code=XYZ999", linkConSpazi);
    }

    @Test
    public void testEstraiCodiceDaUrlString() {
        // Query param standard
        assertEquals("ABC123", CodiceInvitoUtil.estraiCodiceDaUrlString("https://paripari-app-2026.web.app/join?code=abc123"));
        assertEquals("ABC123", CodiceInvitoUtil.estraiCodiceDaUrlString("paripari://join?code=abc123"));
        assertEquals("ABC123", CodiceInvitoUtil.estraiCodiceDaUrlString("https://paripari.app/join?code=abc123&ref=share"));

        // Path standard
        assertEquals("XYZ789", CodiceInvitoUtil.estraiCodiceDaUrlString("https://paripari-app-2026.web.app/join/xyz789"));
        assertEquals("XYZ789", CodiceInvitoUtil.estraiCodiceDaUrlString("https://paripari.app/join/xyz789?ref=app"));

        // Null o invalidi
        assertEquals(null, CodiceInvitoUtil.estraiCodiceDaUrlString(null));
        assertEquals(null, CodiceInvitoUtil.estraiCodiceDaUrlString(""));
        assertEquals(null, CodiceInvitoUtil.estraiCodiceDaUrlString("https://google.com"));
    }
}
