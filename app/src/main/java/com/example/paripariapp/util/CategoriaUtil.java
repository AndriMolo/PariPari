package com.example.paripariapp.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Utility per gestire le icone e gli emoji dinamici associati alle categorie ed ai titoli delle spese.
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
        String textToAnalyze = ((categoria != null ? categoria : "") + " " + (titolo != null ? titolo : "")).trim().toLowerCase();

        if (textToAnalyze.isEmpty()) {
            return "🧾";
        }

        // 1. Saldi e Pareggi
        if (textToAnalyze.contains("saldi") || textToAnalyze.contains("saldo") || textToAnalyze.contains("pareggio") || textToAnalyze.contains("pagamento")) {
            return "💳";
        }

        // 2. Cibo, Pizzeria, Ristorante, Cena, Pranzo
        if (textToAnalyze.contains("pizza") || textToAnalyze.contains("pizzeria")) {
            return "🍕";
        }
        if (textToAnalyze.contains("sushi") || textToAnalyze.contains("giapponese") || textToAnalyze.contains("ramen")) {
            return "🍣";
        }
        if (textToAnalyze.contains("hamburger") || textToAnalyze.contains("fast food") || textToAnalyze.contains("mcdonald") || textToAnalyze.contains("burger")) {
            return "🍔";
        }
        if (textToAnalyze.contains("cibo") || textToAnalyze.contains("ristorante") || textToAnalyze.contains("cena") || textToAnalyze.contains("pranzo") || textToAnalyze.contains("trattoria") || textToAnalyze.contains("osteria") || textToAnalyze.contains("food")) {
            return "🍽️";
        }

        // 3. Bar, Birra, Aperitivo, Caffè, Drink
        if (textToAnalyze.contains("birra") || textToAnalyze.contains("pub") || textToAnalyze.contains("beer")) {
            return "🍺";
        }
        if (textToAnalyze.contains("aperitivo") || textToAnalyze.contains("cocktail") || textToAnalyze.contains("drink") || textToAnalyze.contains("bar") || textToAnalyze.contains("spritz")) {
            return "🍹";
        }
        if (textToAnalyze.contains("caffè") || textToAnalyze.contains("colazione") || textToAnalyze.contains("espresso") || textToAnalyze.contains("coffee")) {
            return "☕";
        }

        // 4. Spesa, Supermercato, Minimarket
        if (textToAnalyze.contains("spesa") || textToAnalyze.contains("supermercato") || textToAnalyze.contains("conad") || textToAnalyze.contains("esselunga") || textToAnalyze.contains("coop") || textToAnalyze.contains("lidl") || textToAnalyze.contains("eurospin") || textToAnalyze.contains("carrefour") || textToAnalyze.contains("market")) {
            return "🛒";
        }

        // 5. Trasporti, Taxi, Volo, Treno, Auto, Benzina, Carburante
        if (textToAnalyze.contains("volo") || textToAnalyze.contains("aereo") || textToAnalyze.contains("ryanair") || textToAnalyze.contains("easyjet") || textToAnalyze.contains("flight")) {
            return "✈️";
        }
        if (textToAnalyze.contains("treno") || textToAnalyze.contains("metro") || textToAnalyze.contains("biglietto") || textToAnalyze.contains("trenitalia") || textToAnalyze.contains("italo") || textToAnalyze.contains("train")) {
            return "🚊";
        }
        if (textToAnalyze.contains("taxi") || textToAnalyze.contains("uber")) {
            return "🚕";
        }
        if (textToAnalyze.contains("benzina") || textToAnalyze.contains("carburante") || textToAnalyze.contains("diesel") || textToAnalyze.contains("gasolio") || textToAnalyze.contains("distributore") || textToAnalyze.contains("eni") || textToAnalyze.contains("q8")) {
            return "⛽";
        }
        if (textToAnalyze.contains("trasporti") || textToAnalyze.contains("auto") || textToAnalyze.contains("parcheggio") || textToAnalyze.contains("pedaggio") || textToAnalyze.contains("autostrada")) {
            return "🚗";
        }

        // 6. Alloggio, Hotel, Airbnb, Casa, Affitto
        if (textToAnalyze.contains("alloggio") || textToAnalyze.contains("hotel") || textToAnalyze.contains("airbnb") || textToAnalyze.contains("b&b") || textToAnalyze.contains("ostello") || textToAnalyze.contains("resort") || textToAnalyze.contains("booking")) {
            return "🏨";
        }
        if (textToAnalyze.contains("casa") || textToAnalyze.contains("affitto") || textToAnalyze.contains("bolletta") || textToAnalyze.contains("luce") || textToAnalyze.contains("gas") || textToAnalyze.contains("wifi")) {
            return "🏠";
        }

        // 7. Svago, Cinema, Musica, Gaming, Giochi, Sport
        if (textToAnalyze.contains("gaming") || textToAnalyze.contains("gioco") || textToAnalyze.contains("playstation") || textToAnalyze.contains("xbox") || textToAnalyze.contains("steam") || textToAnalyze.contains("nintendo") || textToAnalyze.contains("svago") || textToAnalyze.contains("game")) {
            return "🎮";
        }
        if (textToAnalyze.contains("cinema") || textToAnalyze.contains("film") || textToAnalyze.contains("popcorn") || textToAnalyze.contains("netflix") || textToAnalyze.contains("prime")) {
            return "🍿";
        }
        if (textToAnalyze.contains("concerto") || textToAnalyze.contains("musica") || textToAnalyze.contains("evento") || textToAnalyze.contains("ticket") || textToAnalyze.contains("spotify")) {
            return "🎟️";
        }
        if (textToAnalyze.contains("sport") || textToAnalyze.contains("calcetto") || textToAnalyze.contains("palestra") || textToAnalyze.contains("padel") || textToAnalyze.contains("tennis")) {
            return "⚽";
        }

        // 8. Shopping, Abbigliamento, Regali
        if (textToAnalyze.contains("regalo") || textToAnalyze.contains("compleanno") || textToAnalyze.contains("gift")) {
            return "🎁";
        }
        if (textToAnalyze.contains("shopping") || textToAnalyze.contains("abbigliamento") || textToAnalyze.contains("vestiti") || textToAnalyze.contains("negozio") || textToAnalyze.contains("amazon") || textToAnalyze.contains("zara")) {
            return "🛍️";
        }

        // 9. Salute, Farmacia, Medico
        if (textToAnalyze.contains("farmacia") || textToAnalyze.contains("medico") || textToAnalyze.contains("medicina") || textToAnalyze.contains("dottore") || textToAnalyze.contains("salute")) {
            return "💊";
        }

        return "🧾";
    }

    public static boolean isCategoriaSaldi(@Nullable String categoria) {
        if (categoria == null) return false;
        String catLower = categoria.trim().toLowerCase();
        return catLower.equalsIgnoreCase("saldi") || catLower.equalsIgnoreCase("saldo") || catLower.contains("pareggio");
    }
}
