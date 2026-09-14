package com.example.paripariapp.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Utility per gestire le icone e gli emoji dinamici associati alle categorie ed ai titoli delle spese.
 * Supporta categorie in italiano e inglese (bilingue) oltre all'analisi intelligente del titolo.
 */
public class CategoriaUtil {

    public static final String CAT_CIBO = "Cibo";
    public static final String CAT_SPESA = "Spesa";
    public static final String CAT_TRASPORTI = "Trasporti";
    public static final String CAT_ALLOGGIO = "Alloggio";
    public static final String CAT_SVAGO = "Svago";
    public static final String CAT_SHOPPING = "Shopping";
    public static final String CAT_BAR = "Bar & Aperitivi";
    public static final String CAT_SALUTE = "Salute";
    public static final String CAT_ALTRO = "Altro";
    public static final String CAT_SALDI = "Saldi";

    @NonNull
    public static String getEmojiForCategoria(@Nullable String categoria) {
        return getEmojiForCategoria(categoria, null);
    }

    @NonNull
    public static String getEmojiForCategoria(@Nullable String categoria, @Nullable String titolo) {
        String catClean = categoria != null ? categoria.trim().toLowerCase(java.util.Locale.ROOT) : "";
        String titleClean = titolo != null ? titolo.trim().toLowerCase(java.util.Locale.ROOT) : "";
        String textToAnalyze = (catClean + " " + titleClean).trim();

        if (textToAnalyze.isEmpty()) {
            return "🧾";
        }

        // 1. Saldi, Pareggi e Settlement
        if (catClean.contains("saldi") || catClean.contains("saldo") || catClean.contains("pareggio") || catClean.contains("settlement") ||
                titleClean.contains("saldi") || titleClean.contains("saldo") || titleClean.contains("pareggio") || titleClean.contains("pagamento")) {
            return "💳";
        }

        // 2. Cibo & Ristoranti (Cibo, Food, Restaurant, Dining)
        if (catClean.contains("cibo") || catClean.contains("food") || catClean.contains("ristorante") || catClean.contains("restaurant") || catClean.contains("dining")) {
            if (titleClean.contains("pizza") || titleClean.contains("pizzeria")) return "🍕";
            if (titleClean.contains("sushi") || titleClean.contains("giapponese") || titleClean.contains("ramen")) return "🍣";
            if (titleClean.contains("hamburger") || titleClean.contains("fast food") || titleClean.contains("mcdonald") || titleClean.contains("burger")) return "🍔";
            return "🍽️";
        }

        // 3. Bar & Aperitivi (Bar, Drinks)
        if (catClean.contains("bar") || catClean.contains("aperitivi") || catClean.contains("aperitivo") || catClean.contains("drink")) {
            if (titleClean.contains("birra") || titleClean.contains("pub") || titleClean.contains("beer")) return "🍺";
            if (titleClean.contains("caffè") || titleClean.contains("coffee") || titleClean.contains("colazione") || titleClean.contains("espresso")) return "☕";
            return "🍹";
        }

        // 4. Spesa & Supermercato (Spesa, Groceries, Supermarket, Market)
        if (catClean.contains("spesa") || catClean.contains("groceries") || catClean.contains("supermercato") || catClean.contains("market")) {
            return "🛒";
        }

        // 5. Trasporti (Trasporti, Transport, Travel, Car)
        if (catClean.contains("trasporti") || catClean.contains("transport") || catClean.contains("travel") || catClean.contains("auto") || catClean.contains("car")) {
            if (titleClean.contains("volo") || titleClean.contains("aereo") || titleClean.contains("ryanair") || titleClean.contains("easyjet") || titleClean.contains("flight")) return "✈️";
            if (titleClean.contains("treno") || titleClean.contains("metro") || titleClean.contains("biglietto") || titleClean.contains("trenitalia") || titleClean.contains("italo") || titleClean.contains("train")) return "🚊";
            if (titleClean.contains("taxi") || titleClean.contains("uber")) return "🚕";
            if (titleClean.contains("benzina") || titleClean.contains("carburante") || titleClean.contains("diesel") || titleClean.contains("gasolio") || titleClean.contains("gas") || titleClean.contains("eni") || titleClean.contains("q8")) return "⛽";
            return "🚗";
        }

        // 6. Alloggio (Alloggio, Accommodation, Hotel, Home, Stay)
        if (catClean.contains("alloggio") || catClean.contains("accommodation") || catClean.contains("hotel") || catClean.contains("casa") || catClean.contains("home") || catClean.contains("stay")) {
            if (titleClean.contains("hotel") || titleClean.contains("airbnb") || titleClean.contains("b&b") || titleClean.contains("ostello") || titleClean.contains("resort") || titleClean.contains("booking")) return "🏨";
            return "🏠";
        }

        // 7. Svago (Svago, Leisure, Entertainment, Fun)
        if (catClean.contains("svago") || catClean.contains("leisure") || catClean.contains("intrattenimento") || catClean.contains("entertainment") || catClean.contains("fun")) {
            if (titleClean.contains("gaming") || titleClean.contains("gioco") || titleClean.contains("playstation") || titleClean.contains("xbox") || titleClean.contains("steam") || titleClean.contains("nintendo") || titleClean.contains("game")) return "🎮";
            if (titleClean.contains("cinema") || titleClean.contains("film") || titleClean.contains("popcorn") || titleClean.contains("netflix") || titleClean.contains("prime")) return "🍿";
            if (titleClean.contains("concerto") || titleClean.contains("musica") || titleClean.contains("evento") || titleClean.contains("ticket") || titleClean.contains("spotify")) return "🎟️";
            if (titleClean.contains("sport") || titleClean.contains("calcetto") || titleClean.contains("palestra") || titleClean.contains("padel") || titleClean.contains("tennis")) return "⚽";
            return "🎟️";
        }

        // 8. Shopping (Shopping, Clothes)
        if (catClean.contains("shopping") || catClean.contains("abbigliamento") || catClean.contains("clothes")) {
            if (titleClean.contains("regalo") || titleClean.contains("compleanno") || titleClean.contains("gift")) return "🎁";
            return "🛍️";
        }

        // 9. Salute (Salute, Health, Pharmacy)
        if (catClean.contains("salute") || catClean.contains("health") || catClean.contains("farmacia") || catClean.contains("pharmacy")) {
            return "💊";
        }

        // 10. Altro / Other / General
        if (catClean.contains("altro") || catClean.contains("other") || catClean.contains("generale") || catClean.contains("general")) {
            return "🧾";
        }

        // Analisi globale di fallback basata su parole chiave nel titolo e categoria
        if (textToAnalyze.contains("pizza")) return "🍕";
        if (textToAnalyze.contains("sushi")) return "🍣";
        if (textToAnalyze.contains("birra") || textToAnalyze.contains("pub") || textToAnalyze.contains("beer")) return "🍺";
        if (textToAnalyze.contains("aperitivo") || textToAnalyze.contains("cocktail") || textToAnalyze.contains("drink")) return "🍹";
        if (textToAnalyze.contains("caffè") || textToAnalyze.contains("colazione") || textToAnalyze.contains("coffee")) return "☕";
        if (textToAnalyze.contains("spesa") || textToAnalyze.contains("groceries") || textToAnalyze.contains("supermercato")) return "🛒";
        if (textToAnalyze.contains("volo") || textToAnalyze.contains("aereo") || textToAnalyze.contains("flight")) return "✈️";
        if (textToAnalyze.contains("treno") || textToAnalyze.contains("train")) return "🚊";
        if (textToAnalyze.contains("benzina") || textToAnalyze.contains("carburante") || textToAnalyze.contains("gas")) return "⛽";
        if (textToAnalyze.contains("hotel") || textToAnalyze.contains("airbnb")) return "🏨";
        if (textToAnalyze.contains("cinema") || textToAnalyze.contains("film")) return "🍿";
        if (textToAnalyze.contains("shopping") || textToAnalyze.contains("amazon") || textToAnalyze.contains("zara")) return "🛍️";
        if (textToAnalyze.contains("farmacia") || textToAnalyze.contains("medico") || textToAnalyze.contains("pharmacy")) return "💊";

        return "🧾";
    }

    public static boolean isCategoriaSaldi(@Nullable String categoria) {
        if (categoria == null) return false;
        String catLower = categoria.trim().toLowerCase(java.util.Locale.ROOT);
        return catLower.equalsIgnoreCase("saldi") || catLower.equalsIgnoreCase("saldo") || catLower.contains("pareggio");
    }
}
