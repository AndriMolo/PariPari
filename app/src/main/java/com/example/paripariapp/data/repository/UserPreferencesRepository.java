package com.example.paripariapp.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.Arrays;
import java.util.List;

/**
 * Repository per la gestione e la persistenza locale delle preferenze utente.
 * Gestisce la valuta predefinita utilizzata per la creazione automatica delle schede spese.
 */
public class UserPreferencesRepository {

    private static final String PREFS_NAME = "paripari_user_preferences";
    private static final String KEY_DEFAULT_CURRENCY = "pref_default_currency";
    public static final String DEFAULT_CURRENCY = "EUR";

    public static final List<String> SUPPORTED_CURRENCIES = Arrays.asList(
            "EUR - Euro",
            "USD - Dollaro USA",
            "GBP - Sterlina britannica",
            "CHF - Franco svizzero",
            "JPY - Yen giapponese",
            "CAD - Dollaro canadese",
            "AUD - Dollaro australiano",
            "CNY - Yuan cinese",
            "SEK - Corona svedese",
            "NOK - Corona norvegese"
    );

    private static volatile UserPreferencesRepository instance;

    private final SharedPreferences preferences;
    private final MutableLiveData<String> defaultCurrencyLive = new MutableLiveData<>();

    private UserPreferencesRepository(Context context) {
        this.preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String currentCurrency = preferences.getString(KEY_DEFAULT_CURRENCY, DEFAULT_CURRENCY);
        defaultCurrencyLive.setValue(currentCurrency);
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
}
