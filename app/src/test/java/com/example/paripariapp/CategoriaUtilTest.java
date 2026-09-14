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
