package com.example.paripariapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.paripariapp.util.CategoriaUtil;

import org.junit.Test;

public class CategoriaUtilTest {

    @Test
    public void testIndovinaCategoriaDaTitolo() {
        assertEquals(CategoriaUtil.CAT_CIBO, CategoriaUtil.indovinaCategoriaDaTitolo("Pizza da Michele"));
        assertEquals(CategoriaUtil.CAT_BAR, CategoriaUtil.indovinaCategoriaDaTitolo("Aperitivo e birre"));
        assertEquals(CategoriaUtil.CAT_SPESA, CategoriaUtil.indovinaCategoriaDaTitolo("Spesa Esselunga"));
        assertEquals(CategoriaUtil.CAT_TRASPORTI, CategoriaUtil.indovinaCategoriaDaTitolo("Biglietto treno Milano"));
        assertEquals(CategoriaUtil.CAT_ALLOGGIO, CategoriaUtil.indovinaCategoriaDaTitolo("Prenotazione Airbnb"));
        assertEquals(CategoriaUtil.CAT_SVAGO, CategoriaUtil.indovinaCategoriaDaTitolo("Biglietti cinema"));
        assertEquals(CategoriaUtil.CAT_SHOPPING, CategoriaUtil.indovinaCategoriaDaTitolo("Regalo compleanno"));
        assertEquals(CategoriaUtil.CAT_SALUTE, CategoriaUtil.indovinaCategoriaDaTitolo("Farmacia e medicine"));
        assertNull(CategoriaUtil.indovinaCategoriaDaTitolo("Qualcosa"));
        assertNull(CategoriaUtil.indovinaCategoriaDaTitolo(""));
        assertNull(CategoriaUtil.indovinaCategoriaDaTitolo(null));
    }

    @Test
    public void testContieneParola() {
        // Delimitazione corretta per parole brevi
        org.junit.Assert.assertTrue(CategoriaUtil.contieneParola("aperitivo al bar", "bar"));
        org.junit.Assert.assertTrue(CategoriaUtil.contieneParola("bar & drinks", "bar"));
        org.junit.Assert.assertTrue(CategoriaUtil.contieneParola("cocktail-bar!", "bar"));
        org.junit.Assert.assertTrue(CategoriaUtil.contieneParola("un café con leche", "café"));
        org.junit.Assert.assertTrue(CategoriaUtil.contieneParola("pub crawl", "pub"));
        org.junit.Assert.assertTrue(CategoriaUtil.contieneParola("zug nach berlin", "zug"));
        org.junit.Assert.assertTrue(CategoriaUtil.contieneParola("vol pour paris", "vol"));

        // Prevenzione collisioni da sottostringa
        org.junit.Assert.assertFalse(CategoriaUtil.contieneParola("volo per barcellona", "bar"));
        org.junit.Assert.assertFalse(CategoriaUtil.contieneParola("treno per bari", "bar"));
        org.junit.Assert.assertFalse(CategoriaUtil.contieneParola("gita in barca", "bar"));
        org.junit.Assert.assertFalse(CategoriaUtil.contieneParola("piazza repubblica", "pub"));
        org.junit.Assert.assertFalse(CategoriaUtil.contieneParola("anzug kaufen", "zug"));
        org.junit.Assert.assertFalse(CategoriaUtil.contieneParola("volo aereo", "vol"));
    }

    @Test
    public void testPrevenzioneFalsiPositivi() {
        // "Barcellona" e "Bari" contengono "bar", ma devono essere Trasporti (o non Bar)
        assertEquals(CategoriaUtil.CAT_TRASPORTI, CategoriaUtil.indovinaCategoriaDaTitolo("Volo per Barcellona"));
        assertEquals(CategoriaUtil.CAT_TRASPORTI, CategoriaUtil.indovinaCategoriaDaTitolo("Treno per Bari"));
        assertNull(CategoriaUtil.indovinaCategoriaDaTitolo("Piazza della Repubblica"));

        // "Visita guidata" non deve essere scambiata per spesa medica
        assertNull(CategoriaUtil.indovinaCategoriaDaTitolo("Visita guidata Colosseo"));
        // "Visita medica" deve essere Salute
        assertEquals(CategoriaUtil.CAT_SALUTE, CategoriaUtil.indovinaCategoriaDaTitolo("Visita medica specialistica"));
    }

    @Test
    public void testRiconoscimentoMultilinguaEN_ES_FR_DE() {
        // English
        assertEquals(CategoriaUtil.CAT_TRASPORTI, CategoriaUtil.indovinaCategoriaDaTitolo("Flight to London"));
        assertEquals(CategoriaUtil.CAT_CIBO, CategoriaUtil.indovinaCategoriaDaTitolo("Dinner with team"));
        assertEquals(CategoriaUtil.CAT_CIBO, CategoriaUtil.indovinaCategoriaDaTitolo("Lunch at restaurant"));
        assertEquals(CategoriaUtil.CAT_SPESA, CategoriaUtil.indovinaCategoriaDaTitolo("Groceries at supermarket"));
        assertEquals(CategoriaUtil.CAT_BAR, CategoriaUtil.indovinaCategoriaDaTitolo("Morning coffee"));
        assertEquals(CategoriaUtil.CAT_ALLOGGIO, CategoriaUtil.indovinaCategoriaDaTitolo("Night at hostel"));
        assertEquals(CategoriaUtil.CAT_SALUTE, CategoriaUtil.indovinaCategoriaDaTitolo("Pharmacy medicine"));
        assertEquals(CategoriaUtil.CAT_SVAGO, CategoriaUtil.indovinaCategoriaDaTitolo("Rock concert tickets"));
        assertEquals(CategoriaUtil.CAT_SHOPPING, CategoriaUtil.indovinaCategoriaDaTitolo("Clothes and shoes shopping"));

        // Spanish
        assertEquals(CategoriaUtil.CAT_CIBO, CategoriaUtil.indovinaCategoriaDaTitolo("Cena de tapas"));
        assertEquals(CategoriaUtil.CAT_SPESA, CategoriaUtil.indovinaCategoriaDaTitolo("Supermercado Mercadona"));
        assertEquals(CategoriaUtil.CAT_TRASPORTI, CategoriaUtil.indovinaCategoriaDaTitolo("Vuelo a Madrid"));
        assertEquals(CategoriaUtil.CAT_BAR, CategoriaUtil.indovinaCategoriaDaTitolo("Cerveza y copas"));
        assertEquals(CategoriaUtil.CAT_ALLOGGIO, CategoriaUtil.indovinaCategoriaDaTitolo("Hotel en Sevilla"));
        assertEquals(CategoriaUtil.CAT_SHOPPING, CategoriaUtil.indovinaCategoriaDaTitolo("Regalo de cumpleaños"));
        assertEquals(CategoriaUtil.CAT_SALUTE, CategoriaUtil.indovinaCategoriaDaTitolo("Farmacia y médico"));

        // French
        assertEquals(CategoriaUtil.CAT_CIBO, CategoriaUtil.indovinaCategoriaDaTitolo("Dîner au restaurant"));
        assertEquals(CategoriaUtil.CAT_SPESA, CategoriaUtil.indovinaCategoriaDaTitolo("Courses au supermarché"));
        assertEquals(CategoriaUtil.CAT_TRASPORTI, CategoriaUtil.indovinaCategoriaDaTitolo("Billet de train SNCF"));
        assertEquals(CategoriaUtil.CAT_BAR, CategoriaUtil.indovinaCategoriaDaTitolo("Bière et apéro"));
        assertEquals(CategoriaUtil.CAT_ALLOGGIO, CategoriaUtil.indovinaCategoriaDaTitolo("Hôtel à Paris"));
        assertEquals(CategoriaUtil.CAT_SHOPPING, CategoriaUtil.indovinaCategoriaDaTitolo("Cadeau anniversaire"));

        // German
        assertEquals(CategoriaUtil.CAT_CIBO, CategoriaUtil.indovinaCategoriaDaTitolo("Abendessen im Restaurant"));
        assertEquals(CategoriaUtil.CAT_SPESA, CategoriaUtil.indovinaCategoriaDaTitolo("Einkauf im Supermarkt"));
        assertEquals(CategoriaUtil.CAT_TRASPORTI, CategoriaUtil.indovinaCategoriaDaTitolo("Zug nach Berlin"));
        assertEquals(CategoriaUtil.CAT_BAR, CategoriaUtil.indovinaCategoriaDaTitolo("Kaffee und Bier"));
        assertEquals(CategoriaUtil.CAT_ALLOGGIO, CategoriaUtil.indovinaCategoriaDaTitolo("Hotel in München"));
        assertEquals(CategoriaUtil.CAT_SHOPPING, CategoriaUtil.indovinaCategoriaDaTitolo("Geschenk zum Geburtstag"));
        assertEquals(CategoriaUtil.CAT_SALUTE, CategoriaUtil.indovinaCategoriaDaTitolo("Apotheke Medikamente"));
    }

    @Test
    public void testEmojiCategoria() {
        assertEquals("🍕", CategoriaUtil.getEmojiForCategoria(CategoriaUtil.CAT_CIBO, "Pizza"));
        assertEquals("🚗", CategoriaUtil.getEmojiForCategoria(CategoriaUtil.CAT_TRASPORTI));
        assertEquals("💳", CategoriaUtil.getEmojiForCategoria(CategoriaUtil.CAT_SALDI));
        assertEquals("💳", CategoriaUtil.getEmojiForCategoria(CategoriaUtil.CAT_RIMBORSI));
        assertEquals("💳", CategoriaUtil.getEmojiForCategoria("Pareggio"));
    }

    @Test
    public void testIsCategoriaSaldi() {
        org.junit.Assert.assertTrue(CategoriaUtil.isCategoriaSaldi("Pareggio"));
        org.junit.Assert.assertTrue(CategoriaUtil.isCategoriaSaldi("Saldi"));
        org.junit.Assert.assertTrue(CategoriaUtil.isCategoriaSaldi("Saldo"));
        org.junit.Assert.assertTrue(CategoriaUtil.isCategoriaSaldi("Rimborsi"));
        org.junit.Assert.assertTrue(CategoriaUtil.isCategoriaSaldi("Rimborso"));
        org.junit.Assert.assertFalse(CategoriaUtil.isCategoriaSaldi("Cibo"));
        org.junit.Assert.assertFalse(CategoriaUtil.isCategoriaSaldi("Trasporti"));
        org.junit.Assert.assertFalse(CategoriaUtil.isCategoriaSaldi(null));
        org.junit.Assert.assertFalse(CategoriaUtil.isCategoriaSaldi(""));
    }
}
