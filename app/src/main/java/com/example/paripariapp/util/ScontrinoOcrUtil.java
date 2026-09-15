package com.example.paripariapp.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility per l'analisi OCR on-device di scontrini e ricevute (Google ML Kit)
 * e upload compresso su Firebase Storage.
 */
public class ScontrinoOcrUtil {

    private static final String TAG = "ScontrinoOcrUtil";

    public static class RisultatoOcr {
        public final Double importo;
        public final Long dataTimestamp;
        public final String esercenteSuggerito;
        public final String testoGreggio;

        public RisultatoOcr(@Nullable Double importo, @Nullable Long dataTimestamp, @Nullable String esercenteSuggerito, String testoGreggio) {
            this.importo = importo;
            this.dataTimestamp = dataTimestamp;
            this.esercenteSuggerito = esercenteSuggerito;
            this.testoGreggio = testoGreggio;
        }
    }

    public interface OcrCallback {
        void onSuccess(RisultatoOcr risultato);
        void onError(Exception e);
    }

    public interface UploadCallback {
        void onSuccess(String downloadUrl);
        void onError(Exception e);
    }

    /**
     * Esegue la scansione OCR on-device tramite Google ML Kit.
     */
    public static void analizzaScontrino(@NonNull Context context, @NonNull Uri imageUri, @NonNull OcrCallback callback) {
        try {
            InputImage image = InputImage.fromFilePath(context, imageUri);
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

            recognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        RisultatoOcr risultato = estraiDatiDaTesto(visionText);
                        callback.onSuccess(risultato);
                    })
                    .addOnFailureListener(callback::onError);
        } catch (Exception e) {
            Log.e(TAG, "Errore caricamento immagine per OCR", e);
            callback.onError(e);
        }
    }

    /**
     * Analizza il testo rilevato per estrarre importo totale, data e nome esercizio.
     */
    private static RisultatoOcr estraiDatiDaTesto(Text visionText) {
        if (visionText == null || visionText.getText().isEmpty()) {
            return new RisultatoOcr(null, null, null, "");
        }

        String testoCompleto = visionText.getText();
        List<String> righe = new ArrayList<>();
        for (Text.TextBlock block : visionText.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                righe.add(line.getText().trim());
            }
        }

        Double importoRilevato = null;
        Long dataRilevata = null;
        String esercente = null;

        // 1. Ricerca Esercente (prima riga non numerica e di lunghezza adeguata)
        for (String riga : righe) {
            if (riga.length() > 3 && !riga.matches(".*\\d{4,}.*") && !riga.toLowerCase().contains("scontrino") && !riga.toLowerCase().contains("ricevuta")) {
                esercente = riga;
                break;
            }
        }

        // 2. Ricerca Importo Totale
        // Pattern per righe tipo: "TOTALE 24,50" o "TOTAL EUR 12.00" o "EURO 15,90"
        Pattern patternTotale = Pattern.compile("(?i)(?:totale|total|importo|euro|eur|€)\\s*[:=]?\\s*([0-9]+[.,][0-9]{2})");
        for (String riga : righe) {
            Matcher matcher = patternTotale.matcher(riga);
            if (matcher.find()) {
                String valStr = matcher.group(1).replace(',', '.');
                try {
                    importoRilevato = Double.parseDouble(valStr);
                    break;
                } catch (NumberFormatException ignored) {}
            }
        }

        // Fallback importo: se non c'è la parola "TOTALE", cerca tutti gli importi e prendi il massimo plausibile
        if (importoRilevato == null) {
            Pattern patternGenerico = Pattern.compile("\\b([0-9]{1,4}[.,][0-9]{2})\\b");
            List<Double> numeriTrovati = new ArrayList<>();
            for (String riga : righe) {
                Matcher matcher = patternGenerico.matcher(riga);
                while (matcher.find()) {
                    try {
                        double val = Double.parseDouble(matcher.group(1).replace(',', '.'));
                        if (val > 0.50 && val < 5000.0) {
                            numeriTrovati.add(val);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
            if (!numeriTrovati.isEmpty()) {
                Collections.sort(numeriTrovati);
                importoRilevato = numeriTrovati.get(numeriTrovati.size() - 1);
            }
        }

        // 3. Ricerca Data (es. 12/05/2026, 12-05-26, 12.05.2026)
        Pattern patternData = Pattern.compile("\\b([0-3]?[0-9])[/-]([0-1]?[0-9])[/-](20[2-3][0-9]|[2-3][0-9])\\b");
        for (String riga : righe) {
            Matcher matcher = patternData.matcher(riga);
            if (matcher.find()) {
                String g = matcher.group(1);
                String m = matcher.group(2);
                String a = matcher.group(3);
                if (a.length() == 2) a = "20" + a;
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.ITALIAN);
                    Date d = sdf.parse(String.format(Locale.US, "%02d/%02d/%s", Integer.parseInt(g), Integer.parseInt(m), a));
                    if (d != null && Math.abs(System.currentTimeMillis() - d.getTime()) < 365L * 24 * 3600 * 1000) {
                        dataRilevata = d.getTime();
                        break;
                    }
                } catch (Exception ignored) {}
            }
        }

        return new RisultatoOcr(importoRilevato, dataRilevata, esercente, testoCompleto);
    }

    /**
     * Comprime l'immagine selezionata a JPEG (max 1280px) e la carica su Firebase Storage.
     */
    public static void comprimiECarica(@NonNull Context context, @NonNull Uri imageUri,
                                      @NonNull String schedaId, @NonNull String spesaId,
                                      @NonNull UploadCallback callback) {
        AppDatabaseWriteExecutor().execute(() -> {
            try {
                InputStream is = context.getContentResolver().openInputStream(imageUri);
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                if (is != null) is.close();

                if (bitmap == null) {
                    callback.onError(new IllegalArgumentException("Impossibile decodificare l'immagine"));
                    return;
                }

                // Ridimensionamento a max 1280px mantenendo le proporzioni
                int maxDim = 1280;
                int w = bitmap.getWidth();
                int h = bitmap.getHeight();
                if (w > maxDim || h > maxDim) {
                    float ratio = Math.min((float) maxDim / w, (float) maxDim / h);
                    bitmap = Bitmap.createScaledBitmap(bitmap, Math.round(w * ratio), Math.round(h * ratio), true);
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos);
                byte[] data = baos.toByteArray();

                StorageReference storageRef = FirebaseStorage.getInstance().getReference();
                StorageReference receiptRef = storageRef.child("scontrini/" + schedaId + "/" + spesaId + ".jpg");

                receiptRef.putBytes(data)
                        .continueWithTask(task -> {
                            if (!task.isSuccessful() && task.getException() != null) {
                                throw task.getException();
                            }
                            return receiptRef.getDownloadUrl();
                        })
                        .addOnSuccessListener(uri -> callback.onSuccess(uri.toString()))
                        .addOnFailureListener(callback::onError);

            } catch (Exception e) {
                Log.e(TAG, "Errore durante la compressione e l'upload dello scontrino", e);
                callback.onError(e);
            }
        });
    }

    private static java.util.concurrent.Executor AppDatabaseWriteExecutor() {
        return com.example.paripariapp.data.local.AppDatabase.databaseWriteExecutor;
    }
}
