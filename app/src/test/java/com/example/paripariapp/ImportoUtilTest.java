package com.example.paripariapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.paripariapp.util.ImportoUtil;

import org.junit.Test;

/**
 * Test unitari per {@link ImportoUtil}.
 */
public class ImportoUtilTest {

    @Test
    public void testFormattaConValuta() {
        String result = ImportoUtil.formatta(15.5, "USD");
        assertTrue(result.contains("15") && result.contains("50") && result.contains("USD"));
    }

    @Test
    public void testFormattaConValutaNullUsaFallback() {
        String result = ImportoUtil.formatta(20.0, null);
        assertTrue(result.contains("20") && result.contains("00") && result.contains("EUR"));
    }

    @Test
    public void testFormattaConValutaVuotaUsaFallback() {
        String result = ImportoUtil.formatta(0.0, "   ");
        assertTrue(result.contains("0") && result.contains("00") && result.contains("EUR"));
    }

    @Test
    public void testFormattaConSegnoPositivo() {
        String result = ImportoUtil.formattaConSegno(12.34, "EUR");
        assertTrue(result.startsWith("+"));
        assertTrue(result.contains("12") && result.contains("34") && result.contains("EUR"));
    }

    @Test
    public void testFormattaConSegnoNegativo() {
        String result = ImportoUtil.formattaConSegno(-8.99, "EUR");
        assertTrue(result.startsWith("-"));
        assertTrue(result.contains("8") && result.contains("99") && result.contains("EUR"));
    }

    @Test
    public void testFormattaConSegnoZero() {
        String result = ImportoUtil.formattaConSegno(0.0, "EUR");
        assertTrue(!result.startsWith("+") && !result.startsWith("-"));
        assertTrue(result.contains("0") && result.contains("00") && result.contains("EUR"));
    }

    @Test
    public void testFormattaPerInput() {
        assertEquals("10.50", ImportoUtil.formattaPerInput(10.5));
        assertEquals("0.00", ImportoUtil.formattaPerInput(0.0));
        assertEquals("1234.56", ImportoUtil.formattaPerInput(1234.56));
    }
}
