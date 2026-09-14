package com.example.paripariapp.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.paripariapp.R;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.EnumMap;
import java.util.Map;

/**
 * Utilità per la generazione di codici QR ottimizzati per Android.
 * Incorpora al centro il logo ufficiale di PariPari con badge protettivo
 * e livello di correzione errore elevato (Level H ~30%).
 */
public final class QrCodeUtil {

    private static final String TAG = "QrCodeUtil";

    private QrCodeUtil() {
        // Utility class
    }

    /**
     * Genera un {@link Bitmap} contenente un codice QR per il testo specificato (es. link HTTPS),
     * con al centro il logo dell'app PariPari protetto da un badge arrotondato bianco.
     *
     * @param context    Contesto per il recupero delle risorse grafiche
     * @param contenuto  Testo o URL da codificare
     * @param dimensione Larghezza e altezza del bitmap risultante in pixel
     * @return Bitmap con il QR code e il logo al centro, oppure null in caso di errore
     */
    @Nullable
    public static Bitmap generaQrCodeConLogo(@NonNull Context context, @Nullable String contenuto, int dimensione) {
        if (contenuto == null || contenuto.trim().isEmpty() || dimensione <= 0) {
            return null;
        }

        try {
            BitMatrix bitMatrix = generaMatriceQr(contenuto, dimensione);

            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            int[] pixels = new int[width * height];

            for (int y = 0; y < height; y++) {
                int offset = y * width;
                for (int x = 0; x < width; x++) {
                    pixels[offset + x] = bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }

            // Crea direttamente il bitmap finale senza allocazioni o copie intermedie
            Bitmap outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            outputBitmap.setPixels(pixels, 0, width, 0, 0, width, height);

            // Carica il logo PariPari dalle risorse
            Bitmap rawLogo = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_launcher_foreground);
            if (rawLogo == null) {
                return outputBitmap;
            }

            // Ritaglia i margini trasparenti per ottenere il simbolo effettivo
            Bitmap croppedLogo = ritagliaLogoTrasparente(rawLogo);

            Canvas canvas = new Canvas(outputBitmap);

            // Dimensioni badge centrale (~23% del QR code)
            int badgeDim = (int) (dimensione * 0.23f);
            int badgeCornerRadius = (int) (badgeDim * 0.24f);

            int centerX = dimensione / 2;
            int centerY = dimensione / 2;

            RectF badgeRect = new RectF(
                    centerX - badgeDim / 2f,
                    centerY - badgeDim / 2f,
                    centerX + badgeDim / 2f,
                    centerY + badgeDim / 2f
            );

            // Sfondo bianco pieno per isolare il logo dai pixel del QR
            Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            badgePaint.setColor(Color.WHITE);
            badgePaint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(badgeRect, badgeCornerRadius, badgeCornerRadius, badgePaint);

            // Bordo sottile di separazione
            Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            strokePaint.setColor(Color.parseColor("#E2E8F0"));
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(Math.max(2f, dimensione * 0.005f));
            canvas.drawRoundRect(badgeRect, badgeCornerRadius, badgeCornerRadius, strokePaint);

            // Disegna il logo al centro del badge (occupa l'82% del badge)
            int logoDim = (int) (badgeDim * 0.82f);
            RectF logoRect = new RectF(
                    centerX - logoDim / 2f,
                    centerY - logoDim / 2f,
                    centerX + logoDim / 2f,
                    centerY + logoDim / 2f
            );

            Paint logoPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(croppedLogo != null ? croppedLogo : rawLogo, null, logoRect, logoPaint);

            // Rilascia tempestivamente la memoria nativa dei bitmap temporanei
            if (croppedLogo != null && croppedLogo != rawLogo) {
                croppedLogo.recycle();
            }
            rawLogo.recycle();

            return outputBitmap;

        } catch (Exception e) {
            Log.e(TAG, "Errore nella generazione del QR code con logo", e);
            return null;
        }
    }

    /**
     * Rileva e ritaglia la regione non trasparente del bitmap per centrare perfettamente il logo.
     */
    @Nullable
    public static Bitmap ritagliaLogoTrasparente(@Nullable Bitmap source) {
        if (source == null) return null;
        int width = source.getWidth();
        int height = source.getHeight();

        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;

        int[] pixels = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int y = 0; y < height; y++) {
            int rowOffset = y * width;
            for (int x = 0; x < width; x++) {
                int alpha = (pixels[rowOffset + x] >>> 24);
                if (alpha > 25) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }

        if (maxX <= minX || maxY <= minY) {
            return source;
        }

        int pad = Math.max(2, (maxX - minX) / 25);
        minX = Math.max(0, minX - pad);
        minY = Math.max(0, minY - pad);
        maxX = Math.min(width - 1, maxX + pad);
        maxY = Math.min(height - 1, maxY + pad);

        int cropW = maxX - minX + 1;
        int cropH = maxY - minY + 1;
        return Bitmap.createBitmap(source, minX, minY, cropW, cropH);
    }

    /**
     * Genera la matrice booleana del codice QR con codifica UTF-8, livello di errore H e margine 1.
     */
    @NonNull
    public static BitMatrix generaMatriceQr(@NonNull String contenuto, int dimensione) throws Exception {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.MARGIN, 1);
        return new QRCodeWriter().encode(contenuto, BarcodeFormat.QR_CODE, dimensione, dimensione, hints);
    }
}
