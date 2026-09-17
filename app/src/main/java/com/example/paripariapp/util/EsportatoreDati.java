package com.example.paripariapp.util;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.data.model.SpesaPartecipante;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Utility per l'esportazione dei dati di un gruppo in formato CSV strutturato
 * (pronto per futura importazione e ricreazione del gruppo) e PDF formattato.
 */
public final class EsportatoreDati {

    private static final SimpleDateFormat FORMATO_DATA =
            new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY);
    private static final SimpleDateFormat FORMATO_GIORNO =
            new SimpleDateFormat("dd/MM/yyyy", Locale.ITALY);

    private EsportatoreDati() {
    }

    private static File preparaFileExport(Context context, String nomeScheda, String estensione) {
        File cartella = new File(context.getCacheDir(), "export");
        if (!cartella.exists()) {
            cartella.mkdirs();
        }
        String titolo = (nomeScheda != null && !nomeScheda.isEmpty()) ? nomeScheda : context.getString(R.string.nome_gruppo_default);
        String nomeSicuro = titolo.replaceAll("[^a-zA-Z0-9_-]", "_");
        return new File(cartella, "PariPari_" + nomeSicuro + "." + estensione);
    }

    private static Map<String, String> creaMappaNomi(List<Partecipante> partecipanti) {
        Map<String, String> mappaNomi = new HashMap<>();
        if (partecipanti != null) {
            for (Partecipante p : partecipanti) {
                mappaNomi.put(p.getId(), p.getNome());
            }
        }
        return mappaNomi;
    }

    private static String sanitizzaCsv(String input) {
        if (input == null) return "";
        return input.replace(";", ",").replace("\"", "'").replace("\n", " ").trim();
    }

    private static String sanitizzaQuote(String input) {
        if (input == null) return "";
        return input.replace(":", "_").replace("|", "-").replace(";", ",").replace("\"", "'").trim();
    }

    /**
     * Sovraccarico legacy senza quote.
     */
    public static File generaCsv(Context context, String nomeScheda, String valutaScheda,
                                 List<Spesa> spese, List<Partecipante> partecipanti) throws IOException {
        return generaCsv(context, nomeScheda, valutaScheda, spese, partecipanti, null);
    }

    /**
     * Genera un file CSV fortemente strutturato con intestazione metadati,
     * elenco membri e dettaglio ripartizione quote per spesa (pronto per futura importazione).
     */
    public static File generaCsv(Context context, String nomeScheda, String valutaScheda,
                                 List<Spesa> spese, List<Partecipante> partecipanti,
                                 @Nullable List<SpesaPartecipante> quote) throws IOException {
        File file = preparaFileExport(context, nomeScheda, "csv");
        Map<String, String> mappaNomi = creaMappaNomi(partecipanti);

        String valutaDef = (valutaScheda != null && !valutaScheda.trim().isEmpty()) ? valutaScheda.trim() : "EUR";
        String titoloGruppo = (nomeScheda != null && !nomeScheda.trim().isEmpty()) ? nomeScheda.trim() : "Gruppo";

        // Mappa delle quote raggruppate per spesaId
        Map<String, List<SpesaPartecipante>> quoteMap = new HashMap<>();
        if (quote != null) {
            for (SpesaPartecipante q : quote) {
                List<SpesaPartecipante> list = quoteMap.computeIfAbsent(q.getSpesaId(), k -> new ArrayList<>());
                list.add(q);
            }
        }

        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            // UTF-8 BOM per la corretta apertura in Microsoft Excel / Calc
            writer.print('\ufeff');

            // --- INTESTAZIONE METADATI PER IMPORTAZIONE FUTURA ---
            writer.println("# PARIPARI_EXPORT_VERSION;1.0");
            writer.println("# GRUPPO;" + sanitizzaCsv(titoloGruppo));
            writer.println("# VALUTA;" + sanitizzaCsv(valutaDef));

            // Lista Membri per importazione
            StringBuilder sbMembri = new StringBuilder("# MEMBRI");
            if (partecipanti != null) {
                for (Partecipante p : partecipanti) {
                    sbMembri.append(";").append(sanitizzaCsv(p.getNome()));
                }
            }
            writer.println(sbMembri.toString());
            writer.println("#");

            // Intestazione colonne CSV
            writer.println("Data;Tipo;Descrizione;Categoria;Importo;Valuta;PagatoDa;RipartizioneQuote");

            if (spese != null) {
                for (Spesa s : spese) {
                    String data = FORMATO_DATA.format(new Date(s.getDataSpesa()));
                    boolean isRimborso = CategoriaUtil.isCategoriaSaldi(s.getCategoria());
                    String tipo = isRimborso ? "Rimborso" : "Spesa";
                    String desc = s.getTitolo() != null ? sanitizzaCsv(s.getTitolo()) : "";
                    String cat = s.getCategoria() != null ? s.getCategoria() : "Generale";
                    String importo = String.format(Locale.US, "%.2f", s.getImporto());
                    String valuta = s.getValuta() != null ? s.getValuta() : valutaDef;
                    String pagante = mappaNomi.getOrDefault(s.getPagatoDaId(), s.getPagatoDaId() != null ? s.getPagatoDaId() : "");

                    // Genera stringa strutturata della ripartizione quote (es. "Marco:20.00|Giulia:20.00|Luca:20.00")
                    StringBuilder sbQuote = new StringBuilder();
                    List<SpesaPartecipante> quoteSpesa = quoteMap.get(s.getId());
                    if (quoteSpesa != null && !quoteSpesa.isEmpty()) {
                        for (int i = 0; i < quoteSpesa.size(); i++) {
                            SpesaPartecipante q = quoteSpesa.get(i);
                            String nomeMembro = mappaNomi.getOrDefault(q.getPartecipanteId(), q.getPartecipanteId());
                            if (i > 0) sbQuote.append("|");
                            sbQuote.append(sanitizzaQuote(nomeMembro)).append(":").append(String.format(Locale.US, "%.2f", q.getQuota()));
                        }
                    }

                    writer.println(String.format(Locale.ROOT,
                            "%s;%s;\"%s\";\"%s\";%s;%s;\"%s\";\"%s\"",
                            data,
                            tipo,
                            desc,
                            cat,
                            importo,
                            valuta,
                            pagante,
                            sbQuote.toString()));
                }
            }
        }

        return file;
    }

    /**
     * Sovraccarico legacy senza quote.
     */
    public static File generaPdf(Context context, String nomeScheda, String valutaScheda,
                                 List<Spesa> spese, List<Partecipante> partecipanti) throws IOException {
        return generaPdf(context, nomeScheda, valutaScheda, spese, partecipanti, null);
    }

    /**
     * Genera un report PDF moderno ed elegante con card riepilogative KPI,
     * posizione dei bilanci dei membri e tabella dettagliata delle spese.
     */
    public static File generaPdf(Context context, String nomeScheda, String valutaScheda,
                                 List<Spesa> spese, List<Partecipante> partecipanti,
                                 @Nullable List<SpesaPartecipante> quote) throws IOException {
        File file = preparaFileExport(context, nomeScheda, "pdf");
        String valutaDefault = (valutaScheda != null && !valutaScheda.trim().isEmpty()) ? valutaScheda.trim() : "EUR";
        Map<String, String> mappaNomi = creaMappaNomi(partecipanti);
        String titolo = (nomeScheda != null && !nomeScheda.trim().isEmpty()) ? nomeScheda.trim() : context.getString(R.string.app_name);

        PdfDocument document = new PdfDocument();
        int pageNumber = 1;
        int pageWidth = 595; // Formato A4
        int pageHeight = 842;
        int margin = 36;
        int contentWidth = pageWidth - (margin * 2);

        Paint paint = new Paint();
        paint.setAntiAlias(true);

        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        int y = margin + 20;

        // --- 1. BRAND HEADER ---
        paint.setColor(Color.rgb(43, 85, 237)); // Royal Blue
        paint.setTextSize(22);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText("PariPari", margin, y, paint);

        paint.setColor(Color.rgb(100, 116, 139));
        paint.setTextSize(10);
        paint.setTypeface(Typeface.DEFAULT);
        canvas.drawText("Report Finanziario di Gruppo", margin + 105, y - 2, paint);

        String dataGenerazione = FORMATO_DATA.format(new Date());
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(dataGenerazione, pageWidth - margin, y - 2, paint);
        paint.setTextAlign(Paint.Align.LEFT);

        y += 18;
        paint.setColor(Color.rgb(226, 232, 240));
        paint.setStrokeWidth(1.2f);
        canvas.drawLine(margin, y, pageWidth - margin, y, paint);
        y += 24;

        // --- 2. TITOLO GRUPPO ---
        paint.setColor(Color.rgb(15, 23, 42));
        paint.setTextSize(18);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(titolo, margin, y, paint);
        y += 20;

        // --- 3. CARDS RIEPILOGATIVE KPI ---
        double totaleSpeso = 0.0;
        int conteggioSpese = 0;
        if (spese != null) {
            for (Spesa s : spese) {
                if (!CategoriaUtil.isCategoriaSaldi(s.getCategoria())) {
                    totaleSpeso += CalcolatoreSaldi.convertiImportoSpesa(s.getImporto(), s, valutaDefault, context);
                    conteggioSpese++;
                }
            }
        }

        int cardBoxWidth = (contentWidth - 16) / 3;
        int cardHeight = 44;

        paint.setColor(Color.rgb(241, 245, 249)); // Card Background
        // Card 1: Totale Speso
        canvas.drawRoundRect(new RectF(margin, y, margin + cardBoxWidth, y + cardHeight), 8, 8, paint);
        // Card 2: Transazioni
        canvas.drawRoundRect(new RectF(margin + cardBoxWidth + 8, y, margin + (cardBoxWidth * 2) + 8, y + cardHeight), 8, 8, paint);
        // Card 3: Membri
        canvas.drawRoundRect(new RectF(margin + (cardBoxWidth * 2) + 16, y, pageWidth - margin, y + cardHeight), 8, 8, paint);

        // Testi Card 1
        paint.setColor(Color.rgb(100, 116, 139));
        paint.setTextSize(8);
        paint.setTypeface(Typeface.DEFAULT);
        canvas.drawText("TOTALE SPESO", margin + 10, y + 14, paint);
        paint.setColor(Color.rgb(43, 85, 237));
        paint.setTextSize(12);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(String.format(Locale.getDefault(), "%.2f %s", totaleSpeso, valutaDefault), margin + 10, y + 32, paint);

        // Testi Card 2
        paint.setColor(Color.rgb(100, 116, 139));
        paint.setTextSize(8);
        paint.setTypeface(Typeface.DEFAULT);
        canvas.drawText("SPESE REGISTRATE", margin + cardBoxWidth + 18, y + 14, paint);
        paint.setColor(Color.rgb(15, 23, 42));
        paint.setTextSize(12);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(String.valueOf(conteggioSpese), margin + cardBoxWidth + 18, y + 32, paint);

        // Testi Card 3
        paint.setColor(Color.rgb(100, 116, 139));
        paint.setTextSize(8);
        paint.setTypeface(Typeface.DEFAULT);
        canvas.drawText("MEMBRI GRUPPO", margin + (cardBoxWidth * 2) + 26, y + 14, paint);
        paint.setColor(Color.rgb(15, 23, 42));
        paint.setTextSize(12);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(String.valueOf(partecipanti != null ? partecipanti.size() : 0), margin + (cardBoxWidth * 2) + 26, y + 32, paint);

        y += cardHeight + 24;

        // --- 4. BILANCIO MEMBRI ---
        if (partecipanti != null && !partecipanti.isEmpty()) {
            paint.setColor(Color.rgb(15, 23, 42));
            paint.setTextSize(11);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText("Posizione Finanziaria Membri", margin, y, paint);
            y += 14;

            List<CalcolatoreSaldi.BilancioMembro> bilanci = CalcolatoreSaldi.calcolaListaBilanciMembri(
                    partecipanti, spese, quote, valutaDefault, context
            );

            paint.setTextSize(9);
            paint.setTypeface(Typeface.DEFAULT);
            for (CalcolatoreSaldi.BilancioMembro b : bilanci) {
                if (y > pageHeight - margin - 50) break;
                String nomeMembro = b.getNomePartecipante();
                double saldo = b.getSaldoNetto();

                paint.setColor(Color.rgb(51, 65, 85));
                canvas.drawText("• " + nomeMembro + ":", margin + 8, y, paint);

                if (saldo > 0.001) {
                    paint.setColor(Color.rgb(22, 163, 74)); // Verde Credito
                    paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                    canvas.drawText(String.format(Locale.getDefault(), "+%.2f %s", saldo, valutaDefault), margin + 140, y, paint);
                } else if (saldo < -0.001) {
                    paint.setColor(Color.rgb(220, 38, 38)); // Rosso Debito
                    paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                    canvas.drawText(String.format(Locale.getDefault(), "%.2f %s", saldo, valutaDefault), margin + 140, y, paint);
                } else {
                    paint.setColor(Color.rgb(100, 116, 139));
                    paint.setTypeface(Typeface.DEFAULT);
                    canvas.drawText(String.format(Locale.getDefault(), "0.00 %s (In pareggio)", valutaDefault), margin + 140, y, paint);
                }
                paint.setTypeface(Typeface.DEFAULT);
                y += 14;
            }
            y += 12;
        }

        // --- 5. TABELLA SPESE ---
        paint.setColor(Color.rgb(15, 23, 42));
        paint.setTextSize(11);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText("Elenco Dettagliato Spese (" + (spese != null ? spese.size() : 0) + ")", margin, y, paint);
        y += 16;

        // Header Tabella
        paint.setColor(Color.rgb(43, 85, 237));
        canvas.drawRoundRect(new RectF(margin, y - 12, pageWidth - margin, y + 10), 6, 6, paint);

        paint.setColor(Color.WHITE);
        paint.setTextSize(9);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText("Data", margin + 8, y, paint);
        canvas.drawText("Descrizione", margin + 75, y, paint);
        canvas.drawText("Categoria", margin + 250, y, paint);
        canvas.drawText("Pagato Da", margin + 350, y, paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("Importo", pageWidth - margin - 10, y, paint);
        paint.setTextAlign(Paint.Align.LEFT);
        y += 18;

        int rowHeight = 20;
        int rowIndex = 0;

        if (spese != null) {
            for (Spesa sp : spese) {
                if (y > pageHeight - margin - 30) {
                    document.finishPage(page);
                    pageNumber++;
                    pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                    page = document.startPage(pageInfo);
                    canvas = page.getCanvas();
                    y = margin + 20;

                    // Re-draw Header Tabella su nuova pagina
                    paint.setColor(Color.rgb(43, 85, 237));
                    canvas.drawRoundRect(new RectF(margin, y - 12, pageWidth - margin, y + 10), 6, 6, paint);
                    paint.setColor(Color.WHITE);
                    paint.setTextSize(9);
                    paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                    canvas.drawText("Data", margin + 8, y, paint);
                    canvas.drawText("Descrizione", margin + 75, y, paint);
                    canvas.drawText("Categoria", margin + 250, y, paint);
                    canvas.drawText("Pagato Da", margin + 350, y, paint);
                    paint.setTextAlign(Paint.Align.RIGHT);
                    canvas.drawText("Importo", pageWidth - margin - 10, y, paint);
                    paint.setTextAlign(Paint.Align.LEFT);
                    y += 18;
                }

                // Sfondo righe alternate
                if (rowIndex % 2 == 1) {
                    paint.setColor(Color.rgb(248, 250, 252));
                    canvas.drawRect(margin, y - 12, pageWidth - margin, y + 8, paint);
                }

                paint.setColor(Color.rgb(51, 65, 85));
                paint.setTextSize(9);
                paint.setTypeface(Typeface.DEFAULT);
                canvas.drawText(FORMATO_GIORNO.format(new Date(sp.getDataSpesa())), margin + 8, y, paint);

                String desc = sp.getTitolo() != null ? sp.getTitolo() : "";
                if (desc.length() > 28) desc = desc.substring(0, 26) + "...";
                canvas.drawText(desc, margin + 75, y, paint);

                String cat = sp.getCategoria() != null ? sp.getCategoria() : "Generale";
                canvas.drawText(cat, margin + 250, y, paint);

                String pagante = mappaNomi.getOrDefault(sp.getPagatoDaId(), "");
                if (pagante.length() > 14) pagante = pagante.substring(0, 12) + "...";
                canvas.drawText(pagante, margin + 350, y, paint);

                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                String importoFmt = String.format(Locale.getDefault(), "%.2f %s", sp.getImporto(), sp.getValuta() != null ? sp.getValuta() : valutaDefault);
                paint.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(importoFmt, pageWidth - margin - 10, y, paint);
                paint.setTextAlign(Paint.Align.LEFT);

                y += rowHeight;
                rowIndex++;
            }
        }

        document.finishPage(page);

        try (FileOutputStream fos = new FileOutputStream(file)) {
            document.writeTo(fos);
        } finally {
            document.close();
        }

        return file;
    }

    public static void condividiFile(Context context, File file, String mimeType, String titoloChooser) {
        if (context == null || file == null) return;
        try {
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(mimeType);
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(intent, titoloChooser);
            if (!(context instanceof android.app.Activity)) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(chooser);
        } catch (Exception e) {
            android.util.Log.e("EsportatoreDati", "Errore condivisione file", e);
        }
    }
}
