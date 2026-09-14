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
    public static final String CAT_RIMBORSI = "Rimborsi";

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

        // 1. Saldi, Pareggi, Rimborsi e Settlement
        if (catClean.contains("saldi") || catClean.contains("saldo") || catClean.contains("pareggio") || catClean.contains("settlement") || catClean.contains("rimbors") ||
                titleClean.contains("saldi") || titleClean.contains("saldo") || titleClean.contains("pareggio") || titleClean.contains("pagamento") || titleClean.contains("rimbors")) {
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

    /**
     * Riconosce intelligentemente la categoria più probabile a partire dal testo/titolo inserito dall'utente
     * (es. "Pizza con amici" -> Cibo, "Spritz" -> Bar & Aperitivi, "Esselunga" -> Spesa, "Benzina" -> Trasporti).
     */
    @Nullable
    public static String indovinaCategoriaDaTitolo(@Nullable String titolo) {
        if (titolo == null) return null;
        String t = titolo.trim().toLowerCase(java.util.Locale.ROOT);
        if (t.isEmpty()) return null;

        if (t.contains("pizza") || t.contains("pizzeria") || t.contains("sushi") || t.contains("ristorante")
                || t.contains("pranzo") || t.contains("cena") || t.contains("mcdonald") || t.contains("burger")
                || t.contains("panino") || t.contains("kebab") || t.contains("poke")) {
            return CAT_CIBO;
        }
        if (t.contains("birra") || t.contains("beer") || t.contains("pub") || t.contains("cocktail")
                || t.contains("aperitivo") || t.contains("spritz") || t.contains("drink") || t.contains("caffè")
                || t.contains("colazione") || t.contains("bar")) {
            return CAT_BAR;
        }
        if (t.contains("spesa") || t.contains("supermercato") || t.contains("conad") || t.contains("coop")
                || t.contains("esselunga") || t.contains("lidl") || t.contains("carrefour") || t.contains("alimentari")) {
            return CAT_SPESA;
        }
        if (t.contains("benzina") || t.contains("gasolio") || t.contains("diesel") || t.contains("carburante")
                || t.contains("treno") || t.contains("trenitalia") || t.contains("italo") || t.contains("volo")
                || t.contains("aereo") || t.contains("ryanair") || t.contains("easyjet") || t.contains("taxi")
                || t.contains("uber") || t.contains("pedaggio") || t.contains("telepass") || t.contains("parcheggio")) {
            return CAT_TRASPORTI;
        }
        if (t.contains("hotel") || t.contains("airbnb") || t.contains("b&b") || t.contains("ostello")
                || t.contains("booking") || t.contains("alloggio") || t.contains("casa vacanze")) {
            return CAT_ALLOGGIO;
        }
        if (t.contains("cinema") || t.contains("film") || t.contains("concerto") || t.contains("teatro")
                || t.contains("museo") || t.contains("partita") || t.contains("calcetto") || t.contains("padel")
                || t.contains("palestra") || t.contains("bowling") || t.contains("escape room") || t.contains("stadio")) {
            return CAT_SVAGO;
        }
        if (t.contains("zara") || t.contains("h&m") || t.contains("amazon") || t.contains("shopping")
                || t.contains("vestiti") || t.contains("scarpe") || t.contains("regalo") || t.contains("compleanno")) {
            return CAT_SHOPPING;
        }
        if (t.contains("farmacia") || t.contains("medico") || t.contains("visita") || t.contains("medicine")
                || t.contains("dentista") || t.contains("tamponi")) {
            return CAT_SALUTE;
        }
        return null;
    }

    @Nullable
    public static String getNomeLocalizzatoCategoria(@NonNull android.content.Context context, @Nullable String categoriaStandard) {
        if (categoriaStandard == null) return null;
        if (categoriaStandard.equalsIgnoreCase(CAT_CIBO)) return context.getString(com.example.paripariapp.R.string.cat_cibo);
        if (categoriaStandard.equalsIgnoreCase(CAT_SPESA)) return context.getString(com.example.paripariapp.R.string.cat_spesa);
        if (categoriaStandard.equalsIgnoreCase(CAT_TRASPORTI)) return context.getString(com.example.paripariapp.R.string.cat_trasporti);
        if (categoriaStandard.equalsIgnoreCase(CAT_ALLOGGIO)) return context.getString(com.example.paripariapp.R.string.cat_alloggio);
        if (categoriaStandard.equalsIgnoreCase(CAT_SVAGO)) return context.getString(com.example.paripariapp.R.string.cat_svago);
        if (categoriaStandard.equalsIgnoreCase(CAT_SHOPPING)) return context.getString(com.example.paripariapp.R.string.cat_shopping);
        if (categoriaStandard.equalsIgnoreCase(CAT_BAR)) return context.getString(com.example.paripariapp.R.string.cat_bar);
        if (categoriaStandard.equalsIgnoreCase(CAT_SALUTE)) return context.getString(com.example.paripariapp.R.string.cat_salute);
        if (categoriaStandard.equalsIgnoreCase(CAT_ALTRO)) return context.getString(com.example.paripariapp.R.string.cat_altro);
        if (categoriaStandard.equalsIgnoreCase(CAT_RIMBORSI)) return context.getString(com.example.paripariapp.R.string.cat_rimborsi);
        return categoriaStandard;
    }

    public static boolean isCategoriaSaldi(@Nullable String categoria) {
        if (categoria == null) return false;
        String catLower = categoria.trim().toLowerCase(java.util.Locale.ROOT);
        return catLower.equalsIgnoreCase("saldi") || catLower.equalsIgnoreCase("saldo")
                || catLower.contains("pareggio") || catLower.contains("rimbors");
    }
}
