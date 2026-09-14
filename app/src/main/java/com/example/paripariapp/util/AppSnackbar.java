package com.example.paripariapp.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;

import com.example.paripariapp.R;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.snackbar.Snackbar;

/**
 * Utility per mostrare messaggi di notifica inferiori (Snackbar) con uno stile
 * coerente, elegante e perfettamente integrato con il design Material 3 dell'app PariPari:
 * - Forma pill / card flottante con angoli arrotondati (20dp)
 * - Margini laterali e inferiori per non toccare i bordi dello schermo
 * - Palette armoniosa (Surface Container, testo OnSurface ad alto contrasto, azione in Primary)
 * - Animazione fluida di scorrimento (slide)
 */
public final class AppSnackbar {

    private static final int CORNER_RADIUS_DP = 20;
    private static final int HORIZONTAL_MARGIN_DP = 16;
    private static final int BOTTOM_MARGIN_DP = 16;
    private static final int ELEVATION_DP = 6;

    private AppSnackbar() {
        // Utility class
    }

    /**
     * Crea e personalizza una Snackbar con lo stile unificato dell'app.
     */
    @NonNull
    public static Snackbar make(@NonNull View view, @NonNull CharSequence text, int duration) {
        Snackbar snackbar = Snackbar.make(view, text, duration);
        applyUnifiedStyle(snackbar, view);
        return snackbar;
    }

    /**
     * Crea e personalizza una Snackbar tramite string resource ID.
     */
    @NonNull
    public static Snackbar make(@NonNull View view, @StringRes int resId, int duration) {
        Snackbar snackbar = Snackbar.make(view, resId, duration);
        applyUnifiedStyle(snackbar, view);
        return snackbar;
    }

    /**
     * Mostra rapidamente un messaggio breve (SHORT).
     */
    public static void show(@Nullable View view, @NonNull CharSequence text) {
        if (view == null) return;
        make(view, text, Snackbar.LENGTH_SHORT).show();
    }

    /**
     * Mostra rapidamente un messaggio breve (SHORT) tramite string resource ID.
     */
    public static void show(@Nullable View view, @StringRes int resId) {
        if (view == null) return;
        make(view, resId, Snackbar.LENGTH_SHORT).show();
    }

    /**
     * Mostra un messaggio lungo (LONG).
     */
    public static void showLong(@Nullable View view, @NonNull CharSequence text) {
        if (view == null) return;
        make(view, text, Snackbar.LENGTH_LONG).show();
    }

    /**
     * Mostra un messaggio lungo (LONG) tramite string resource ID.
     */
    public static void showLong(@Nullable View view, @StringRes int resId) {
        if (view == null) return;
        make(view, resId, Snackbar.LENGTH_LONG).show();
    }

    /**
     * Mostra rapidamente un messaggio breve (SHORT) da un Fragment,
     * assicurando che rimanga visibile anche in caso di navigazione/popBackStack.
     */
    public static void showFromFragment(@Nullable androidx.fragment.app.Fragment fragment, @NonNull CharSequence text) {
        if (fragment == null) return;
        View target = null;
        if (fragment.getActivity() != null) {
            target = fragment.getActivity().findViewById(android.R.id.content);
        }
        if (target == null && fragment.getView() != null) {
            target = fragment.getView();
        }
        if (target != null) {
            show(target, text);
        }
    }

    /**
     * Mostra rapidamente un messaggio breve (SHORT) tramite string resource ID da un Fragment.
     */
    public static void showFromFragment(@Nullable androidx.fragment.app.Fragment fragment, @StringRes int resId) {
        if (fragment == null) return;
        View target = null;
        if (fragment.getActivity() != null) {
            target = fragment.getActivity().findViewById(android.R.id.content);
        }
        if (target == null && fragment.getView() != null) {
            target = fragment.getView();
        }
        if (target != null) {
            show(target, resId);
        }
    }

    /**
     * Mostra un messaggio lungo (LONG) da un Fragment.
     */
    public static void showLongFromFragment(@Nullable androidx.fragment.app.Fragment fragment, @NonNull CharSequence text) {
        if (fragment == null) return;
        View target = null;
        if (fragment.getActivity() != null) {
            target = fragment.getActivity().findViewById(android.R.id.content);
        }
        if (target == null && fragment.getView() != null) {
            target = fragment.getView();
        }
        if (target != null) {
            showLong(target, text);
        }
    }

    /**
     * Mostra un messaggio lungo (LONG) tramite string resource ID da un Fragment.
     */
    public static void showLongFromFragment(@Nullable androidx.fragment.app.Fragment fragment, @StringRes int resId) {
        if (fragment == null) return;
        View target = null;
        if (fragment.getActivity() != null) {
            target = fragment.getActivity().findViewById(android.R.id.content);
        }
        if (target == null && fragment.getView() != null) {
            target = fragment.getView();
        }
        if (target != null) {
            showLong(target, resId);
        }
    }

    /**
     * Applica la formattazione grafica Material 3 unificata.
     */
    public static void applyUnifiedStyle(@NonNull Snackbar snackbar, @NonNull View contextView) {
        Context context = contextView.getContext();
        View snackbarView = snackbar.getView();

        // 1. Animazione a scorrimento (slide)
        snackbar.setAnimationMode(Snackbar.ANIMATION_MODE_SLIDE);

        // 2. Forma a card arrotondata (Pill Shape)
        float cornerRadiusPx = dpToPx(context, CORNER_RADIUS_DP);
        MaterialShapeDrawable backgroundShape = new MaterialShapeDrawable(
                ShapeAppearanceModel.builder()
                        .setAllCornerSizes(cornerRadiusPx)
                        .build()
        );

        // Colore di sfondo elegante (Surface Container Highest / Inverse Surface)
        int backgroundColor = com.google.android.material.color.MaterialColors.getColor(contextView, com.google.android.material.R.attr.colorSurfaceContainerHighest);
        backgroundShape.setFillColor(ColorStateList.valueOf(backgroundColor));
        backgroundShape.setElevation(dpToPx(context, ELEVATION_DP));
        snackbarView.setBackground(backgroundShape);

        // 3. Margini flottanti (floating style)
        int marginHorizontalPx = dpToPx(context, HORIZONTAL_MARGIN_DP);
        int marginBottomPx = dpToPx(context, BOTTOM_MARGIN_DP);

        ViewGroup.LayoutParams layoutParams = snackbarView.getLayoutParams();
        if (layoutParams instanceof CoordinatorLayout.LayoutParams) {
            CoordinatorLayout.LayoutParams coordParams = (CoordinatorLayout.LayoutParams) layoutParams;
            coordParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            coordParams.setMargins(marginHorizontalPx, 0, marginHorizontalPx, marginBottomPx);
            snackbarView.setLayoutParams(coordParams);
        } else if (layoutParams instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams frameParams = (FrameLayout.LayoutParams) layoutParams;
            frameParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            frameParams.setMargins(marginHorizontalPx, 0, marginHorizontalPx, marginBottomPx);
            snackbarView.setLayoutParams(frameParams);
        } else if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) layoutParams;
            marginParams.setMargins(marginHorizontalPx, 0, marginHorizontalPx, marginBottomPx);
            snackbarView.setLayoutParams(marginParams);
        }

        // 4. Stile testo del messaggio
        TextView textView = snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);
        if (textView != null) {
            int textColor = com.google.android.material.color.MaterialColors.getColor(contextView, com.google.android.material.R.attr.colorOnSurface);
            textView.setTextColor(textColor);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            textView.setMaxLines(3);
        }

        // 5. Stile pulsante azione (es. "Annulla")
        Button actionButton = snackbarView.findViewById(com.google.android.material.R.id.snackbar_action);
        if (actionButton != null) {
            int primaryColor = com.google.android.material.color.MaterialColors.getColor(contextView, androidx.appcompat.R.attr.colorPrimary);
            actionButton.setTextColor(primaryColor);
            actionButton.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            actionButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        }

        ViewCompat.setElevation(snackbarView, dpToPx(context, ELEVATION_DP));
    }

    private static int dpToPx(@NonNull Context context, int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.getResources().getDisplayMetrics()
        );
    }
}
