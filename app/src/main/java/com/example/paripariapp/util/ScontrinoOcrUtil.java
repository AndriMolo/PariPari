package com.example.paripariapp.util;

import android.content.Context;
import android.graphics.Rect;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.paripariapp.data.model.ScontrinoDigitale;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility per l'analisi OCR on-device di scontrini e ricevute (Google ML Kit).
 * Estrae esercente, data, voci singole con quantità e prezzi, e genera il JSON
 * per Firestore/Room a costo storage 0 e banda 0.
 */
public class ScontrinoOcrUtil {

    private static final String TAG = "ScontrinoOcrUtil";

    // Pattern pre-compilati una sola volta a livello statico per ottimizzare la CPU ed evitare allocazioni di memoria (RAM)
    private static final Pattern PATTERN_PREZZO_FINALE =
            Pattern.compile("(?i)(?:^|\\s)([0-9]{1,4}[.,][0-9]{2})(?:\\s*([a-d]|euro|eur|€))?$");
    private static final Pattern PATTERN_TOTALE =
            Pattern.compile("(?i)(?:totale|total|importo|dovuto|complessivo|euro|eur|€)\\s*[:=]?\\s*([0-9]{1,4}[.,][0-9]{2})");
    private static final Pattern PATTERN_SUBTOTALE =
            Pattern.compile("(?i)(?:subtotale|subtotal|sub-totale|parziale)\\s*[:=]?\\s*([0-9]{1,4}[.,][0-9]{2})");
    private static final Pattern PATTERN_DATA =
            Pattern.compile("\\b([0-3]?[0-9])[/-]([0-1]?[0-9])[/-](20[2-3][0-9]|[2-3][0-9])\\b");
    private static final Pattern PATTERN_PIVA =
            Pattern.compile("(?i)(?:p\\.?\\s*iva|partita\\s*iva|cf|c\\.f\\.)\\s*[:=]?\\s*([0-9a-zA-Z]{11,16})");
    private static final Pattern PATTERN_QTA =
            Pattern.compile("(?i)\\b([1-9][0-9]?)\\s*(?:pz|x|\\*)\\s*([0-9]{1,3}[.,][0-9]{2})?\\b");
    private static final Pattern PATTERN_GENERICO =
            Pattern.compile("\\b([0-9]{1,4}[.,][0-9]{2})\\b");
    private static final Pattern PATTERN_DIGITS_4 =
            Pattern.compile(".*\\d{4,}.*");
    private static final Pattern PATTERN_LEADING_DIGITS =
            Pattern.compile("^[0-9]{1,4}\\s+");
    private static final Pattern PATTERN_NON_ALPHA_ESERCENTE =
            Pattern.compile("(?i)[^a-zA-Z0-9àèéìòù&'\\s.-]");
    private static final Pattern PATTERN_ONLY_SYMBOLS =
            Pattern.compile("^[0-9.,\\-\\s]+$");

    public static class RisultatoOcr {
        public final Double importo;
        public final Long dataTimestamp;
        public final String esercenteSuggerito;
        public final String testoGreggio;
        @Nullable
        public final ScontrinoDigitale scontrinoDigitale;

        public RisultatoOcr(@Nullable Double importo, @Nullable Long dataTimestamp,
                            @Nullable String esercenteSuggerito, String testoGreggio) {
            this(importo, dataTimestamp, esercenteSuggerito, testoGreggio, null);
        }

        public RisultatoOcr(@Nullable Double importo, @Nullable Long dataTimestamp,
                            @Nullable String esercenteSuggerito, String testoGreggio,
                            @Nullable ScontrinoDigitale scontrinoDigitale) {
            this.importo = importo;
            this.dataTimestamp = dataTimestamp;
            this.esercenteSuggerito = esercenteSuggerito;
            this.testoGreggio = testoGreggio;
            this.scontrinoDigitale = scontrinoDigitale;
        }

        @NonNull
        public String getJsonStrutturato() {
            if (scontrinoDigitale != null) {
                return scontrinoDigitale.toJson();
            }
            return "{}";
        }
    }

    public interface OcrCallback {
        void onSuccess(RisultatoOcr risultato);
        void onError(Exception e);
    }

    /**
     * Esegue la scansione OCR on-device tramite Google ML Kit.
     * Rilascia esplicitamente le risorse native del TextRecognizer al completamento per azzerare leak di RAM.
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
                    .addOnFailureListener(callback::onError)
                    .addOnCompleteListener(task -> {
                        try {
                            recognizer.close();
                        } catch (Exception ignored) {}
                    });
        } catch (Exception e) {
            Log.e(TAG, "Errore caricamento immagine per OCR", e);
            callback.onError(e);
        }
    }

    /**
     * Analizza il testo rilevato con ordinamento geometrico per raggruppare descrizioni e prezzi,
     * estraendo esercente, data, totali e le singole voci dello scontrino.
     */
    public static RisultatoOcr estraiDatiDaTesto(Text visionText) {
        if (visionText == null || visionText.getText().isEmpty()) {
            return new RisultatoOcr(null, null, null, "", new ScontrinoDigitale());
        }

        String testoCompleto = visionText.getText();
        List<RigaScontrinoGeometrica> righeOrdinate = ricostruisciRigheGeometriche(visionText);

        Double importoTotale = null;
        Double subtotale = null;
        Long dataRilevata = null;
        String dataStr = null;
        String esercente = null;
        String metodoPagamento = null;
        String piva = null;
        List<ScontrinoDigitale.VoceScontrino> voci = new ArrayList<>();

        int indiceRiga = 0;
        for (RigaScontrinoGeometrica rigaGeo : righeOrdinate) {
            String riga = rigaGeo.testo.trim();
            indiceRiga++;

            if (riga.isEmpty()) continue;

            // 1. Esercente (primi 3 blocchi/righe non numerici e privi di keyword scontrino)
            if (esercente == null && indiceRiga <= 4) {
                if (riga.length() >= 3
                        && !PATTERN_DIGITS_4.matcher(riga).matches()
                        && !riga.toLowerCase(Locale.ROOT).contains("scontrino")
                        && !riga.toLowerCase(Locale.ROOT).contains("documento")
                        && !riga.toLowerCase(Locale.ROOT).contains("fiscale")
                        && !riga.toLowerCase(Locale.ROOT).contains("benvenuti")
                        && !riga.toLowerCase(Locale.ROOT).contains("ricevuta")) {
                    esercente = pulisciNomeEsercente(riga);
                }
            }

            // 2. Partita IVA / CF
            if (piva == null) {
                Matcher mPiva = PATTERN_PIVA.matcher(riga);
                if (mPiva.find()) {
                    piva = mPiva.group(1);
                }
            }

            // 3. Data
            if (dataRilevata == null) {
                Matcher mData = PATTERN_DATA.matcher(riga);
                if (mData.find()) {
                    String g = mData.group(1);
                    String m = mData.group(2);
                    String a = mData.group(3);
                    if (a.length() == 2) a = "20" + a;
                    try {
                        int day = Integer.parseInt(g);
                        int month = Integer.parseInt(m);
                        dataStr = String.format(Locale.US, "%02d/%02d/%s", day, month, a);
                        java.util.Calendar cal = java.util.Calendar.getInstance();
                        cal.setLenient(false);
                        cal.set(Integer.parseInt(a), month - 1, day, 12, 0, 0);
                        long time = cal.getTimeInMillis();
                        if (Math.abs(System.currentTimeMillis() - time) < 365L * 24 * 3600 * 1000) {
                            dataRilevata = time;
                        }
                    } catch (Exception ignored) {}
                }
            }

            // 4. Totale e Subtotale
            Matcher mTot = PATTERN_TOTALE.matcher(riga);
            if (mTot.find() && importoTotale == null) {
                try {
                    importoTotale = Double.parseDouble(mTot.group(1).replace(',', '.'));
                    continue; // riga totale, non è una voce
                } catch (Exception ignored) {}
            }

            Matcher mSub = PATTERN_SUBTOTALE.matcher(riga);
            if (mSub.find() && subtotale == null) {
                try {
                    subtotale = Double.parseDouble(mSub.group(1).replace(',', '.'));
                    continue; // riga subtotale, non è una voce
                } catch (Exception ignored) {}
            }

            // 5. Metodo di Pagamento
            String rigaLower = riga.toLowerCase(Locale.ROOT);
            if (metodoPagamento == null) {
                if (rigaLower.contains("bancomat") || rigaLower.contains("carta") || rigaLower.contains("pos") || rigaLower.contains("electronic")) {
                    metodoPagamento = "Carta / Elettronico";
                } else if (rigaLower.contains("contanti") || rigaLower.contains("cash")) {
                    metodoPagamento = "Contanti";
                }
            }

            // 6. Esclusione righe di sistema e rumore
            if (isRigaDiSistema(rigaLower)) {
                continue;
            }

            // 7. Estrazione singola voce (nome riga + prezzo a fine riga)
            Matcher mPrezzo = PATTERN_PREZZO_FINALE.matcher(riga);
            if (mPrezzo.find()) {
                String prezzoStr = mPrezzo.group(1).replace(',', '.');
                try {
                    double prezzoTotale = Double.parseDouble(prezzoStr);
                    if (prezzoTotale > 0.05 && prezzoTotale < 3000.0) {
                        String descrizione = riga.substring(0, mPrezzo.start()).trim();

                        // Rimozione di eventuali codici numerici all'inizio (es. "1 PANE" o "001 PANE")
                        descrizione = PATTERN_LEADING_DIGITS.matcher(descrizione).replaceFirst("").trim();

                        // Ricerca eventuale moltiplicatore quantità (es. "2 x 1.50" o "2 pz x 1.50")
                        double quantita = 1.0;
                        double prezzoUnitario = prezzoTotale;

                        Matcher mQta = PATTERN_QTA.matcher(descrizione);
                        if (mQta.find()) {
                            try {
                                quantita = Double.parseDouble(mQta.group(1));
                                if (mQta.group(2) != null) {
                                    prezzoUnitario = Double.parseDouble(mQta.group(2).replace(',', '.'));
                                } else if (quantita > 0) {
                                    prezzoUnitario = Math.round((prezzoTotale / quantita) * 100.0) / 100.0;
                                }
                                descrizione = descrizione.substring(mQta.end()).trim();
                            } catch (Exception ignored) {}
                        }

                        if (!descrizione.isEmpty() && descrizione.length() >= 2 && !PATTERN_ONLY_SYMBOLS.matcher(descrizione).matches()) {
                            voci.add(new ScontrinoDigitale.VoceScontrino(descrizione, quantita, prezzoUnitario, prezzoTotale));
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        // Fallback Importo Totale se non trovato esplicitamente dalla parola "TOTALE"
        if (importoTotale == null) {
            double sommaVoci = 0.0;
            for (ScontrinoDigitale.VoceScontrino v : voci) {
                sommaVoci += v.getPrezzoTotale();
            }
            if (sommaVoci > 0.0) {
                importoTotale = Math.round(sommaVoci * 100.0) / 100.0;
            } else {
                // Ricerca massima cifra plausibile nel testo
                List<Double> cifre = new ArrayList<>();
                for (RigaScontrinoGeometrica r : righeOrdinate) {
                    Matcher m = PATTERN_GENERICO.matcher(r.testo);
                    while (m.find()) {
                        try {
                            double val = Double.parseDouble(m.group(1).replace(',', '.'));
                            if (val > 0.50 && val < 5000.0) cifre.add(val);
                        } catch (Exception ignored) {}
                    }
                }
                if (!cifre.isEmpty()) {
                    Collections.sort(cifre);
                    importoTotale = cifre.get(cifre.size() - 1);
                }
            }
        }

        // Costruzione dell'oggetto strutturato
        ScontrinoDigitale digitale = new ScontrinoDigitale();
        digitale.setEsercente(esercente != null ? esercente : "Scontrino");
        digitale.setDataFormatted(dataStr != null ? dataStr : "");
        digitale.setTimestamp(dataRilevata != null ? dataRilevata : System.currentTimeMillis());
        digitale.setTotale(importoTotale);
        digitale.setSubtotale(subtotale != null ? subtotale : importoTotale);
        digitale.setMetodoPagamento(metodoPagamento);
        digitale.setPiva(piva);
        digitale.setVoci(voci);
        digitale.setTestoGreggio(testoCompleto);

        return new RisultatoOcr(importoTotale, dataRilevata, esercente, testoCompleto, digitale);
    }

    private static boolean isRigaDiSistema(String rigaLower) {
        return rigaLower.contains("documento commerciale")
                || rigaLower.contains("scontrino fiscale")
                || rigaLower.contains("registratore telematico")
                || rigaLower.contains("rt ")
                || rigaLower.contains("matricola")
                || rigaLower.contains("arrivederci")
                || rigaLower.contains("grazie")
                || rigaLower.contains("partita iva")
                || rigaLower.contains("cod. fisc")
                || rigaLower.contains("punto vendita")
                || rigaLower.contains("cassiere")
                || rigaLower.contains("operatore")
                || rigaLower.contains("totale euro")
                || rigaLower.contains("totale complessivo")
                || rigaLower.contains("totale dovuto")
                || rigaLower.contains("subtotale")
                || rigaLower.contains("pagamento")
                || rigaLower.contains("importo pagato")
                || rigaLower.contains("resto")
                || rigaLower.contains("transazione")
                || rigaLower.contains("autorizzazione")
                || rigaLower.contains("acquirer")
                || rigaLower.contains("pan")
                || rigaLower.contains("aid");
    }

    private static String pulisciNomeEsercente(String grezzo) {
        String clean = PATTERN_NON_ALPHA_ESERCENTE.matcher(grezzo).replaceAll(" ").trim();
        return clean.length() > 50 ? clean.substring(0, 50).trim() : clean;
    }

    /**
     * Struttura helper che raggruppa le righe OCR in base alla coordinata verticale Y
     * per unire testo e prezzi posti su colonne separate.
     */
    private static class RigaScontrinoGeometrica {
        final int top;
        final int bottom;
        final String testo;

        RigaScontrinoGeometrica(int top, int bottom, String testo) {
            this.top = top;
            this.bottom = bottom;
            this.testo = testo;
        }

        int centerY() {
            return (top + bottom) / 2;
        }

        int height() {
            return Math.max(bottom - top, 10);
        }
    }

    private static List<RigaScontrinoGeometrica> ricostruisciRigheGeometriche(Text visionText) {
        List<Text.Line> tutteLinee = new ArrayList<>();
        for (Text.TextBlock block : visionText.getTextBlocks()) {
            tutteLinee.addAll(block.getLines());
        }

        // Ordina dall'alto verso il basso (coordinata top)
        Collections.sort(tutteLinee, (l1, l2) -> {
            Rect r1 = l1.getBoundingBox();
            Rect r2 = l2.getBoundingBox();
            int top1 = r1 != null ? r1.top : 0;
            int top2 = r2 != null ? r2.top : 0;
            return Integer.compare(top1, top2);
        });

        List<List<Text.Line>> gruppiOrizzontali = new ArrayList<>();
        for (Text.Line linea : tutteLinee) {
            Rect r = linea.getBoundingBox();
            if (r == null) continue;

            boolean aggiunto = false;
            for (List<Text.Line> gruppo : gruppiOrizzontali) {
                Rect rCapo = gruppo.get(0).getBoundingBox();
                if (rCapo != null) {
                    int tolleranza = (int) (Math.max(rCapo.height(), r.height()) * 0.6);
                    if (Math.abs(rCapo.centerY() - r.centerY()) <= tolleranza) {
                        gruppo.add(linea);
                        aggiunto = true;
                        break;
                    }
                }
            }

            if (!aggiunto) {
                List<Text.Line> nuovoGruppo = new ArrayList<>();
                nuovoGruppo.add(linea);
                gruppiOrizzontali.add(nuovoGruppo);
            }
        }

        List<RigaScontrinoGeometrica> risultato = new ArrayList<>();
        for (List<Text.Line> gruppo : gruppiOrizzontali) {
            // Ordina le linee dello stesso gruppo da sinistra a destra
            Collections.sort(gruppo, Comparator.comparingInt(l -> l.getBoundingBox() != null ? l.getBoundingBox().left : 0));

            StringBuilder sb = new StringBuilder();
            int minTop = Integer.MAX_VALUE;
            int maxBottom = Integer.MIN_VALUE;

            for (Text.Line l : gruppo) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(l.getText());
                Rect br = l.getBoundingBox();
                if (br != null) {
                    minTop = Math.min(minTop, br.top);
                    maxBottom = Math.max(maxBottom, br.bottom);
                }
            }

            if (minTop == Integer.MAX_VALUE) {
                minTop = 0;
                maxBottom = 20;
            }

            risultato.add(new RigaScontrinoGeometrica(minTop, maxBottom, sb.toString()));
        }

        return risultato;
    }

    /**
     * Decodifica una miniatura scalata in modo sicuro (anti OutOfMemoryError) per l'anteprima UI.
     */
    @Nullable
    public static android.graphics.Bitmap caricaMiniatura(@NonNull Context context, @NonNull Uri uri, int maxDimensione) {
        try {
            android.graphics.BitmapFactory.Options boundsOptions = new android.graphics.BitmapFactory.Options();
            boundsOptions.inJustDecodeBounds = true;
            try (java.io.InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                android.graphics.BitmapFactory.decodeStream(in, null, boundsOptions);
            }

            int larghezza = boundsOptions.outWidth;
            int altezza = boundsOptions.outHeight;
            if (larghezza <= 0 || altezza <= 0) return null;

            int sampleSize = 1;
            while ((larghezza / sampleSize) > maxDimensione || (altezza / sampleSize) > maxDimensione) {
                sampleSize *= 2;
            }

            android.graphics.BitmapFactory.Options decodeOptions = new android.graphics.BitmapFactory.Options();
            decodeOptions.inSampleSize = sampleSize;
            decodeOptions.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565;

            try (java.io.InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                return android.graphics.BitmapFactory.decodeStream(in, null, decodeOptions);
            }
        } catch (Exception e) {
            Log.w(TAG, "Impossibile caricare miniatura scalata per URI: " + uri, e);
            return null;
        }
    }
}
