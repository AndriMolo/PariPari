package com.example.paripariapp.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Repository per la gestione e la persistenza locale delle preferenze utente.
 * Gestisce la valuta predefinita (creazione schede) e la lingua/locale dell'app.
 */
public class UserPreferencesRepository {

    private static final String PREFS_NAME = "paripari_user_preferences";
    private static final String KEY_DEFAULT_CURRENCY = "pref_default_currency";
    public static final String DEFAULT_CURRENCY = "EUR";

    private static final String KEY_APP_LANGUAGE = "pref_app_language";
    public static final String LANGUAGE_SYSTEM = "SYSTEM";

    private static final String KEY_APP_THEME = "pref_app_theme";
    public static final String THEME_SYSTEM = "SYSTEM";
    public static final String THEME_LIGHT = "LIGHT";
    public static final String THEME_DARK = "DARK";

    public static final List<String> SUPPORTED_CURRENCIES = Arrays.asList(
            "EUR - Euro",
            "USD - Dollaro USA",
            "GBP - Sterlina britannica",
            "CHF - Franco svizzero",
            "JPY - Yen giapponese",
            "CAD - Dollaro canadese",
            "AUD - Dollaro australiano",
            "BRL - Real brasiliano",
            "CNY - Yuan cinese",
            "CZK - Corona ceca",
            "DKK - Corona danese",
            "HKD - Dollaro di Hong Kong",
            "HUF - Fiorino ungherese",
            "IDR - Rupia indonesiana",
            "ILS - Nuovo shekel israeliano",
            "INR - Rupia indiana",
            "ISK - Corona islandese",
            "KRW - Won sudcoreano",
            "MXN - Peso messicano",
            "MYR - Ringgit malese",
            "NOK - Corona norvegese",
            "NZD - Dollaro neozelandese",
            "PHP - Peso filippino",
            "PLN - Złoty polacco",
            "RON - Leu rumeno",
            "SEK - Corona svedese",
            "SGD - Dollaro di Singapore",
            "THB - Baht thailandese",
            "TRY - Lira turca",
            "ZAR - Rand sudafricano"
    );

    public static final List<String> SUPPORTED_LANGUAGES = Arrays.asList(
            "SYSTEM - Predefinita di sistema",
            "it - Italiano",
            "en - English",
            "es - Español",
            "fr - Français",
            "de - Deutsch"
    );

    private static volatile UserPreferencesRepository instance;

    private final SharedPreferences preferences;
    private final MutableLiveData<String> defaultCurrencyLive = new MutableLiveData<>();
    private final MutableLiveData<String> appLanguageLive = new MutableLiveData<>();
    private final MutableLiveData<String> appThemeLive = new MutableLiveData<>();

    private UserPreferencesRepository(Context context) {
        this.preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String currentCurrency = preferences.getString(KEY_DEFAULT_CURRENCY, DEFAULT_CURRENCY);
        defaultCurrencyLive.setValue(currentCurrency);

        String currentLanguage = preferences.getString(KEY_APP_LANGUAGE, LANGUAGE_SYSTEM);
        appLanguageLive.setValue(currentLanguage);

        String currentTheme = preferences.getString(KEY_APP_THEME, THEME_SYSTEM);
        appThemeLive.setValue(currentTheme);
    }

    public static UserPreferencesRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (UserPreferencesRepository.class) {
                if (instance == null) {
                    instance = new UserPreferencesRepository(context);
                }
            }
        }
        return instance;
    }

    /**
     * Restituisce il codice valuta attualmente impostato (es. "EUR", "USD").
     */
    public String getDefaultCurrency() {
        return preferences.getString(KEY_DEFAULT_CURRENCY, DEFAULT_CURRENCY);
    }

    /**
     * LiveData osservabile per aggiornare reattivamente la UI quando la valuta cambia.
     */
    public LiveData<String> getDefaultCurrencyLive() {
        return defaultCurrencyLive;
    }

    /**
     * Salva la nuova valuta predefinita ed emette l'aggiornamento.
     *
     * @param currencyCode Codice valuta (es. "EUR", "USD")
     */
    public void setDefaultCurrency(String currencyCode) {
        if (currencyCode == null || currencyCode.trim().isEmpty()) {
            return;
        }
        String cleanCode = currencyCode.trim().toUpperCase();
        preferences.edit().putString(KEY_DEFAULT_CURRENCY, cleanCode).apply();
        defaultCurrencyLive.postValue(cleanCode);
    }

    /**
     * Helper per estrarre il codice a 3 lettere da una stringa formattata (es. "USD - Dollaro USA" -> "USD").
     */
    public static String extractCurrencyCode(String displayCurrency) {
        if (displayCurrency == null || displayCurrency.isEmpty()) {
            return DEFAULT_CURRENCY;
        }
        if (displayCurrency.contains(" - ")) {
            return displayCurrency.split(" - ")[0].trim();
        }
        return displayCurrency.trim().toUpperCase();
    }

    /**
     * Helper per trovare la voce visuale corrispondente al codice (es. "USD" -> "USD - Dollaro USA").
     */
    public static String getDisplayItemForCode(String currencyCode) {
        if (currencyCode == null || currencyCode.isEmpty()) {
            return SUPPORTED_CURRENCIES.get(0);
        }
        for (String item : SUPPORTED_CURRENCIES) {
            if (item.startsWith(currencyCode.toUpperCase())) {
                return item;
            }
        }
        return currencyCode;
    }

    /**
     * Helper per trovare l'indice posizionale della valuta supportata (0 se non trovata).
     */
    public static int getIndexOfCurrencyCode(String currencyCode) {
        if (currencyCode == null) return 0;
        for (int i = 0; i < SUPPORTED_CURRENCIES.size(); i++) {
            if (SUPPORTED_CURRENCIES.get(i).startsWith(currencyCode.toUpperCase())) {
                return i;
            }
        }
        return 0;
    }

    /**
     * Estrae il nome esteso della valuta (es. "USD - Dollaro USA" -> "Dollaro USA").
     */
    public static String extractCurrencyName(String displayCurrency) {
        if (displayCurrency == null || displayCurrency.isEmpty()) {
            return "";
        }
        if (displayCurrency.contains(" - ")) {
            return displayCurrency.split(" - ")[1].trim();
        }
        return displayCurrency.trim();
    }

    /**
     * Restituisce l'emoji della bandiera associata al codice valuta.
     */
    public static String getCurrencyFlag(String currencyCode) {
        if (currencyCode == null) return "🏳️";
        switch (currencyCode.toUpperCase().trim()) {
            case "EUR": return "🇪🇺";
            case "USD": return "🇺🇸";
            case "GBP": return "🇬🇧";
            case "CHF": return "🇨🇭";
            case "JPY": return "🇯🇵";
            case "CAD": return "🇨🇦";
            case "AUD": return "🇦🇺";
            case "BRL": return "🇧🇷";
            case "CNY": return "🇨🇳";
            case "CZK": return "🇨🇿";
            case "DKK": return "🇩🇰";
            case "HKD": return "🇭🇰";
            case "HUF": return "🇭🇺";
            case "IDR": return "🇮🇩";
            case "ILS": return "🇮🇱";
            case "INR": return "🇮🇳";
            case "ISK": return "🇮🇸";
            case "KRW": return "🇰🇷";
            case "MXN": return "🇲🇽";
            case "MYR": return "🇲🇾";
            case "NOK": return "🇳🇴";
            case "NZD": return "🇳🇿";
            case "PHP": return "🇵🇭";
            case "PLN": return "🇵🇱";
            case "RON": return "🇷🇴";
            case "SEK": return "🇸🇪";
            case "SGD": return "🇸🇬";
            case "THB": return "🇹🇭";
            case "TRY": return "🇹🇷";
            case "ZAR": return "🇿🇦";
            default: return "💰";
        }
    }

    // ====================================================================
    // GESTIONE LINGUA & LOCALE APP
    // ====================================================================

    /**
     * Restituisce il codice lingua attualmente salvato ("SYSTEM", "it", "en", ecc.).
     */
    public String getAppLanguage() {
        return preferences.getString(KEY_APP_LANGUAGE, LANGUAGE_SYSTEM);
    }

    /**
     * LiveData osservabile per la lingua dell'applicazione.
     */
    public LiveData<String> getAppLanguageLive() {
        return appLanguageLive;
    }

    /**
     * Salva la nuova lingua e la applica immediatamente tramite AppCompatDelegate.
     *
     * @param langCode Codice lingua (es. "SYSTEM", "it", "en")
     */
    public void setAppLanguage(String langCode) {
        if (langCode == null || langCode.trim().isEmpty()) {
            return;
        }
        String cleanCode = langCode.trim();
        preferences.edit().putString(KEY_APP_LANGUAGE, cleanCode).apply();
        appLanguageLive.postValue(cleanCode);
        applyLanguage(cleanCode);
    }

    /**
     * Applica la lingua correntemente salvata nelle preferenze.
     */
    public void applyCurrentLanguage() {
        applyLanguage(getAppLanguage());
    }

    /**
     * Applica a livello di framework/AppCompat la lingua selezionata.
     */
    public static void applyLanguage(String langCode) {
        if (langCode == null || LANGUAGE_SYSTEM.equalsIgnoreCase(langCode)) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(langCode));
        }
    }

    /**
     * Estrae il codice lingua da una stringa formattata (es. "it - Italiano" -> "it").
     */
    public static String extractLanguageCode(String displayLanguage) {
        if (displayLanguage == null || displayLanguage.isEmpty()) {
            return LANGUAGE_SYSTEM;
        }
        if (displayLanguage.contains(" - ")) {
            return displayLanguage.split(" - ")[0].trim();
        }
        return displayLanguage.trim();
    }

    /**
     * Restituisce la dicitura leggibile per la lingua specificata.
     */
    public static String getDisplayLanguageForCode(String langCode) {
        if (langCode == null || LANGUAGE_SYSTEM.equalsIgnoreCase(langCode)) {
            return "Predefinita di sistema";
        }
        for (String item : SUPPORTED_LANGUAGES) {
            if (item.startsWith(langCode + " - ")) {
                return item.split(" - ")[1].trim();
            }
        }
        return langCode;
    }

    /**
     * Helper per trovare l'indice posizionale della lingua supportata (0 se non trovata).
     */
    public static int getIndexOfLanguageCode(String langCode) {
        if (langCode == null) return 0;
        for (int i = 0; i < SUPPORTED_LANGUAGES.size(); i++) {
            if (SUPPORTED_LANGUAGES.get(i).startsWith(langCode)) {
                return i;
            }
        }
        return 0;
    }

    /**
     * Restituisce il Locale attivo dell'applicazione per la formattazione di numeri e decimali.
     */
    public Locale getAppLocale() {
        String lang = getAppLanguage();
        if (LANGUAGE_SYSTEM.equalsIgnoreCase(lang) || lang == null || lang.isEmpty()) {
            return Locale.getDefault();
        }
        return Locale.forLanguageTag(lang);
    }

    // ====================================================================
    // GESTIONE TEMA APP
    // ====================================================================

    public String getAppTheme() {
        return preferences.getString(KEY_APP_THEME, THEME_SYSTEM);
    }

    public LiveData<String> getAppThemeLive() {
        return appThemeLive;
    }

    public void setAppTheme(String themeCode) {
        if (themeCode == null || themeCode.trim().isEmpty()) {
            return;
        }
        String cleanCode = themeCode.trim().toUpperCase();
        preferences.edit().putString(KEY_APP_THEME, cleanCode).apply();
        appThemeLive.postValue(cleanCode);
        applyTheme(cleanCode);
    }

    public void applyCurrentTheme() {
        applyTheme(getAppTheme());
    }

    public static void applyTheme(String themeCode) {
        if (THEME_LIGHT.equalsIgnoreCase(themeCode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (THEME_DARK.equalsIgnoreCase(themeCode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }
}
