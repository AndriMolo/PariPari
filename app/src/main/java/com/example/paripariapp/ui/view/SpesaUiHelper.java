package com.example.paripariapp.ui.view;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Filter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.data.model.SpesaPartecipante;
import com.example.paripariapp.data.repository.CurrencyRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Helper per la deduplicazione della logica di calcolo, validazione, auto-bilanciamento
 * e ripartizione delle quote spese condivisa tra NuovaSpesaFragment e ModificaSpesaFragment.
 */
public final class SpesaUiHelper {

    /**
     * Verifica centralizzata per controllare se una spesa o rimborso include partecipanti non più attivi
     * (ex membri usciti o rimossi).
     */
    public static boolean haPartecipantiAssenti(
            @Nullable String pagatoDaId,
            @Nullable String spesaId,
            @Nullable List<SpesaPartecipante> quote,
            @Nullable List<Partecipante> partecipantiGruppo
    ) {
        if (partecipantiGruppo == null || partecipantiGruppo.isEmpty()) {
            return false;
        }
        Set<String> activeIds = new HashSet<>();
        for (Partecipante p : partecipantiGruppo) {
            if (p != null && p.isAttivo()) {
                activeIds.add(p.getId());
            }
        }
        if (pagatoDaId != null && !pagatoDaId.trim().isEmpty() && !activeIds.contains(pagatoDaId)) {
            return true;
        }
        if (quote != null) {
            for (SpesaPartecipante q : quote) {
                if (q == null) continue;
                if (spesaId != null && !spesaId.equals(q.getSpesaId())) {
                    continue;
                }
                if (q.getPartecipanteId() != null && !activeIds.contains(q.getPartecipanteId())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean haPartecipantiAssenti(
            @Nullable Spesa spesa,
            @Nullable List<SpesaPartecipante> quote,
            @Nullable List<Partecipante> partecipantiGruppo
    ) {
        if (spesa == null) return false;
        return haPartecipantiAssenti(spesa.getPagatoDaId(), spesa.getId(), quote, partecipantiGruppo);
    }

    /**
     * Calcola il tasso di cambio attuale per convertire 1 unità di valutaSpesa nella valuta della scheda.
     * Se le valute coincidono o context è nullo, restituisce 1.0.
     */
    public static double calcolaTassoCambioAttuale(
            @Nullable String valutaSpesa,
            @Nullable String valutaScheda,
            @Nullable Context context
    ) {
        if (valutaSpesa == null || valutaScheda == null || valutaSpesa.equalsIgnoreCase(valutaScheda)) {
            return 1.0;
        }
        if (context == null) {
            return 1.0;
        }
        CurrencyRepository repo = CurrencyRepository.getInstance(context.getApplicationContext());
        double rateOrigine = repo.getRate(valutaSpesa);
        double rateDestinazione = repo.getRate(valutaScheda);
        if (rateOrigine <= 0.0) {
            return 1.0;
        }
        return rateDestinazione / rateOrigine;
    }

    public enum TipoDivisione {
        EQUA,
        PERCENTUALE,
        PER_PARTI
    }

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
     * Verifica che la somma degli importi inseriti sia pari all'importo totale (con tolleranza di 0.05 per arrotondamenti).
     */
    public static boolean isSommaImportiValida(double sommaImporti, double importoTotale) {
        return Math.abs(sommaImporti - importoTotale) <= 0.05;
    }

    /**
     * Calcola la divisione iniziale delle percentuali (100% diviso equamente tra tutti gli inclusi).
     */
    @NonNull
    public static Map<String, Double> calcolaDivisioneInizialePercentuale(@NonNull List<Partecipante> partecipantiInclusi) {
        Map<String, Double> result = new HashMap<>();
        if (partecipantiInclusi.isEmpty()) {
            return result;
        }
        int size = partecipantiInclusi.size();
        double quotaBase = Math.round((100.0 / size) * 10.0) / 10.0;
        double assegnato = 0.0;
        for (int i = 0; i < size; i++) {
            Partecipante p = partecipantiInclusi.get(i);
            if (i == size - 1) {
                double quotaFinale = Math.round((100.0 - assegnato) * 10.0) / 10.0;
                result.put(p.getId(), quotaFinale);
            } else {
                result.put(p.getId(), quotaBase);
                assegnato += quotaBase;
            }
        }
        return result;
    }

    /**
     * Calcola la divisione iniziale degli importi (importoTotale diviso equamente tra tutti gli inclusi).
     */
    @NonNull
    public static Map<String, Double> calcolaDivisioneInizialeImporto(double importoTotale, @NonNull List<Partecipante> partecipantiInclusi) {
        Map<String, Double> result = new HashMap<>();
        if (partecipantiInclusi.isEmpty()) {
            return result;
        }
        int size = partecipantiInclusi.size();
        double quotaBase = Math.round((importoTotale / size) * 100.0) / 100.0;
        double assegnato = 0.0;
        for (int i = 0; i < size; i++) {
            Partecipante p = partecipantiInclusi.get(i);
            if (i == size - 1) {
                double quotaFinale = Math.round((importoTotale - assegnato) * 100.0) / 100.0;
                result.put(p.getId(), quotaFinale);
            } else {
                result.put(p.getId(), quotaBase);
                assegnato += quotaBase;
            }
        }
        return result;
    }

    /**
     * Auto-bilancia le percentuali in tempo reale.
     * Quando un partecipante viene modificato dall'utente, il rimanente (100% - bloccati) viene distribuito
     * equamente tra i partecipanti non ancora modificati (o l'ultimo partecipante disponibile).
     * La somma restituita è SEMPRE pari a 100.0%.
     */
    @NonNull
    public static Map<String, Double> bilanciaPercentuali(
            @NonNull List<Partecipante> partecipantiInclusi,
            @NonNull String activeId,
            double activeVal,
            @NonNull Set<String> lockedIds,
            @NonNull Map<String, Double> currentPercMap
    ) {
        Map<String, Double> updated = new HashMap<>(currentPercMap);
        if (partecipantiInclusi.isEmpty()) {
            return updated;
        }

        if (partecipantiInclusi.size() == 1) {
            updated.put(partecipantiInclusi.get(0).getId(), 100.0);
            return updated;
        }

        // Calcola somma degli altri locked esclusi l'activeId
        double sumOtherLocked = 0.0;
        for (Partecipante p : partecipantiInclusi) {
            if (!p.getId().equals(activeId) && lockedIds.contains(p.getId())) {
                Double val = currentPercMap.get(p.getId());
                sumOtherLocked += (val != null ? val : 0.0);
            }
        }

        double maxActive = Math.max(0.0, Math.round((100.0 - sumOtherLocked) * 10.0) / 10.0);
        double clampedActive = Math.min(Math.max(0.0, activeVal), maxActive);
        clampedActive = Math.round(clampedActive * 10.0) / 10.0;
        updated.put(activeId, clampedActive);

        double rimanente = Math.max(0.0, Math.round((100.0 - (sumOtherLocked + clampedActive)) * 10.0) / 10.0);

        List<String> unlocked = new ArrayList<>();
        for (Partecipante p : partecipantiInclusi) {
            if (!p.getId().equals(activeId) && !lockedIds.contains(p.getId())) {
                unlocked.add(p.getId());
            }
        }

        if (!unlocked.isEmpty()) {
            int uSize = unlocked.size();
            double quotaBase = Math.round((rimanente / uSize) * 10.0) / 10.0;
            double allocato = 0.0;
            for (int i = 0; i < uSize; i++) {
                String uId = unlocked.get(i);
                if (i == uSize - 1) {
                    double finale = Math.round((rimanente - allocato) * 10.0) / 10.0;
                    updated.put(uId, Math.max(0.0, finale));
                } else {
                    updated.put(uId, Math.max(0.0, quotaBase));
                    allocato += quotaBase;
                }
            }
        } else {
            // Se tutti sono locked, assorbe l'ultimo partecipante diverso da activeId
            for (int i = partecipantiInclusi.size() - 1; i >= 0; i--) {
                Partecipante p = partecipantiInclusi.get(i);
                if (!p.getId().equals(activeId)) {
                    double val = Math.max(0.0, Math.round((rimanente + (currentPercMap.containsKey(p.getId()) ? currentPercMap.get(p.getId()) : 0.0)) * 10.0) / 10.0);
                    // Ricalcola esattamente per fare 100.0
                    double sumOthers = 0.0;
                    for (Partecipante op : partecipantiInclusi) {
                        if (!op.getId().equals(p.getId())) {
                            Double v = updated.get(op.getId());
                            sumOthers += (v != null ? v : 0.0);
                        }
                    }
                    updated.put(p.getId(), Math.max(0.0, Math.round((100.0 - sumOthers) * 10.0) / 10.0));
                    break;
                }
            }
        }

        return updated;
    }

    /**
     * Auto-bilancia gli importi in tempo reale.
     * Quando un partecipante viene modificato dall'utente, il rimanente (importoTotale - bloccati) viene distribuito
     * equamente tra i partecipanti non ancora modificati (o l'ultimo partecipante disponibile).
     * La somma restituita è SEMPRE pari a importoTotale.
     */
    @NonNull
    public static Map<String, Double> bilanciaImporti(
            double importoTotale,
            @NonNull List<Partecipante> partecipantiInclusi,
            @NonNull String activeId,
            double activeVal,
            @NonNull Set<String> lockedIds,
            @NonNull Map<String, Double> currentImportoMap
    ) {
        Map<String, Double> updated = new HashMap<>(currentImportoMap);
        if (partecipantiInclusi.isEmpty()) {
            return updated;
        }

        if (partecipantiInclusi.size() == 1) {
            updated.put(partecipantiInclusi.get(0).getId(), importoTotale);
            return updated;
        }

        // Calcola somma degli altri locked esclusi l'activeId
        double sumOtherLocked = 0.0;
        for (Partecipante p : partecipantiInclusi) {
            if (!p.getId().equals(activeId) && lockedIds.contains(p.getId())) {
                Double val = currentImportoMap.get(p.getId());
                sumOtherLocked += (val != null ? val : 0.0);
            }
        }

        double maxActive = Math.max(0.0, Math.round((importoTotale - sumOtherLocked) * 100.0) / 100.0);
        double clampedActive = Math.min(Math.max(0.0, activeVal), maxActive);
        clampedActive = Math.round(clampedActive * 100.0) / 100.0;
        updated.put(activeId, clampedActive);

        double rimanente = Math.max(0.0, Math.round((importoTotale - (sumOtherLocked + clampedActive)) * 100.0) / 100.0);

        List<String> unlocked = new ArrayList<>();
        for (Partecipante p : partecipantiInclusi) {
            if (!p.getId().equals(activeId) && !lockedIds.contains(p.getId())) {
                unlocked.add(p.getId());
            }
        }

        if (!unlocked.isEmpty()) {
            int uSize = unlocked.size();
            double quotaBase = Math.round((rimanente / uSize) * 100.0) / 100.0;
            double allocato = 0.0;
            for (int i = 0; i < uSize; i++) {
                String uId = unlocked.get(i);
                if (i == uSize - 1) {
                    double finale = Math.round((rimanente - allocato) * 100.0) / 100.0;
                    updated.put(uId, Math.max(0.0, finale));
                } else {
                    updated.put(uId, Math.max(0.0, quotaBase));
                    allocato += quotaBase;
                }
            }
        } else {
            // Se tutti sono locked, assorbe l'ultimo partecipante diverso da activeId
            for (int i = partecipantiInclusi.size() - 1; i >= 0; i--) {
                Partecipante p = partecipantiInclusi.get(i);
                if (!p.getId().equals(activeId)) {
                    double sumOthers = 0.0;
                    for (Partecipante op : partecipantiInclusi) {
                        if (!op.getId().equals(p.getId())) {
                            Double v = updated.get(op.getId());
                            sumOthers += (v != null ? v : 0.0);
                        }
                    }
                    updated.put(p.getId(), Math.max(0.0, Math.round((importoTotale - sumOthers) * 100.0) / 100.0));
                    break;
                }
            }
        }

        return updated;
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
     * Calcola la ripartizione per parti/importi di una spesa tra i partecipanti inclusi,
     * bilanciando i centesimi sull'ultimo elemento.
     */
    @NonNull
    public static List<SpesaPartecipante> calcolaDivisionePerImporto(
            @NonNull String spesaId,
            double importoTotale,
            @NonNull List<Partecipante> partecipantiInclusi,
            @NonNull List<Partecipante> tuttiPartecipanti,
            @NonNull Map<String, Double> importiInseriti,
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
            Double valObj = importiInseriti.get(p.getId());
            double val = valObj != null ? valObj : 0.0;
            double quotaEuro;

            if (i == size - 1) {
                quotaEuro = Math.round((importoTotale - totaleAssegnato) * 100.0) / 100.0;
            } else {
                quotaEuro = Math.round(val * 100.0) / 100.0;
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
