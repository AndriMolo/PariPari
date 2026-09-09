package com.example.paripariapp.ui.viewmodel;

import android.app.Application;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.paripariapp.R;
import com.example.paripariapp.data.repository.CurrencyRepository;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * ViewModel per la conversione valuta.
 * Gestisce la validazione regex dell'importo, il calcolo della conversione e l'aggiornamento dei tassi.
 */
public class ValutaViewModel extends AndroidViewModel {

    // Regex per validare numeri con fino a 2 cifre decimali (con punto o virgola)
    private static final Pattern AMOUNT_REGEX = Pattern.compile("^[0-9]+([.,][0-9]{1,2})?$");

    private final CurrencyRepository repository;

    private final MutableLiveData<String> conversionResult = new MutableLiveData<>();
    private final MutableLiveData<String> rateDetail = new MutableLiveData<>();
    private final MutableLiveData<String> validationError = new MutableLiveData<>();

    public ValutaViewModel(@NonNull Application application) {
        super(application);
        repository = CurrencyRepository.getInstance(application);
    }

    public LiveData<String> getConversionResult() {
        return conversionResult;
    }

    public LiveData<String> getRateDetail() {
        return rateDetail;
    }

    public LiveData<String> getValidationError() {
        return validationError;
    }

    public LiveData<String> getLastUpdatedDate() {
        return repository.getLastUpdatedDate();
    }

    public LiveData<Boolean> getIsLoading() {
        return repository.getIsLoading();
    }

    /**
     * Esegue la conversione tra due valute selezionate.
     *
     * @param amountStr     testo digitato dall'utente
     * @param valutaDaFull  stringa estesa es. "EUR - Euro"
     * @param valutaAFull   stringa estesa es. "USD - Dollaro USA"
     */
    public void converti(String amountStr, String valutaDaFull, String valutaAFull) {
        validationError.setValue(null);

        if (TextUtils.isEmpty(amountStr)) {
            conversionResult.setValue(null);
            rateDetail.setValue(null);
            return;
        }

        String trimmed = amountStr.trim();
        if (trimmed.equals(".") || trimmed.equals(",")) {
            return;
        }

        if (!AMOUNT_REGEX.matcher(trimmed).matches()) {
            validationError.setValue(getApplication().getString(R.string.error_importo_valuta));
            return;
        }

        try {
            double amount = Double.parseDouble(amountStr.trim().replace(',', '.'));
            if (amount <= 0) {
                validationError.setValue(getApplication().getString(R.string.error_importo_zero));
                return;
            }

            String codeDa = extractCurrencyCode(valutaDaFull);
            String codeA = extractCurrencyCode(valutaAFull);

            double rateDa = repository.getRate(codeDa);
            double rateA = repository.getRate(codeA);

            if (rateDa <= 0) rateDa = 1.0;
            if (rateA <= 0) rateA = 1.0;

            // Conversione basata sull'Euro come perno comune (EUR = 1.0)
            double amountInEur = amount / rateDa;
            double convertedAmount = amountInEur * rateA;

            // Calcolo tasso unitario tra le due valute
            double unitRate = (1.0 / rateDa) * rateA;

            // Formattazione con 2 cifre decimali
            String formattedResult = String.format(Locale.ITALY, "%.2f %s", convertedAmount, codeA);
            String formattedDetail = String.format(Locale.ITALY, "1 %s = %.4f %s", codeDa, unitRate, codeA);

            conversionResult.setValue(formattedResult);
            rateDetail.setValue(formattedDetail);

        } catch (NumberFormatException e) {
            validationError.setValue(getApplication().getString(R.string.error_formato_numerico));
        }
    }

    public void forceRefreshRates() {
        repository.fetchRatesIfNeeded(true);
    }

    private String extractCurrencyCode(String fullString) {
        if (fullString != null && fullString.length() >= 3) {
            return fullString.substring(0, 3).toUpperCase(Locale.ROOT).trim();
        }
        return "EUR";
    }
}
