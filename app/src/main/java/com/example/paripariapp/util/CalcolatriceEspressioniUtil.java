package com.example.paripariapp.util;

import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility fintech per la valutazione inline di espressioni matematiche
 * direttamente nel campo importo spesa (es. "12.50 + 8", "50 - 15.20", "25 * 3").
 */
public final class CalcolatriceEspressioniUtil {

    private CalcolatriceEspressioniUtil() {}

    /**
     * Verifica se una stringa contiene operatori aritmetici (+, -, *, x, /).
     */
    public static boolean contieneOperatori(@Nullable String input) {
        if (input == null) return false;
        return input.contains("+") || input.contains("-") || input.contains("*")
                || input.contains("x") || input.contains("X") || input.contains("/");
    }

    /**
     * Valuta l'espressione matematica sanitizzata e restituisce l'importo calcolato arrotondato a 2 decimali,
     * oppure null se l'espressione non è valida o vuota.
     */
    @Nullable
    public static Double valuta(@Nullable String input) {
        if (input == null) return null;
        String sanitizzato = input.trim()
                .replace(',', '.')
                .replace(" ", "")
                .replace('x', '*')
                .replace('X', '*');

        if (sanitizzato.isEmpty()) return null;

        // Rimuovi eventuale operatore trailing (es. digitando "12 +")
        while (sanitizzato.endsWith("+") || sanitizzato.endsWith("-")
                || sanitizzato.endsWith("*") || sanitizzato.endsWith("/")) {
            sanitizzato = sanitizzato.substring(0, sanitizzato.length() - 1);
        }
        if (sanitizzato.isEmpty()) return null;

        try {
            List<BigDecimal> numeri = new ArrayList<>();
            List<Character> operatori = new ArrayList<>();

            StringBuilder currentNumber = new StringBuilder();
            for (int i = 0; i < sanitizzato.length(); i++) {
                char c = sanitizzato.charAt(i);
                if (c == '+' || c == '-' || c == '*' || c == '/') {
                    if (currentNumber.length() == 0) {
                        if (c == '-') {
                            currentNumber.append('-');
                            continue;
                        } else {
                            return null;
                        }
                    }
                    numeri.add(new BigDecimal(currentNumber.toString()));
                    currentNumber.setLength(0);
                    operatori.add(c);
                } else if (Character.isDigit(c) || c == '.') {
                    currentNumber.append(c);
                } else {
                    return null;
                }
            }

            if (currentNumber.length() > 0) {
                numeri.add(new BigDecimal(currentNumber.toString()));
            }

            if (numeri.isEmpty() || numeri.size() != operatori.size() + 1) {
                return null;
            }

            // Passo 1: Precedenza per moltiplicazione e divisione
            int i = 0;
            while (i < operatori.size()) {
                char op = operatori.get(i);
                if (op == '*' || op == '/') {
                    BigDecimal a = numeri.get(i);
                    BigDecimal b = numeri.get(i + 1);
                    BigDecimal res;
                    if (op == '*') {
                        res = a.multiply(b);
                    } else {
                        if (b.compareTo(BigDecimal.ZERO) == 0) return null;
                        res = a.divide(b, 4, RoundingMode.HALF_UP);
                    }
                    numeri.set(i, res);
                    numeri.remove(i + 1);
                    operatori.remove(i);
                } else {
                    i++;
                }
            }

            // Passo 2: Addizione e sottrazione
            BigDecimal risultato = numeri.get(0);
            for (int j = 0; j < operatori.size(); j++) {
                char op = operatori.get(j);
                BigDecimal b = numeri.get(j + 1);
                if (op == '+') {
                    risultato = risultato.add(b);
                } else if (op == '-') {
                    risultato = risultato.subtract(b);
                }
            }

            double val = risultato.setScale(2, RoundingMode.HALF_UP).doubleValue();
            return val >= 0 ? val : 0.0;
        } catch (Exception e) {
            return null;
        }
    }
}
