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
 * (o strutturati in modo equivalente da altre app/fogli di calcolo), ricostruendo
 * integralmente la scheda, i membri e l'elenco delle spese con le relative quote di ripartizione.
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

    private static final String[] FORMATI_DATA = new String[] {
            "dd/MM/yyyy HH:mm:ss",
            "dd/MM/yyyy HH:mm",
            "dd/MM/yyyy",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd",
            "dd-MM-yyyy HH:mm",
            "dd-MM-yyyy",
            "MM/dd/yyyy HH:mm",
            "MM/dd/yyyy"
    };

    private static final ThreadLocal<SimpleDateFormat[]> FORMATI_DATA_PARSERS =
            ThreadLocal.withInitial(() -> {
                SimpleDateFormat[] arr = new SimpleDateFormat[FORMATI_DATA.length];
                for (int i = 0; i < FORMATI_DATA.length; i++) {
                    arr[i] = new SimpleDateFormat(FORMATI_DATA[i], Locale.ITALY);
                    arr[i].setLenient(true);
                }
                return arr;
            });

    @Nullable
    public static RisultatoImportazione analizzaCsv(@NonNull Context context, @NonNull Uri csvUri, @NonNull String schedaId) {
        try (InputStream is = context.getContentResolver().openInputStream(csvUri)) {
            if (is == null) return null;
            return analizzaCsv(is, schedaId);
        } catch (Exception e) {
            Log.e(TAG, "Errore apertura URI CSV", e);
            return null;
        }
    }

    @Nullable
    public static RisultatoImportazione analizzaCsv(@NonNull InputStream is, @NonNull String schedaId) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            String nomeGruppo = "Gruppo Importato";
            String valutaGruppo = "EUR";
            List<String> nomiMembriMetadati = new ArrayList<>();

            List<String[]> righeDati = new ArrayList<>();
            String line;
            boolean inDataSection = false;
            char delimiter = ';';
            boolean delimiterDetected = false;

            // Indici colonne (valori predefiniti corrispondenti all'export PariPari)
            int colData = 0;
            int colTipo = 1;
            int colDesc = 2;
            int colCat = 3;
            int colImporto = 4;
            int colValuta = 5;
            int colPagatoDa = 6;
            int colQuote = 7;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("\ufeff")) {
                    line = line.substring(1);
                }
                String lineTrim = line.trim();
                if (lineTrim.isEmpty()) continue;

                // Rilevamento automatico delimitatore (; o ,)
                if (!delimiterDetected) {
                    if (lineTrim.contains(";")) {
                        delimiter = ';';
                        delimiterDetected = true;
                    } else if (lineTrim.contains(",")) {
                        delimiter = ',';
                        delimiterDetected = true;
                    }
                }

                // 1. Parsing righe metadati (#)
                if (lineTrim.startsWith("#")) {
                    String lineClean = lineTrim.substring(1).trim();
                    char metaSep = lineClean.contains(";") ? ';' : (lineClean.contains(",") ? ',' : delimiter);
                    String[] parts = lineClean.split(String.valueOf(metaSep));
                    if (parts.length >= 2) {
                        String key = pulisciCampo(parts[0]).toUpperCase(Locale.ROOT);
                        if (key.equals("GRUPPO")) {
                            nomeGruppo = pulisciCampo(parts[1]);
                        } else if (key.equals("VALUTA")) {
                            valutaGruppo = pulisciCampo(parts[1]);
                        } else if (key.equals("MEMBRI")) {
                            for (int i = 1; i < parts.length; i++) {
                                String m = pulisciCampo(parts[i]);
                                if (!m.isEmpty() && !nomiMembriMetadati.contains(m)) {
                                    nomiMembriMetadati.add(m);
                                }
                            }
                        }
                    }
                    continue;
                }

                // 2. Riga Intestazione Tabelle
                String lowerLine = lineTrim.toLowerCase(Locale.ROOT);
                if (lowerLine.startsWith("data") || lowerLine.startsWith("date")
                        || lowerLine.contains("descrizione") || lowerLine.contains("description")
                        || lowerLine.contains("importo") || lowerLine.contains("amount")) {
                    inDataSection = true;
                    String[] headerTokens = dividiRigaCsv(lineTrim, delimiter);
                    for (int i = 0; i < headerTokens.length; i++) {
                        String h = pulisciCampo(headerTokens[i]).toLowerCase(Locale.ROOT);
                        if (h.contains("dat")) colData = i;
                        else if (h.contains("tip") || h.equals("type")) colTipo = i;
                        else if (h.contains("desc") || h.contains("titolo") || h.equals("title") || h.equals("name")) colDesc = i;
                        else if (h.contains("cat")) colCat = i;
                        else if (h.contains("import") || h.contains("ammont") || h.contains("amount") || h.contains("cost") || h.contains("costo") || h.contains("prezzo")) colImporto = i;
                        else if (h.contains("valut") || h.contains("curr")) colValuta = i;
                        else if (h.contains("pagat") || h.contains("paid") || h.contains("payer") || h.contains("chi")) colPagatoDa = i;
                        else if (h.contains("quot") || h.contains("shar") || h.contains("divis") || h.contains("ripartiz")) colQuote = i;
                    }
                    continue;
                }

                // 3. Righe Dati Spese
                if (inDataSection || lineTrim.contains(String.valueOf(delimiter))) {
                    String[] tokens = dividiRigaCsv(lineTrim, delimiter);
                    if (tokens.length >= 2) {
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
                if (colPagatoDa >= 0 && colPagatoDa < row.length) {
                    String paganteNome = pulisciCampo(row[colPagatoDa]);
                    if (!paganteNome.isEmpty() && !mappaMembri.containsKey(paganteNome.toLowerCase(Locale.ROOT))) {
                        String pId = UUID.randomUUID().toString();
                        mappaMembri.put(paganteNome.toLowerCase(Locale.ROOT),
                                new Partecipante(pId, schedaId, paganteNome, null, SyncStatus.PENDING_INSERT));
                    }
                }
                if (colQuote >= 0 && colQuote < row.length) {
                    String quoteStr = pulisciCampo(row[colQuote]);
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
                String dataStr = (colData >= 0 && colData < row.length) ? pulisciCampo(row[colData]) : "";
                String tipoStr = (colTipo >= 0 && colTipo < row.length) ? pulisciCampo(row[colTipo]) : "Spesa";
                String descStr = (colDesc >= 0 && colDesc < row.length) ? pulisciCampo(row[colDesc]) : "Spesa Importata";
                String catStr = (colCat >= 0 && colCat < row.length) ? pulisciCampo(row[colCat]) : "Generale";
                String importoStr = (colImporto >= 0 && colImporto < row.length) ? pulisciCampo(row[colImporto]).replace(",", ".") : "0";
                String valutaStr = (colValuta >= 0 && colValuta < row.length) ? pulisciCampo(row[colValuta]) : valutaGruppo;
                String paganteStr = (colPagatoDa >= 0 && colPagatoDa < row.length) ? pulisciCampo(row[colPagatoDa]) : "";

                if (descStr.isEmpty()) descStr = "Spesa";

                double importo = 0.0;
                try {
                    importo = Double.parseDouble(importoStr);
                } catch (NumberFormatException ignored) {}

                if (importo <= 0) continue;

                long timestamp = parseTimestamp(dataStr, System.currentTimeMillis());

                Partecipante pPagante = mappaMembri.get(paganteStr.toLowerCase(Locale.ROOT));
                String pagatoDaId = pPagante != null ? pPagante.getId() : (!partecipanti.isEmpty() ? partecipanti.get(0).getId() : "");

                boolean isRimborso = tipoStr.equalsIgnoreCase("Rimborso") || CategoriaUtil.isCategoriaSaldi(catStr) || descStr.toLowerCase(Locale.ROOT).contains("rimborso");
                String categoriaFinale = isRimborso ? CategoriaUtil.CAT_RIMBORSI : (catStr.isEmpty() ? "Generale" : catStr);

                String spesaId = UUID.randomUUID().toString();
                Spesa spesa = new Spesa(
                        spesaId,
                        schedaId,
                        descStr,
                        importo,
                        valutaStr.isEmpty() ? valutaGruppo : valutaStr,
                        timestamp,
                        categoriaFinale,
                        pagatoDaId,
                        null,
                        SyncStatus.PENDING_INSERT
                );

                List<SpesaPartecipante> quoteSpesa = new ArrayList<>();

                if (colQuote >= 0 && colQuote < row.length && !row[colQuote].trim().isEmpty()) {
                    String quoteStr = pulisciCampo(row[colQuote]);
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

                // Se non specificate le quote, suddividi equamente tra tutti i partecipanti
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

    private static long parseTimestamp(String dataStr, long fallback) {
        if (dataStr == null || dataStr.trim().isEmpty()) return fallback;
        String clean = dataStr.trim();
        SimpleDateFormat[] parsers = FORMATI_DATA_PARSERS.get();
        for (SimpleDateFormat sdf : parsers) {
            try {
                Date d = sdf.parse(clean);
                if (d != null) return d.getTime();
            } catch (Exception ignored) {}
        }
        return fallback;
    }

    public static String pulisciCampo(String raw) {
        if (raw == null) return "";
        String clean = raw.trim();
        if (clean.startsWith("\"") && clean.endsWith("\"") && clean.length() >= 2) {
            clean = clean.substring(1, clean.length() - 1);
        }
        return clean.replace("\"\"", "\"").trim();
    }

    public static String[] dividiRigaCsv(String riga, char delimiter) {
        List<String> tokens = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < riga.length(); i++) {
            char c = riga.charAt(i);
            if (c == '\"') {
                if (inQuotes && i + 1 < riga.length() && riga.charAt(i + 1) == '\"') {
                    sb.append('\"');
                    i++; // salta la seconda virgoletta di escape RFC 4180
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == delimiter && !inQuotes) {
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
