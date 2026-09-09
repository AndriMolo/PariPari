package com.example.paripariapp;

import com.example.paripariapp.data.repository.UserPreferencesRepository;

import org.junit.Test;

import java.util.Locale;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Test unitari completi per la logica di validazione e calcolo conversione valuta.
 */
public class ValutaConversionTest {

    private static final Pattern AMOUNT_REGEX = Pattern.compile("^[0-9]+([.,][0-9]{1,2})?$");

    @Test
    public void testAmountRegex_ValidInputs() {
        assertTrue("Intero valido", AMOUNT_REGEX.matcher("10").matches());
        assertTrue("Decimale con punto", AMOUNT_REGEX.matcher("10.5").matches());
        assertTrue("Decimale con due cifre punto", AMOUNT_REGEX.matcher("10.50").matches());
        assertTrue("Decimale con virgola", AMOUNT_REGEX.matcher("150,75").matches());
        assertTrue("Numero grande", AMOUNT_REGEX.matcher("1000000").matches());
        assertTrue("Zero iniziale valido", AMOUNT_REGEX.matcher("0.99").matches());
    }

    @Test
    public void testAmountRegex_InvalidInputs() {
        assertFalse("Testo non numerico", AMOUNT_REGEX.matcher("abc").matches());
        assertFalse("Più di due decimali", AMOUNT_REGEX.matcher("10.555").matches());
        assertFalse("Numero negativo", AMOUNT_REGEX.matcher("-10").matches());
        assertFalse("Doppio separatore", AMOUNT_REGEX.matcher("10..5").matches());
        assertFalse("Separatore multiplo", AMOUNT_REGEX.matcher("10,5,2").matches());
        assertFalse("Stringa vuota", AMOUNT_REGEX.matcher("").matches());
        assertFalse("Solo punto", AMOUNT_REGEX.matcher(".").matches());
        assertFalse("Solo virgola", AMOUNT_REGEX.matcher(",").matches());
    }

    @Test
    public void testExtractCurrencyCode() {
        assertEquals("EUR", UserPreferencesRepository.extractCurrencyCode("EUR - Euro"));
        assertEquals("USD", UserPreferencesRepository.extractCurrencyCode("USD - Dollaro USA"));
        assertEquals("CHF", UserPreferencesRepository.extractCurrencyCode("CHF - Franco svizzero"));
        assertEquals("JPY", UserPreferencesRepository.extractCurrencyCode("JPY - Yen giapponese"));
        assertEquals("ZAR", UserPreferencesRepository.extractCurrencyCode("ZAR - Rand sudafricano"));
        assertEquals("USD", UserPreferencesRepository.extractCurrencyCode("USD"));
        assertEquals("EUR", UserPreferencesRepository.extractCurrencyCode(null));
        assertEquals("EUR", UserPreferencesRepository.extractCurrencyCode(""));
    }

    @Test
    public void testConversionMath_DirectAndCross() {
        // EUR = 1.0 (base)
        // USD = 1.165
        // CHF = 0.940
        double rateEur = 1.0;
        double rateUsd = 1.165;
        double rateChf = 0.940;

        // 100 EUR in USD = 100 * 1.165 = 116.50 USD
        double eurToUsd = (100.0 / rateEur) * rateUsd;
        assertEquals(116.50, eurToUsd, 0.001);

        // 100 USD in EUR = 100 / 1.165 = 85.8369... EUR
        double usdToEur = (100.0 / rateUsd) * rateEur;
        assertEquals(85.8369, usdToEur, 0.001);

        // 150 CHF in USD: (150 / 0.940) * 1.165 = 185.9042... USD
        double chfToUsd = (150.0 / rateChf) * rateUsd;
        assertEquals(185.9042, chfToUsd, 0.001);

        // Inversione (A -> B -> A)
        double usdBackToChf = (chfToUsd / rateUsd) * rateChf;
        assertEquals(150.0, usdBackToChf, 0.0001);
    }

    @Test
    public void testAll30SupportedCurrenciesHaveFlagsAndNames() {
        assertEquals(30, UserPreferencesRepository.SUPPORTED_CURRENCIES.size());

        for (String currencyFull : UserPreferencesRepository.SUPPORTED_CURRENCIES) {
            String code = UserPreferencesRepository.extractCurrencyCode(currencyFull);
            assertNotNull("Codice non nullo per " + currencyFull, code);
            assertEquals(3, code.length());

            String flag = UserPreferencesRepository.getCurrencyFlag(code);
            assertNotNull("Bandiera presente per " + code, flag);
            assertFalse("Bandiera non vuota per " + code, flag.isEmpty());

            String name = UserPreferencesRepository.extractCurrencyName(currencyFull);
            assertNotNull("Nome presente per " + currencyFull, name);
            assertFalse("Nome non vuoto per " + currencyFull, name.isEmpty());
        }
    }
}
