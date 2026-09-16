package com.example.paripariapp.util;

import androidx.annotation.Nullable;

import com.example.paripariapp.data.model.SpesaConDettagli;
import com.example.paripariapp.data.model.SpesaPartecipante;
import com.example.paripariapp.data.model.TrasferimentoSaldo;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility per il filtraggio avanzato dell'utente ("Solo se ci sei anche tu").
 * Consente di verificare se l'utente attivo è direttamente coinvolto in una spesa o in un saldo.
 */
public final class FiltroUtenteUtil {

    private FiltroUtenteUtil() {
        // Utility class
    }

    /**
     * Verifica se un partecipante è direttamente coinvolto in una spesa (come pagatore o tra chi partecipa alla quota).
     */
    public static boolean isUtenteCoinvoltoInSpesa(@Nullable SpesaConDettagli spesaConDettagli,
                                                  @Nullable List<SpesaPartecipante> quote,
                                                  @Nullable String myPartecipanteId) {
        if (myPartecipanteId == null || spesaConDettagli == null || spesaConDettagli.getSpesa() == null) {
            return true;
        }

        String spesaId = spesaConDettagli.getSpesa().getId();
        String pagatoDaId = spesaConDettagli.getSpesa().getPagatoDaId();

        if (myPartecipanteId.equals(pagatoDaId)) {
            return true;
        }

        if (quote != null) {
            for (SpesaPartecipante q : quote) {
                if (q.getSpesaId() != null && q.getSpesaId().equals(spesaId) &&
                        q.getPartecipanteId() != null && q.getPartecipanteId().equals(myPartecipanteId)) {
                    if (q.getQuota() > 0) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Verifica se un partecipante è direttamente coinvolto in un trasferimento di saldo (come debitore o creditore).
     */
    public static boolean isUtenteCoinvoltoInTrasferimento(@Nullable TrasferimentoSaldo t,
                                                          @Nullable String myPartecipanteId) {
        if (myPartecipanteId == null || t == null) {
            return true;
        }
        return myPartecipanteId.equals(t.getDaPartecipanteId()) || myPartecipanteId.equals(t.getAPartecipanteId());
    }

    /**
     * Filtra una lista di spese per includere solo quelle in cui l'utente è coinvolto.
     */
    public static List<SpesaConDettagli> filtraSpesePerUtente(@Nullable List<SpesaConDettagli> listaSpese,
                                                            @Nullable List<SpesaPartecipante> quote,
                                                            @Nullable String myPartecipanteId) {
        List<SpesaConDettagli> risultante = new ArrayList<>();
        if (listaSpese == null) return risultante;

        for (SpesaConDettagli scd : listaSpese) {
            if (isUtenteCoinvoltoInSpesa(scd, quote, myPartecipanteId)) {
                risultante.add(scd);
            }
        }
        return risultante;
    }

    /**
     * Filtra una lista di trasferimenti di saldo per includere solo quelli in cui l'utente è coinvolto.
     */
    public static List<TrasferimentoSaldo> filtraTrasferimentiPerUtente(@Nullable List<TrasferimentoSaldo> listaTrasferimenti,
                                                                      @Nullable String myPartecipanteId) {
        List<TrasferimentoSaldo> risultante = new ArrayList<>();
        if (listaTrasferimenti == null) return risultante;

        for (TrasferimentoSaldo t : listaTrasferimenti) {
            if (isUtenteCoinvoltoInTrasferimento(t, myPartecipanteId)) {
                risultante.add(t);
            }
        }
        return risultante;
    }
}
