package com.example.paripariapp.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.paripariapp.data.model.BilancioPersonaItem;
import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.Scheda;
import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.data.model.SpesaConDettagli;
import com.example.paripariapp.data.model.SpesaPartecipante;
import com.example.paripariapp.data.model.TrasferimentoSaldo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utility per il filtraggio intelligente basato sull'utente corrente.
 * Implementa la logica "Solo se ci sei anche tu" per spese, rimborsi, saldi e cronologia.
 */
public class FiltroUtenteUtil {

    /**
     * Verifica se l'utente con `mioPartecipanteId` è coinvolto in una spesa (come pagatore o partecipante alla quota).
     */
    public static boolean isUtenteCoinvoltoInSpesa(
            @Nullable Spesa spesa,
            @Nullable List<SpesaPartecipante> quote,
            @Nullable String mioPartecipanteId
    ) {
        if (spesa == null || mioPartecipanteId == null || mioPartecipanteId.trim().isEmpty()) {
            return true;
        }

        // 1. Pagatore
        if (mioPartecipanteId.equals(spesa.getPagatoDaId())) {
            return true;
        }

        // 2. Partecipante alla quota
        if (quote != null) {
            for (SpesaPartecipante q : quote) {
                if (spesa.getId().equals(q.getSpesaId()) && mioPartecipanteId.equals(q.getPartecipanteId())) {
                    if (q.getQuota() > 0.001 || q.getQuotaPagata() > 0.001) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Filtra una lista di SpesaConDettagli mantenendo solo quelle in cui l'utente è coinvolto.
     */
    public static List<SpesaConDettagli> filtraSpeseCoinvolgonoUtente(
            @Nullable List<SpesaConDettagli> spese,
            @Nullable Map<String, List<SpesaPartecipante>> quoteMap,
            @Nullable String mioPartecipanteId
    ) {
        List<SpesaConDettagli> ris = new ArrayList<>();
        if (spese == null) return ris;

        if (mioPartecipanteId == null || mioPartecipanteId.trim().isEmpty()) {
            return new ArrayList<>(spese);
        }

        for (SpesaConDettagli scd : spese) {
            if (scd == null || scd.getSpesa() == null) continue;
            Spesa s = scd.getSpesa();
            List<SpesaPartecipante> quote = (quoteMap != null) ? quoteMap.get(s.getId()) : null;
            if (isUtenteCoinvoltoInSpesa(s, quote, mioPartecipanteId)) {
                ris.add(scd);
            }
        }
        return ris;
    }

    /**
     * Filtra una lista di TrasferimentoSaldo mantenendo solo quelli che coinvolgono l'utente come debitore o creditore.
     */
    public static List<TrasferimentoSaldo> filtraTrasferimentiCoinvolgonoUtente(
            @Nullable List<TrasferimentoSaldo> trasferimenti,
            @Nullable String mioPartecipanteId
    ) {
        List<TrasferimentoSaldo> ris = new ArrayList<>();
        if (trasferimenti == null) return ris;

        if (mioPartecipanteId == null || mioPartecipanteId.trim().isEmpty()) {
            return new ArrayList<>(trasferimenti);
        }

        for (TrasferimentoSaldo t : trasferimenti) {
            if (t == null) continue;
            if (mioPartecipanteId.equals(t.getDaPartecipanteId()) || mioPartecipanteId.equals(t.getAPartecipanteId())) {
                ris.add(t);
            }
        }
        return ris;
    }

    /**
     * Filtra i BilancioPersonaItem mantenendo solo quelli dell'utente corrente.
     */
    public static List<BilancioPersonaItem> filtraBilanciCoinvolgonoUtente(
            @Nullable List<BilancioPersonaItem> bilanci,
            @Nullable String mioPartecipanteId
    ) {
        List<BilancioPersonaItem> ris = new ArrayList<>();
        if (bilanci == null) return ris;

        if (mioPartecipanteId == null || mioPartecipanteId.trim().isEmpty()) {
            return new ArrayList<>(bilanci);
        }

        for (BilancioPersonaItem b : bilanci) {
            if (b == null || b.getTrasferimentoSaldo() == null) continue;
            TrasferimentoSaldo t = b.getTrasferimentoSaldo();
            if (mioPartecipanteId.equals(t.getDaPartecipanteId()) || mioPartecipanteId.equals(t.getAPartecipanteId())) {
                ris.add(b);
            }
        }
        return ris;
    }

    /**
     * Determina reattivamente se l'utente corrente è il proprietario/creatore della scheda.
     */
    public static boolean isOwner(
            @Nullable Scheda scheda,
            @Nullable String mioPartecipanteId,
            @Nullable String currentUserId
    ) {
        if (scheda == null) return false;
        String creatoreId = scheda.getCreatoreId();
        if (creatoreId == null || creatoreId.trim().isEmpty()) return false;

        if (mioPartecipanteId != null && creatoreId.equals(mioPartecipanteId)) {
            return true;
        }

        if (currentUserId != null && creatoreId.equals(currentUserId)) {
            return true;
        }

        return false;
    }
}
