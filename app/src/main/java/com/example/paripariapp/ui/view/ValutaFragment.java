package com.example.paripariapp.ui.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.paripariapp.databinding.FragmentValutaBinding;

import java.util.Arrays;
import java.util.List;

/**
 * Fragment della pagina "Conversione Valuta".
 * Permette di convertire un importo da una valuta a un'altra.
 */
public class ValutaFragment extends Fragment {

    private FragmentValutaBinding binding;

    // Lista delle valute supportate
    private static final List<String> VALUTE = Arrays.asList(
            "EUR - Euro",
            "USD - Dollaro USA",
            "GBP - Sterlina britannica",
            "JPY - Yen giapponese",
            "CHF - Franco svizzero",
            "CAD - Dollaro canadese",
            "AUD - Dollaro australiano",
            "CNY - Yuan cinese",
            "SEK - Corona svedese",
            "NOK - Corona norvegese"
    );

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

        // Popola i dropdown delle valute
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                VALUTE
        );
        binding.spinnerValutaDa.setAdapter(adapter);
        binding.spinnerValutaA.setAdapter(adapter);

        // Selezione predefinita
        binding.spinnerValutaDa.setText(VALUTE.get(0), false);
        binding.spinnerValutaA.setText(VALUTE.get(1), false);

        // Pulsante Inverti
        binding.btnInverti.setOnClickListener(v -> {
            String da = binding.spinnerValutaDa.getText().toString();
            String a = binding.spinnerValutaA.getText().toString();
            binding.spinnerValutaDa.setText(a, false);
            binding.spinnerValutaA.setText(da, false);
        });

        // Pulsante Converti
        binding.btnConverti.setOnClickListener(v -> {
            // TODO: collegare ViewModel con chiamata API tassi di cambio (es. ExchangeRate-API)
            binding.cardRisultato.setVisibility(View.VISIBLE);
            binding.tvRisultato.setText("— in arrivo —");
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
