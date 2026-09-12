package com.example.paripariapp.ui.view;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.paripariapp.R;
import com.example.paripariapp.data.repository.UserPreferencesRepository;
import com.example.paripariapp.databinding.FragmentValutaBinding;
import com.example.paripariapp.ui.viewmodel.ValutaViewModel;

import java.util.List;

/**
 * Fragment della pagina "Conversione Valuta".
 * Utilizza la Frankfurter API (dati BCE ufficiali) con caching locale (<24h)
 * e validazione Regex dell'importo per conversioni rapide e offline-first.
 */
public class ValutaFragment extends Fragment {

    private FragmentValutaBinding binding;
    private ValutaViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentValutaBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(ValutaViewModel.class);

        setupCurrencySelectors();
        setupObservers();
        setupListeners();
    }

    private void setupCurrencySelectors() {
        UserPreferencesRepository prefs = UserPreferencesRepository.getInstance(requireContext());
        String defaultCurrency = prefs.getDefaultCurrency();
        if (TextUtils.isEmpty(defaultCurrency)) {
            defaultCurrency = UserPreferencesRepository.DEFAULT_CURRENCY; // Fallback su EUR
        }

        String valutaDa = UserPreferencesRepository.getDisplayItemForCode(defaultCurrency);
        String targetCurrency = "USD".equalsIgnoreCase(defaultCurrency) ? "EUR" : "USD";
        String valutaA = UserPreferencesRepository.getDisplayItemForCode(targetCurrency);

        binding.spinnerValutaDa.setText(valutaDa);
        binding.spinnerValutaA.setText(valutaA);

        // Apertura Searchable Bottom Sheet (Opzione A)
        binding.spinnerValutaDa.setOnClickListener(v -> openCurrencyPicker(true));
        binding.tilValutaDa.setOnClickListener(v -> openCurrencyPicker(true));
        binding.tilValutaDa.setEndIconOnClickListener(v -> openCurrencyPicker(true));

        binding.spinnerValutaA.setOnClickListener(v -> openCurrencyPicker(false));
        binding.tilValutaA.setOnClickListener(v -> openCurrencyPicker(false));
        binding.tilValutaA.setEndIconOnClickListener(v -> openCurrencyPicker(false));
    }

    private void openCurrencyPicker(boolean isDa) {
        String currentSelection = isDa
                ? (binding.spinnerValutaDa.getText() != null ? binding.spinnerValutaDa.getText().toString() : "")
                : (binding.spinnerValutaA.getText() != null ? binding.spinnerValutaA.getText().toString() : "");

        SelettoreValutaBottomSheet sheet = SelettoreValutaBottomSheet.newInstance(currentSelection);
        sheet.setOnCurrencySelectedListener(currencyFull -> {
            if (binding == null) return;
            if (isDa) {
                binding.spinnerValutaDa.setText(currencyFull);
            } else {
                binding.spinnerValutaA.setText(currencyFull);
            }
            eseguiConversione();
        });
        sheet.show(getChildFragmentManager(), isDa ? "PICKER_VALUTA_DA" : "PICKER_VALUTA_A");
    }

    private void setupObservers() {
        // Data ultimo aggiornamento dei tassi BCE (in grigio chiaro in basso)
        viewModel.getLastUpdatedDate().observe(getViewLifecycleOwner(), date -> {
            if (!TextUtils.isEmpty(date)) {
                binding.tvUltimoAggiornamento.setText(getString(R.string.tassi_bce_aggiornati, date));
            }
        });

        // Risultato della conversione in tempo reale
        viewModel.getConversionResult().observe(getViewLifecycleOwner(), result -> {
            if (!TextUtils.isEmpty(result)) {
                binding.cardRisultato.setVisibility(View.VISIBLE);
                binding.tvRisultato.setText(result);
            } else {
                binding.cardRisultato.setVisibility(View.GONE);
            }
        });

        // Dettaglio del tasso di cambio applicato (es. 1 EUR = 1.0850 USD)
        viewModel.getRateDetail().observe(getViewLifecycleOwner(), detail -> {
            if (!TextUtils.isEmpty(detail)) {
                binding.tvDettaglioTasso.setText(detail);
                binding.tvDettaglioTasso.setVisibility(View.VISIBLE);
            } else {
                binding.tvDettaglioTasso.setVisibility(View.GONE);
            }
        });

        // Errori di validazione (tramite Regex)
        viewModel.getValidationError().observe(getViewLifecycleOwner(), error -> {
            binding.tilImporto.setError(error);
        });

        // Stato di caricamento (download da rete Frankfurter)
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loading -> {
            binding.progressValuta.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE);
        });
    }

    private void setupListeners() {
        // Conversione immediata in tempo reale mentre l'utente digita (As-You-Type)
        binding.etImporto.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.tilImporto.setError(null);
                eseguiConversione();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Pulsante Inverti valute: scambia e ricalcola all'istante
        binding.btnInverti.setOnClickListener(v -> {
            String da = binding.spinnerValutaDa.getText() != null ? binding.spinnerValutaDa.getText().toString() : "";
            String a = binding.spinnerValutaA.getText() != null ? binding.spinnerValutaA.getText().toString() : "";

            binding.spinnerValutaDa.setText(a);
            binding.spinnerValutaA.setText(da);

            eseguiConversione();
        });
    }

    private void eseguiConversione() {
        String importo = getTestoImporto();
        String valutaDa = binding.spinnerValutaDa.getText() != null ? binding.spinnerValutaDa.getText().toString() : "";
        String valutaA = binding.spinnerValutaA.getText() != null ? binding.spinnerValutaA.getText().toString() : "";

        viewModel.converti(importo, valutaDa, valutaA);
    }

    private String getTestoImporto() {
        return binding.etImporto.getText() != null ? binding.etImporto.getText().toString().trim() : "";
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Prevenzione memory leak
    }
}
