package com.example.paripariapp.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.paripariapp.R;
import com.google.android.material.card.MaterialCardView;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Utility per il rendering istantaneo a costo storage 0 e banda 0
 * di Avatar Utente e Icone Gruppo (Emoji + Colore o Iniziale + Colore).
 */
public class AvatarVisualUtil {

    public static final String[] COLOR_PALETTE = new String[]{
            "#E64A19", // Coral
            "#E91E63", // Rose
            "#9C27B0", // Purple
            "#673AB7", // Deep Purple
            "#3F51B5", // Indigo
            "#1976D2", // Ocean Blue
            "#0097A7", // Cyan
            "#00796B", // Teal
            "#388E3C", // Emerald
            "#689F38", // Lime Green
            "#FFA000", // Amber
            "#546E7A"  // Slate Grey
    };

    public static final List<String> EMOJI_CIBO = Arrays.asList(
            "🍕", "🍔", "🍣", "🌮", "🥗", "🍦", "☕", "🍺", "🍷", "🛒"
    );

    public static final List<String> EMOJI_VIAGGI = Arrays.asList(
            "✈️", "🚗", "🚆", "🏖️", "🏔️", "🏕️", "🏨", "⛽", "🗺️", "🧳"
    );

    public static final List<String> EMOJI_CASA = Arrays.asList(
            "🏠", "🛋️", "💡", "🔑", "🧹", "🔌", "📦", "🚿", "🪴", "🧺"
    );

    public static final List<String> EMOJI_SVAGO = Arrays.asList(
            "⚽", "🎮", "🎟️", "🍿", "🎸", "⛷️", "🎾", "🎉", "🎳", "🎯"
    );

    public static final List<String> EMOJI_PERSONE = Arrays.asList(
            "😎", "🥳", "🤠", "🐱", "🐶", "🦊", "🐼", "🦁", "🦄", "🚀"
    );

    public static class AvatarConfig {
        public final boolean isImage;
        public final boolean isEmoji;
        public final boolean isInitial;
        public final String text;
        public final int backgroundColor;
        public final String imageUrl;

        public AvatarConfig(boolean isImage, boolean isEmoji, boolean isInitial,
                            String text, int backgroundColor, String imageUrl) {
            this.isImage = isImage;
            this.isEmoji = isEmoji;
            this.isInitial = isInitial;
            this.text = text;
            this.backgroundColor = backgroundColor;
            this.imageUrl = imageUrl;
        }
    }

    /**
     * Interpreta la stringa di visualizzazione avatar/icona e produce una configurazione visiva.
     */
    @NonNull
    public static AvatarConfig parse(@Nullable String rawString, @Nullable String fallbackName) {
        String fallbackInitial = extractInitial(fallbackName);
        int fallbackBg = getColorForText(fallbackName);

        if (rawString == null || rawString.trim().isEmpty()) {
            return new AvatarConfig(false, false, true, fallbackInitial, fallbackBg, null);
        }

        String raw = rawString.trim();

        // 1. URL Immagine HTTP/HTTPS (legacy o foto Google gratuita)
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            return new AvatarConfig(true, false, false, null, fallbackBg, raw);
        }

        // 2. Formato Emoji: "emoji:🍕:#FF5722" oppure legacy "emoji:🍕"
        if (raw.startsWith("emoji:")) {
            String[] parts = raw.split(":");
            if (parts.length >= 3) {
                String emoji = parts[1];
                int color = parseColorSafe(parts[2], fallbackBg);
                return new AvatarConfig(false, true, false, emoji, color, null);
            } else if (parts.length == 2) {
                String emoji = parts[1];
                return new AvatarConfig(false, true, false, emoji, fallbackBg, null);
            }
        }

        // 3. Formato Iniziale: "initial:N:#9C27B0"
        if (raw.startsWith("initial:")) {
            String[] parts = raw.split(":");
            if (parts.length >= 3) {
                String initial = parts[1].toUpperCase(Locale.getDefault());
                int color = parseColorSafe(parts[2], fallbackBg);
                return new AvatarConfig(false, false, true, initial, color, null);
            } else if (parts.length == 2) {
                String initial = parts[1].toUpperCase(Locale.getDefault());
                return new AvatarConfig(false, false, true, initial, fallbackBg, null);
            }
        }

        // 4. Se la stringa è direttamente una singola emoji o testo
        if (raw.length() <= 4 && isEmojiOrShort(raw)) {
            return new AvatarConfig(false, true, false, raw, fallbackBg, null);
        }

        return new AvatarConfig(false, false, true, fallbackInitial, fallbackBg, null);
    }

    /**
     * Applica la configurazione avatar alla composizione standard MaterialCardView + TextView + ImageView.
     */
    public static void applyToCard(@Nullable String rawData,
                                   @Nullable MaterialCardView cardContainer,
                                   @Nullable TextView tvEmoji,
                                   @Nullable ImageView ivIcon,
                                   @Nullable String fallbackName) {
        AvatarConfig cfg = parse(rawData, fallbackName);

        if (cardContainer != null) {
            cardContainer.setCardBackgroundColor(cfg.backgroundColor);
        }

        if (cfg.isImage) {
            if (tvEmoji != null) tvEmoji.setVisibility(View.GONE);
            if (ivIcon != null) {
                ivIcon.setVisibility(View.VISIBLE);
                ImageLoaderUtil.caricaImmagine(cfg.imageUrl, ivIcon, R.drawable.ic_account);
            }
        } else {
            if (ivIcon != null) ivIcon.setVisibility(View.GONE);
            if (tvEmoji != null) {
                tvEmoji.setVisibility(View.VISIBLE);
                tvEmoji.setText(cfg.text);
                if (cfg.isInitial) {
                    tvEmoji.setTextColor(Color.WHITE);
                    tvEmoji.setTextSize(18);
                } else {
                    tvEmoji.setTextSize(22);
                }
            }
        }
    }

    /**
     * Crea un Drawable circolare nativo contenente l'avatar (Emoji o Iniziale con sfondo colorato)
     * utilizzabile in qualsiasi ImageView.
     */
    @NonNull
    public static Drawable createAvatarDrawable(@NonNull Context context,
                                                @Nullable String rawData,
                                                @Nullable String fallbackName,
                                                int sizePx) {
        AvatarConfig cfg = parse(rawData, fallbackName);

        if (sizePx <= 0) sizePx = 96;

        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // Disegna cerchio di sfondo
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(cfg.backgroundColor);
        bgPaint.setStyle(Paint.Style.FILL);
        float radius = sizePx / 2f;
        canvas.drawCircle(radius, radius, radius, bgPaint);

        // Disegna testo/emoji al centro
        String textToDraw = cfg.text != null ? cfg.text : extractInitial(fallbackName);
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextAlign(Paint.Align.CENTER);

        if (cfg.isInitial) {
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(sizePx * 0.45f);
            textPaint.setFakeBoldText(true);
        } else {
            textPaint.setTextSize(sizePx * 0.50f);
        }

        Rect bounds = new Rect();
        textPaint.getTextBounds(textToDraw, 0, textToDraw.length(), bounds);
        float y = radius + (bounds.height() / 2f) - bounds.bottom;
        canvas.drawText(textToDraw, radius, y, textPaint);

        return new BitmapDrawable(context.getResources(), bitmap);
    }

    /**
     * Applica l'avatar direttamente a un ImageView (supportando sia Drawable locale che URL remoto).
     */
    public static void applyToImageView(@NonNull Context context,
                                        @NonNull ImageView imageView,
                                        @Nullable String rawData,
                                        @Nullable String fallbackName) {
        AvatarConfig cfg = parse(rawData, fallbackName);
        if (cfg.isImage) {
            ImageLoaderUtil.caricaImmagine(cfg.imageUrl, imageView, R.drawable.ic_account);
        } else {
            imageView.setImageDrawable(createAvatarDrawable(context, rawData, fallbackName, 120));
        }
    }

    /**
     * Applica l'iniziale del nome e lo sfondo colorato a una TextView (avatar circolare),
     * mutando il GradientDrawable esistente se già presente per azzerare l'allocazione
     * continua di oggetti e prevenire lag/jank durante lo scrolling del RecyclerView.
     */
    public static void applyInitialToTextView(@NonNull TextView textView, @Nullable String name) {
        String iniziale = extractInitial(name);
        int color = getColorForText(name);
        textView.setText(iniziale);
        textView.setTextColor(Color.WHITE);
        applyColorToBackground(textView, color);
    }

    /**
     * Applica la configurazione avatar (emoji o iniziale) a una TextView circolare,
     * riutilizzando o mutando il GradientDrawable esistente.
     */
    public static void applyAvatarConfigToTextView(@NonNull TextView textView, @NonNull AvatarConfig cfg) {
        textView.setText(cfg.text);
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(cfg.isEmoji ? 18 : 16);
        applyColorToBackground(textView, cfg.backgroundColor);
    }

    private static void applyColorToBackground(@NonNull TextView textView, int color) {
        Drawable bg = textView.getBackground();
        if (bg instanceof android.graphics.drawable.GradientDrawable) {
            ((android.graphics.drawable.GradientDrawable) bg.mutate()).setColor(color);
        } else {
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            gd.setColor(color);
            textView.setBackground(gd);
        }
    }

    public static int getColorForText(@Nullable String text) {
        if (text == null || text.trim().isEmpty()) return parseColorSafe(COLOR_PALETTE[0], 0xFFE64A19);
        int hash = Math.abs(text.hashCode());
        return parseColorSafe(COLOR_PALETTE[hash % COLOR_PALETTE.length], 0xFFE64A19);
    }

    public static int parseColorSafe(String colorHex, int fallback) {
        if (colorHex == null || colorHex.trim().isEmpty()) return fallback;
        try {
            String clean = colorHex.trim();
            if (clean.startsWith("#")) clean = clean.substring(1);
            if (clean.length() == 6) {
                return (int) Long.parseLong("FF" + clean, 16);
            } else if (clean.length() == 8) {
                return (int) Long.parseLong(clean, 16);
            }
            return Color.parseColor(colorHex);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String extractInitial(@Nullable String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String clean = name.trim();
        return clean.isEmpty() ? "?" : String.valueOf(clean.charAt(0)).toUpperCase(Locale.getDefault());
    }

    private static boolean isEmojiOrShort(String text) {
        if (text == null || text.isEmpty()) return false;
        int type = Character.getType(text.codePointAt(0));
        return type == Character.SURROGATE || type == Character.OTHER_SYMBOL || text.length() <= 3;
    }
}
