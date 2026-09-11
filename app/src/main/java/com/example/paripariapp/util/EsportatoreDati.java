package com.example.paripariapp.util;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.Spesa;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    public static File generaCsv(Context context, String nomeScheda, String valutaScheda,
                                 List<Spesa> spese, List<Partecipante> partecipanti) throws IOException {
        File file = preparaFileExport(context, nomeScheda, "csv");
        Map<String, String> mappaNomi = creaMappaNomi(partecipanti);

        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            // UTF-8 BOM per Excel
            writer.print('\ufeff');
            writer.println(context.getString(R.string.csv_intestazione));

            if (spese != null) {
                for (Spesa s : spese) {
                    String data = FORMATO_DATA.format(new Date(s.getDataSpesa()));
                    String desc = s.getTitolo() != null ? s.getTitolo().replace(";", ",") : "";
                    String cat = s.getCategoria() != null ? s.getCategoria() : context.getString(R.string.cat_altro);
                    String importo = String.format(Locale.ITALY, "%.2f", s.getImporto());
                    String valuta = s.getValuta() != null ? s.getValuta() : (valutaScheda != null ? valutaScheda : context.getString(R.string.valuta_default));
                    String pagante = mappaNomi.getOrDefault(s.getPagatoDaId(), s.getPagatoDaId() != null ? s.getPagatoDaId() : "");

                    writer.println(String.format(Locale.ROOT,
                            "%s;%s;\"%s\";%s;%s;%s;\"%s\";\"%s\"",
                            data,
                            context.getString(R.string.csv_tipo_spesa),
                            desc,
                            cat,
                            importo,
                            valuta,
                            pagante,
                            context.getString(R.string.csv_tutti_quote)));
                }
            }
        }

        return file;
    }

    public static File generaPdf(Context context, String nomeScheda, String valutaScheda,
                                 List<Spesa> spese, List<Partecipante> partecipanti) throws IOException {
        File file = preparaFileExport(context, nomeScheda, "pdf");
        String valutaDefault = valutaScheda != null ? valutaScheda : context.getString(R.string.valuta_default);
        Map<String, String> mappaNomi = creaMappaNomi(partecipanti);
        String titolo = (nomeScheda != null && !nomeScheda.trim().isEmpty()) ? nomeScheda.trim() : context.getString(R.string.app_name);

        PdfDocument document = new PdfDocument();
        int pageNumber = 1;
        int pageWidth = 595;
        int pageHeight = 842;
        int margin = 36;

        Paint paint = new Paint();
        paint.setAntiAlias(true);

        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        int y = margin + 20;

        // Intestazione Brand
        paint.setColor(Color.rgb(43, 85, 237));
        paint.setTextSize(24);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(context.getString(R.string.app_name_brand), margin, y, paint);

        paint.setColor(Color.rgb(100, 110, 125));
        paint.setTextSize(12);
        paint.setTypeface(Typeface.DEFAULT);
        canvas.drawText(context.getString(R.string.pdf_sottotitolo), margin + 120, y - 2, paint);

        y += 25;
        paint.setColor(Color.rgb(20, 20, 25));
        paint.setTextSize(18);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(titolo, margin, y, paint);

        y += 16;
        paint.setColor(Color.rgb(120, 120, 130));
        paint.setTextSize(10);
        paint.setTypeface(Typeface.DEFAULT);
        canvas.drawText(context.getString(R.string.pdf_generato_il, FORMATO_DATA.format(new Date())), margin, y, paint);

        y += 20;
        // Linea separatrice
        paint.setColor(Color.rgb(220, 225, 235));
        paint.setStrokeWidth(1.5f);
        canvas.drawLine(margin, y, pageWidth - margin, y, paint);
        y += 20;

        // Tabella Spese
        paint.setColor(Color.rgb(30, 30, 40));
        paint.setTextSize(14);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(context.getString(R.string.pdf_sezione_spese, spese != null ? spese.size() : 0), margin, y, paint);
        y += 20;

        // Header Tabella
        paint.setColor(Color.rgb(240, 243, 250));
        canvas.drawRoundRect(margin, y - 14, pageWidth - margin, y + 8, 6, 6, paint);

        paint.setColor(Color.rgb(70, 80, 95));
        paint.setTextSize(10);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(context.getString(R.string.pdf_th_data), margin + 8, y, paint);
        canvas.drawText(context.getString(R.string.pdf_th_descrizione), margin + 80, y, paint);
        canvas.drawText(context.getString(R.string.pdf_th_categoria), margin + 260, y, paint);
        canvas.drawText(context.getString(R.string.pdf_th_pagante), margin + 360, y, paint);
        canvas.drawText(context.getString(R.string.pdf_th_importo), margin + 460, y, paint);
        y += 20;

        paint.setTypeface(Typeface.DEFAULT);
        if (spese != null) {
            for (Spesa sp : spese) {
                if (y > pageHeight - margin - 30) {
                    document.finishPage(page);
                    pageNumber++;
                    pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                    page = document.startPage(pageInfo);
                    canvas = page.getCanvas();
                    y = margin + 20;
                }

                paint.setColor(Color.rgb(50, 50, 60));
                paint.setTextSize(10);
                canvas.drawText(FORMATO_GIORNO.format(new Date(sp.getDataSpesa())), margin + 8, y, paint);

                String desc = sp.getTitolo() != null ? sp.getTitolo() : "";
                if (desc.length() > 28) desc = desc.substring(0, 26) + "...";
                canvas.drawText(desc, margin + 80, y, paint);

                String cat = sp.getCategoria() != null ? sp.getCategoria() : context.getString(R.string.cat_altro);
                canvas.drawText(cat, margin + 260, y, paint);

                String pagante = mappaNomi.getOrDefault(sp.getPagatoDaId(), "");
                if (pagante.length() > 14) pagante = pagante.substring(0, 12) + "...";
                canvas.drawText(pagante, margin + 360, y, paint);

                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                String importoFormattato = String.format(Locale.getDefault(), "%.2f %s", sp.getImporto(), sp.getValuta() != null ? sp.getValuta() : valutaDefault);
                canvas.drawText(importoFormattato, margin + 460, y, paint);
                paint.setTypeface(Typeface.DEFAULT);

                paint.setColor(Color.rgb(240, 240, 245));
                paint.setStrokeWidth(0.8f);
                canvas.drawLine(margin, y + 6, pageWidth - margin, y + 6, paint);
                y += 20;
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
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(intent, titoloChooser));
    }
}