package com.example.paripariapp.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository per il recupero e il caching locale dei tassi di cambio della Banca Centrale Europea (BCE)
 * tramite l'API pubblica Frankfurter (https://api.frankfurter.dev).
 * Segue l'approccio Offline-First con aggiornamento al massimo ogni 24 ore.
 */
public class CurrencyRepository {

    private static final String TAG = "CurrencyRepository";
    private static final String PREF_NAME = "paripari_currency_cache";
    private static final String KEY_LAST_FETCH = "last_fetch_timestamp";
    private static final String KEY_BCE_DATE = "bce_date";
    private static final String KEY_RATES_JSON = "rates_json";
    private static final String API_URL = "https://api.frankfurter.dev/v1/latest?from=EUR";

    // 24 ore in millisecondi
    private static final long CACHE_VALIDITY_MS = 24L * 60 * 60 * 1000L;

    private static volatile CurrencyRepository instance;

    private final SharedPreferences prefs;
    private final ExecutorService executor;
    private final Map<String, Double> ratesCache = new ConcurrentHashMap<>();

    private final MutableLiveData<String> lastUpdatedDate = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    private CurrencyRepository(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.executor = Executors.newSingleThreadExecutor();

        // Inizializza con i tassi di fallback predefiniti (funzionamento offline garantito)
        initDefaultFallbackRates();

        // Carica la cache locale dalle SharedPreferences (se presente)
        loadFromSharedPreferences();

        // Controlla se la cache è scaduta (>24h) e aggiorna in background
        fetchRatesIfNeeded(false);
    }

    public static CurrencyRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (CurrencyRepository.class) {
                if (instance == null) {
                    instance = new CurrencyRepository(context);
                }
            }
        }
        return instance;
    }

    public LiveData<String> getLastUpdatedDate() {
        return lastUpdatedDate;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    /**
     * Restituisce il tasso di cambio di una valuta rispetto all'Euro (EUR = 1.0).
     */
    public double getRate(String currencyCode) {
        if ("EUR".equalsIgnoreCase(currencyCode)) {
            return 1.0;
        }
        Double rate = ratesCache.get(currencyCode.toUpperCase());
        return rate != null ? rate : 1.0;
    }

    /**
     * Verifica se sono passate più di 24 ore dall'ultimo aggiornamento.
     * Se necessario, scarica i nuovi tassi in background.
     */
    public void fetchRatesIfNeeded(boolean force) {
        long lastFetch = prefs.getLong(KEY_LAST_FETCH, 0);
        long now = System.currentTimeMillis();

        if (!force && (now - lastFetch) < CACHE_VALIDITY_MS && !ratesCache.isEmpty()) {
            Log.d(TAG, "Cache tassi ancora valida (<24h). Nessuna chiamata di rete necessaria.");
            String savedDate = prefs.getString(KEY_BCE_DATE, "");
            lastUpdatedDate.postValue(formatDateForDisplay(savedDate));
            return;
        }

        // Avvia download in background
        isLoading.postValue(true);
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(API_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("Accept", "application/json");

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)
                    );
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject root = new JSONObject(response.toString());
                    String bceDate = root.optString("date", "");
                    JSONObject ratesObj = root.getJSONObject("rates");

                    Map<String, Double> newRates = new HashMap<>();
                    newRates.put("EUR", 1.0);

                    Iterator<String> keys = ratesObj.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        newRates.put(key, ratesObj.getDouble(key));
                    }

                    // Aggiorna cache in memoria e disco
                    ratesCache.putAll(newRates);
                    prefs.edit()
                            .putLong(KEY_LAST_FETCH, System.currentTimeMillis())
                            .putString(KEY_BCE_DATE, bceDate)
                            .putString(KEY_RATES_JSON, ratesObj.toString())
                            .apply();

                    lastUpdatedDate.postValue(formatDateForDisplay(bceDate));
                    Log.i(TAG, "Tassi Frankfurter BCE aggiornati con successo al " + bceDate);
                } else {
                    Log.w(TAG, "Risposta server Frankfurter non OK: " + responseCode);
                }
            } catch (Exception e) {
                Log.w(TAG, "Impossibile contattare Frankfurter API, mantengo tassi locali: " + e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                isLoading.postValue(false);
            }
        });
    }

    private void loadFromSharedPreferences() {
        String json = prefs.getString(KEY_RATES_JSON, null);
        String savedDate = prefs.getString(KEY_BCE_DATE, "");

        if (json != null) {
            try {
                JSONObject obj = new JSONObject(json);
                Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    ratesCache.put(k, obj.getDouble(k));
                }
                if (!savedDate.isEmpty()) {
                    lastUpdatedDate.postValue(formatDateForDisplay(savedDate));
                }
                return;
            } catch (Exception e) {
                Log.e(TAG, "Errore durante il parsing della cache tassi", e);
            }
        }
        // Se non c'è cache salvata, imposta la data di default
        lastUpdatedDate.postValue("08/09/2026");
    }

    private void initDefaultFallbackRates() {
        ratesCache.put("EUR", 1.0);
        ratesCache.put("USD", 1.085);
        ratesCache.put("GBP", 0.854);
        ratesCache.put("CHF", 0.957);
        ratesCache.put("JPY", 160.20);
        ratesCache.put("CAD", 1.482);
        ratesCache.put("AUD", 1.655);
        ratesCache.put("CNY", 7.850);
        ratesCache.put("SEK", 11.350);
        ratesCache.put("NOK", 11.580);
    }

    private String formatDateForDisplay(String isoDate) {
        if (isoDate == null || isoDate.trim().isEmpty()) {
            return "08/09/2026";
        }
        try {
            String[] parts = isoDate.split("-");
            if (parts.length == 3) {
                return parts[2] + "/" + parts[1] + "/" + parts[0];
            }
        } catch (Exception ignored) {
        }
        return isoDate;
    }
}
