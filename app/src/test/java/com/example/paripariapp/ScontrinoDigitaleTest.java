package com.example.paripariapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.paripariapp.data.model.ScontrinoDigitale;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

/**
 * Test unitari per {@link ScontrinoDigitale}.
 * Verifica la serializzazione JSON compatta, la deserializzazione
 * e il calcolo della quadratura contabile tra voci e totale.
 */
public class ScontrinoDigitaleTest {

    @Test
    public void testQuadraturaPerfetta() {
        ScontrinoDigitale scontrino = new ScontrinoDigitale();
        scontrino.setEsercente("Trattoria Da Mario");
        scontrino.setDataFormatted("16/09/2026");
        scontrino.setTotale(25.50);

        scontrino.addVoce(new ScontrinoDigitale.VoceScontrino("Pizza Margherita", 1, 8.00, 8.00));
        scontrino.addVoce(new ScontrinoDigitale.VoceScontrino("Pizza Diavola", 1, 9.50, 9.50));
        scontrino.addVoce(new ScontrinoDigitale.VoceScontrino("Birra Media", 2, 4.00, 8.00));

        assertEquals(25.50, scontrino.calcolaSommaVoci(), 0.001);
        assertTrue(scontrino.isQuadrato());
        assertEquals(0.0, scontrino.getDiscrepanza(), 0.001);
    }

    @Test
    public void testDiscrepanzaQuadratura() {
        ScontrinoDigitale scontrino = new ScontrinoDigitale();
        scontrino.setTotale(30.00);

        scontrino.addVoce(new ScontrinoDigitale.VoceScontrino("Pranzo lavoro", 1, 28.00, 28.00));

        assertEquals(28.00, scontrino.calcolaSommaVoci(), 0.001);
        assertFalse(scontrino.isQuadrato());
        assertEquals(2.00, scontrino.getDiscrepanza(), 0.001);
    }

    @Test
    public void testSerializzazioneEDeserializzazioneJson() {
        ScontrinoDigitale originale = new ScontrinoDigitale();
        originale.setEsercente("Supermercato Fresco");
        originale.setDataFormatted("15/09/2026 18:30");
        originale.setTotale(14.80);
        originale.setSubtotale(14.80);
        originale.setPiva("IT12345678901");
        originale.setMetodoPagamento("CARTA");

        originale.addVoce(new ScontrinoDigitale.VoceScontrino("Pane Integrale", 1, 2.50, 2.50));
        originale.addVoce(new ScontrinoDigitale.VoceScontrino("Latte Bio", 2, 1.65, 3.30));
        originale.addVoce(new ScontrinoDigitale.VoceScontrino("Caffè 250g", 1, 4.00, 4.00));
        originale.addVoce(new ScontrinoDigitale.VoceScontrino("Biscotti", 1, 5.00, 5.00));

        String json = originale.toJson();
        assertNotNull(json);

        // Verifica dimensione payload: deve occupare nettamente meno di 2 KB (< 2048 byte)
        int byteSize = json.getBytes(StandardCharsets.UTF_8).length;
        assertTrue("La dimensione JSON (" + byteSize + " byte) deve essere inferiore a 2 KB", byteSize < 2048);

        // Deserializzazione
        ScontrinoDigitale ricostruito = ScontrinoDigitale.fromJson(json);
        assertNotNull(ricostruito);
        assertEquals("Supermercato Fresco", ricostruito.getEsercente());
        assertEquals("15/09/2026 18:30", ricostruito.getDataFormatted());
        assertEquals(14.80, ricostruito.getTotale(), 0.001);
        assertEquals("IT12345678901", ricostruito.getPiva());
        assertEquals(4, ricostruito.getVoci().size());

        ScontrinoDigitale.VoceScontrino voce2 = ricostruito.getVoci().get(1);
        assertEquals("Latte Bio", voce2.getDescrizione());
        assertEquals(2.0, voce2.getQuantita(), 0.001);
        assertEquals(1.65, voce2.getPrezzoUnitario(), 0.001);
        assertEquals(3.30, voce2.getPrezzoTotale(), 0.001);
    }

    @Test
    public void testFromJsonNullOInvalido() {
        org.junit.Assert.assertNull(ScontrinoDigitale.fromJson(null));
        org.junit.Assert.assertNull(ScontrinoDigitale.fromJson("   "));
        org.junit.Assert.assertNull(ScontrinoDigitale.fromJson("{not valid json}"));
    }
}
