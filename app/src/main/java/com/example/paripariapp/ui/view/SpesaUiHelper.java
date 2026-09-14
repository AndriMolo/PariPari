package com.example.paripariapp.ui.view;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Filter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.SpesaPartecipante;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Helper per la deduplicazione della logica di calcolo, validazione e ripartizione
 * delle quote spese condivisa tra NuovaSpesaFragment e ModificaSpesaFragment.
 */
public final class SpesaUiHelper {

    private SpesaUiHelper() {
        // Utility class
    }

    /**
     * Esegue il parsing sicuro di una stringa numerica che può contenere virgole o punti decimali.
     * Restituisce 0.0 se la stringa è vuota o non valida.
     */
    public static double parseImporto(@Nullable String input) {
        if (input == null || input.trim().isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(input.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * Verifica che la somma delle percentuali inserite sia pari al 100% (con tolleranza di 0.05 per arrotondamenti).
     */
    public static boolean isSommaPercentualiValida(double sommaPercentuali) {
        return Math.abs(sommaPercentuali - 100.0) <= 0.05;
    }

    /**
     * Calcola la divisione equa di un importo tra i partecipanti selezionati,
     * bilanciando gli arrotondamenti al centesimo sull'ultimo partecipante.
     * Ai partecipanti non inclusi viene assegnata una quota pari a 0.0.
     */
    @NonNull
    public static List<SpesaPartecipante> calcolaDivisioneEqua(
            @NonNull String spesaId,
            double importoTotale,
            @NonNull List<Partecipante> partecipantiInclusi,
            @NonNull List<Partecipante> tuttiPartecipanti,
            int syncStatus
    ) {
        List<SpesaPartecipante> quote = new ArrayList<>();
        if (partecipantiInclusi.isEmpty()) {
            return quote;
        }

        double totaleAssegnato = 0.0;
        int size = partecipantiInclusi.size();
        for (int i = 0; i < size; i++) {
            Partecipante p = partecipantiInclusi.get(i);
            double qVal;
            if (i == size - 1) {
                qVal = Math.round((importoTotale - totaleAssegnato) * 100.0) / 100.0;
            } else {
                qVal = Math.round((importoTotale / size) * 100.0) / 100.0;
                totaleAssegnato += qVal;
            }
            quote.add(new SpesaPartecipante(spesaId, p.getId(), qVal, syncStatus));
        }

        for (Partecipante p : tuttiPartecipanti) {
            boolean incluso = false;
            for (Partecipante inc : partecipantiInclusi) {
                if (inc.getId().equals(p.getId())) {
                    incluso = true;
                    break;
                }
            }
            if (!incluso) {
                quote.add(new SpesaPartecipante(spesaId, p.getId(), 0.0, syncStatus));
            }
        }

        return quote;
    }

    /**
     * Calcola la ripartizione in percentuale di un importo tra i partecipanti inclusi,
     * convertendo le percentuali in valuta e bilanciando i centesimi sull'ultimo elemento.
     */
    @NonNull
    public static List<SpesaPartecipante> calcolaDivisionePercentuale(
            @NonNull String spesaId,
            double importoTotale,
            @NonNull List<Partecipante> partecipantiInclusi,
            @NonNull List<Partecipante> tuttiPartecipanti,
            @NonNull Map<String, Double> percentualiInserite,
            int syncStatus
    ) {
        List<SpesaPartecipante> quote = new ArrayList<>();
        if (partecipantiInclusi.isEmpty()) {
            return quote;
        }

        double totaleAssegnato = 0.0;
        int size = partecipantiInclusi.size();
        for (int i = 0; i < size; i++) {
            Partecipante p = partecipantiInclusi.get(i);
            Double percObj = percentualiInserite.get(p.getId());
            double perc = percObj != null ? percObj : 0.0;
            double quotaEuro;

            if (i == size - 1) {
                quotaEuro = Math.round((importoTotale - totaleAssegnato) * 100.0) / 100.0;
            } else {
                quotaEuro = Math.round((importoTotale * (perc / 100.0)) * 100.0) / 100.0;
                totaleAssegnato += quotaEuro;
            }
            quote.add(new SpesaPartecipante(spesaId, p.getId(), quotaEuro, syncStatus));
        }

        for (Partecipante p : tuttiPartecipanti) {
            boolean incluso = false;
            for (Partecipante inc : partecipantiInclusi) {
                if (inc.getId().equals(p.getId())) {
                    incluso = true;
                    break;
                }
            }
            if (!incluso) {
                quote.add(new SpesaPartecipante(spesaId, p.getId(), 0.0, syncStatus));
            }
        }

        return quote;
    }

    /**
     * Crea un ArrayAdapter non-filtrante per AutoCompleteTextView / Dropdown Material.
     */
    @NonNull
    public static ArrayAdapter<String> creaDropdownAdapter(@NonNull Context context, @NonNull List<String> items) {
        return new ArrayAdapter<String>(context, android.R.layout.simple_dropdown_item_1line, items) {
            @NonNull
            @Override
            public Filter getFilter() {
                return new Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        FilterResults results = new FilterResults();
                        results.values = items;
                        results.count = items.size();
                        return results;
                    }

                    @Override
                    protected void publishResults(CharSequence constraint, FilterResults results) {
                        notifyDataSetChanged();
                    }
                };
            }
        };
    }
}
