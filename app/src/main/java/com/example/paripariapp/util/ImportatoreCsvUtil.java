package com.example.paripariapp.util;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.data.model.SpesaPartecipante;
import com.example.paripariapp.data.model.SyncStatus;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Utility avanzata per l'analisi ed importazione di file CSV esportati da PariPari
 * (o strutturati in modo equivalente), ricostruendo integralmente la scheda,
 * i membri e l'elenco delle spese con le relative quote di ripartizione.
 */
public class ImportatoreCsvUtil {

    private static final String TAG = "ImportatoreCsvUtil";

    public static class SpesaConQuote {
        public final Spesa spesa;
        public final List<SpesaPartecipante> quote;

        public SpesaConQuote(Spesa spesa, List<SpesaPartecipante> quote) {
            this.spesa = spesa;
            this.quote = quote;
        }
    }

    public static class RisultatoImportazione {
        public final String nomeScheda;
        public final String valuta;
        public final List<Partecipante> partecipanti;
        public final List<SpesaConQuote> speseConQuote;

        public RisultatoImportazione(String nomeScheda, String valuta,
                                   List<Partecipante> partecipanti,
                                   List<SpesaConQuote> speseConQuote) {
            this.nomeScheda = nomeScheda;
            this.valuta = valuta;
            this.partecipanti = partecipanti;
            this.speseConQuote = speseConQuote;
        }
    }

    private static final SimpleDateFormat FORMATO_DATA =
            new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY);
    private static final SimpleDateFormat FORMATO_GIORNO =
            new SimpleDateFormat("dd/MM/yyyy", Locale.ITALY);

    @Nullable
    public static RisultatoImportazione analizzaCsv(@NonNull Context context, @NonNull Uri csvUri, @NonNull String schedaId) {
        try (InputStream is = context.getContentResolver().openInputStream(csvUri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            String nomeGruppo = "Gruppo Importato";
            String valutaGruppo = "EUR";
            List<String> nomiMembriMetadati = new ArrayList<>();

            List<String[]> righeDati = new ArrayList<>();
            String line;
            boolean inDataSection = false;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("\ufeff")) {
                    line = line.substring(1);
                }
                String lineTrim = line.trim();
                if (lineTrim.isEmpty()) continue;

                // 1. Parsing righe metadati (#)
                if (lineTrim.startsWith("#")) {
                    String lineClean = lineTrim.substring(1).trim();
                    String upper = lineClean.toUpperCase(Locale.ROOT);
                    if (upper.startsWith("GRUPPO;") || upper.startsWith("GRUPPO,")) {
                        String[] parts = lineClean.split("[;,]", 2);
                        if (parts.length >= 2) {
                            nomeGruppo = pulisciCampo(parts[1]);
                        }
                    } else if (upper.startsWith("VALUTA;") || upper.startsWith("VALUTA,")) {
                        String[] parts = lineClean.split("[;,]", 2);
                        if (parts.length >= 2) {
                            valutaGruppo = pulisciCampo(parts[1]);
                        }
                    } else if (upper.startsWith("MEMBRI;") || upper.startsWith("MEMBRI,")) {
                        String[] parts = lineClean.split("[;,]");
                        for (int i = 1; i < parts.length; i++) {
                            String m = pulisciCampo(parts[i]);
                            if (!m.isEmpty() && !nomiMembriMetadati.contains(m)) {
                                nomiMembriMetadati.add(m);
                            }
                        }
                    }
                    continue;
                }

                // 2. Riga Intestazione Tabelle
                String lowerLine = lineTrim.toLowerCase(Locale.ROOT);
                if (lowerLine.startsWith("data;") || lowerLine.startsWith("date;") || lowerLine.startsWith("data,") || lowerLine.startsWith("date,")) {
                    inDataSection = true;
                    continue;
                }

                // 3. Righe Dati Spese
                if (inDataSection || lineTrim.contains(";") || lineTrim.contains(",")) {
                    String[] tokens = dividiRigaCsv(lineTrim);
                    if (tokens.length >= 5) {
                        righeDati.add(tokens);
                    }
                }
            }

            // Raccogli ed estrai la lista completa dei partecipanti (da metadati e da dati pagatori/quote)
            Map<String, Partecipante> mappaMembri = new HashMap<>();

            for (String nomeM : nomiMembriMetadati) {
                String nomeP = Partecipante.pulisciNome(nomeM);
                if (!nomeP.isEmpty() && !mappaMembri.containsKey(nomeP.toLowerCase(Locale.ROOT))) {
                    String pId = UUID.randomUUID().toString();
                    mappaMembri.put(nomeP.toLowerCase(Locale.ROOT),
                            new Partecipante(pId, schedaId, nomeP, null, SyncStatus.PENDING_INSERT));
                }
            }

            for (String[] row : righeDati) {
                if (row.length >= 7) {
                    String paganteNome = pulisciCampo(row[6]);
                    if (!paganteNome.isEmpty() && !mappaMembri.containsKey(paganteNome.toLowerCase(Locale.ROOT))) {
                        String pId = UUID.randomUUID().toString();
                        mappaMembri.put(paganteNome.toLowerCase(Locale.ROOT),
                                new Partecipante(pId, schedaId, paganteNome, null, SyncStatus.PENDING_INSERT));
                    }
                }
                if (row.length >= 8) {
                    String quoteStr = pulisciCampo(row[7]);
                    String[] quotePairs = quoteStr.split("\\|");
                    for (String qPair : quotePairs) {
                        if (qPair.contains(":")) {
                            String[] qParts = qPair.split(":");
                            String nomeM = pulisciCampo(qParts[0]);
                            if (!nomeM.isEmpty() && !mappaMembri.containsKey(nomeM.toLowerCase(Locale.ROOT))) {
                                String pId = UUID.randomUUID().toString();
                                mappaMembri.put(nomeM.toLowerCase(Locale.ROOT),
                                        new Partecipante(pId, schedaId, nomeM, null, SyncStatus.PENDING_INSERT));
                            }
                        }
                    }
                }
            }

            List<Partecipante> partecipanti = new ArrayList<>(mappaMembri.values());

            // Costruisci le Spese e le relative Quote
            List<SpesaConQuote> speseConQuote = new ArrayList<>();

            for (String[] row : righeDati) {
                String dataStr = pulisciCampo(row[0]);
                String tipoStr = row.length > 1 ? pulisciCampo(row[1]) : "Spesa";
                String descStr = row.length > 2 ? pulisciCampo(row[2]) : "Spesa Importata";
                String catStr = row.length > 3 ? pulisciCampo(row[3]) : "Generale";
                String importoStr = row.length > 4 ? pulisciCampo(row[4]).replace(",", ".") : "0";
                String valutaStr = row.length > 5 ? pulisciCampo(row[5]) : valutaGruppo;
                String paganteStr = row.length > 6 ? pulisciCampo(row[6]) : "";

                if (descStr.isEmpty()) descStr = "Spesa";

                double importo = 0.0;
                try {
                    importo = Double.parseDouble(importoStr);
                } catch (NumberFormatException ignored) {}

                if (importo <= 0) continue;

                long timestamp = System.currentTimeMillis();
                try {
                    Date d = FORMATO_DATA.parse(dataStr);
                    if (d != null) timestamp = d.getTime();
                } catch (Exception e1) {
                    try {
                        Date d2 = FORMATO_GIORNO.parse(dataStr);
                        if (d2 != null) timestamp = d2.getTime();
                    } catch (Exception ignored) {}
                }

                Partecipante pPagante = mappaMembri.get(paganteStr.toLowerCase(Locale.ROOT));
                String pagatoDaId = pPagante != null ? pPagante.getId() : (!partecipanti.isEmpty() ? partecipanti.get(0).getId() : "");

                String spesaId = UUID.randomUUID().toString();
                Spesa spesa = new Spesa(
                        spesaId,
                        schedaId,
                        descStr,
                        importo,
                        valutaStr.isEmpty() ? valutaGruppo : valutaStr,
                        timestamp,
                        catStr.isEmpty() ? "Generale" : catStr,
                        pagatoDaId,
                        null,
                        SyncStatus.PENDING_INSERT
                );

                List<SpesaPartecipante> quoteSpesa = new ArrayList<>();

                if (row.length >= 8 && !row[7].trim().isEmpty()) {
                    String quoteStr = pulisciCampo(row[7]);
                    String[] quotePairs = quoteStr.split("\\|");
                    for (String qPair : quotePairs) {
                        if (qPair.contains(":")) {
                            String[] qParts = qPair.split(":");
                            String nomeM = pulisciCampo(qParts[0]);
                            String valM = qParts.length > 1 ? pulisciCampo(qParts[1]).replace(",", ".") : "0";
                            double qImporto = 0.0;
                            try {
                                qImporto = Double.parseDouble(valM);
                            } catch (NumberFormatException ignored) {}

                            Partecipante pMembro = mappaMembri.get(nomeM.toLowerCase(Locale.ROOT));
                            if (pMembro != null && qImporto >= 0) {
                                quoteSpesa.add(new SpesaPartecipante(
                                        spesaId,
                                        pMembro.getId(),
                                        qImporto,
                                        SyncStatus.PENDING_INSERT
                                ));
                            }
                        }
                    }
                }

                if (quoteSpesa.isEmpty() && !partecipanti.isEmpty()) {
                    double quotaEqua = importo / partecipanti.size();
                    for (Partecipante p : partecipanti) {
                        quoteSpesa.add(new SpesaPartecipante(
                                spesaId,
                                p.getId(),
                                quotaEqua,
                                SyncStatus.PENDING_INSERT
                        ));
                    }
                }

                speseConQuote.add(new SpesaConQuote(spesa, quoteSpesa));
            }

            return new RisultatoImportazione(nomeGruppo, valutaGruppo, partecipanti, speseConQuote);

        } catch (Exception e) {
            Log.e(TAG, "Errore durante il parsing del file CSV", e);
            return null;
        }
    }

    private static String pulisciCampo(String raw) {
        if (raw == null) return "";
        String clean = raw.trim();
        if (clean.startsWith("\"") && clean.endsWith("\"") && clean.length() >= 2) {
            clean = clean.substring(1, clean.length() - 1);
        }
        return clean.trim();
    }

    private static String[] dividiRigaCsv(String riga) {
        List<String> tokens = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();

        char separatore = (riga.contains(";") || !riga.contains(",")) ? ';' : ',';

        for (int i = 0; i < riga.length(); i++) {
            char c = riga.charAt(i);
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == separatore && !inQuotes) {
                tokens.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString());
        return tokens.toArray(new String[0]);
    }
}
