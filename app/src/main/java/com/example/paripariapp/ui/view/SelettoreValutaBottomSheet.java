package com.example.paripariapp.ui.view;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.paripariapp.data.repository.UserPreferencesRepository;
import com.example.paripariapp.databinding.BottomSheetSelettoreValutaBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * Searchable Bottom Sheet per la selezione rapida e fluida delle valute (Opzione A).
 * Permette la ricerca istantanea (codice e nome), la selezione rapida delle valute
 * più frequenti tramite Quick Chips e l'esplorazione dell'intero catalogo BCE.
 */
public class SelettoreValutaBottomSheet extends BottomSheetDialogFragment {

    public interface OnCurrencySelectedListener {
        void onCurrencySelected(String currencyFull);
    }

    private static final String ARG_CURRENT_SELECTION = "arg_current_selection";

    private BottomSheetSelettoreValutaBinding binding;
    private ValutaSelettoreAdapter adapter;
    private OnCurrencySelectedListener listener;

    public static SelettoreValutaBottomSheet newInstance(@Nullable String currentlySelected) {
        SelettoreValutaBottomSheet sheet = new SelettoreValutaBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_CURRENT_SELECTION, currentlySelected);
        sheet.setArguments(args);
        return sheet;
    }

    public void setOnCurrencySelectedListener(OnCurrencySelectedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetSelettoreValutaBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String currentSelection = getArguments() != null ? getArguments().getString(ARG_CURRENT_SELECTION) : null;

        setupRecyclerView(currentSelection);
        setupQuickChips();
        setupSearch();
    }

    private void setupRecyclerView(@Nullable String currentSelection) {
        binding.rvValute.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ValutaSelettoreAdapter(
                UserPreferencesRepository.SUPPORTED_CURRENCIES,
                currentSelection,
                currencyFull -> selectAndDismiss(currencyFull)
        );
        binding.rvValute.setAdapter(adapter);
    }

    private void setupQuickChips() {
        binding.chipEur.setOnClickListener(v -> selectAndDismiss(UserPreferencesRepository.getDisplayItemForCode("EUR")));
        binding.chipUsd.setOnClickListener(v -> selectAndDismiss(UserPreferencesRepository.getDisplayItemForCode("USD")));
        binding.chipGbp.setOnClickListener(v -> selectAndDismiss(UserPreferencesRepository.getDisplayItemForCode("GBP")));
        binding.chipChf.setOnClickListener(v -> selectAndDismiss(UserPreferencesRepository.getDisplayItemForCode("CHF")));
    }

    private void setupSearch() {
        binding.etCercaValuta.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s != null ? s.toString().trim() : "";
                int matches = adapter.filter(query);

                boolean isSearching = !query.isEmpty();
                binding.layoutFrequenti.setVisibility(isSearching ? View.GONE : View.VISIBLE);
                binding.tvHeaderTutte.setVisibility(isSearching ? View.GONE : View.VISIBLE);
                binding.tvEmptyValute.setVisibility(matches == 0 ? View.VISIBLE : View.GONE);
                binding.rvValute.setVisibility(matches == 0 ? View.GONE : View.VISIBLE);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void selectAndDismiss(String currencyFull) {
        if (listener != null) {
            listener.onCurrencySelected(currencyFull);
        }
        dismiss();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Prevenzione memory leak
    }
}
