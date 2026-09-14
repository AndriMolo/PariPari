package com.example.paripariapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.paripariapp.util.CalcolatriceEspressioniUtil;

import org.junit.Test;

public class CalcolatriceEspressioniUtilTest {

    @Test
    public void testContieneOperatori() {
        assertTrue(CalcolatriceEspressioniUtil.contieneOperatori("10+5"));
        assertTrue(CalcolatriceEspressioniUtil.contieneOperatori("20 - 4"));
        assertTrue(CalcolatriceEspressioniUtil.contieneOperatori("5 * 2"));
        assertTrue(CalcolatriceEspressioniUtil.contieneOperatori("5 x 2"));
        assertTrue(CalcolatriceEspressioniUtil.contieneOperatori("10 / 2"));
        assertFalse(CalcolatriceEspressioniUtil.contieneOperatori("42.50"));
        assertFalse(CalcolatriceEspressioniUtil.contieneOperatori(""));
        assertFalse(CalcolatriceEspressioniUtil.contieneOperatori(null));
    }

    @Test
    public void testValutaAddizioneSottrazione() {
        Double res1 = CalcolatriceEspressioniUtil.valuta("12.50 + 7.50");
        assertNotNull(res1);
        assertEquals(20.00, res1, 0.001);

        Double res2 = CalcolatriceEspressioniUtil.valuta("50,00 - 15,20");
        assertNotNull(res2);
        assertEquals(34.80, res2, 0.001);

        Double res3 = CalcolatriceEspressioniUtil.valuta("10 + 5 + 3.25");
        assertNotNull(res3);
        assertEquals(18.25, res3, 0.001);
    }

    @Test
    public void testValutaMoltiplicazioneEDivisione() {
        Double res1 = CalcolatriceEspressioniUtil.valuta("25 * 3");
        assertNotNull(res1);
        assertEquals(75.00, res1, 0.001);

        Double res2 = CalcolatriceEspressioniUtil.valuta("25 x 4");
        assertNotNull(res2);
        assertEquals(100.00, res2, 0.001);

        Double res3 = CalcolatriceEspressioniUtil.valuta("100 / 4");
        assertNotNull(res3);
        assertEquals(25.00, res3, 0.001);

        Double res4 = CalcolatriceEspressioniUtil.valuta("10 + 5 * 2");
        assertNotNull(res4);
        assertEquals(20.00, res4, 0.001); // Precedenza moltiplicazione
    }

    @Test
    public void testValutaTrailingOperator() {
        // Mentre l'utente sta digitando "15 + ", deve restituire 15.00
        Double res = CalcolatriceEspressioniUtil.valuta("15 +");
        assertNotNull(res);
        assertEquals(15.00, res, 0.001);
    }

    @Test
    public void testValutaInputInvalido() {
        assertNull(CalcolatriceEspressioniUtil.valuta(""));
        assertNull(CalcolatriceEspressioniUtil.valuta(null));
        assertNull(CalcolatriceEspressioniUtil.valuta("abc"));
        assertNull(CalcolatriceEspressioniUtil.valuta("10 / 0")); // Divisione per zero
    }
}
