package com.example.paripariapp.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.example.paripariapp.MainActivity;
import com.example.paripariapp.R;
import com.example.paripariapp.ui.view.DettaglioSchedaActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.HashMap;
import java.util.Map;

/**
 * Servizio per la gestione dei token e dei messaggi push Firebase Cloud Messaging (FCM).
 * Consente la ricezione di notifiche native quando l'app è a schermo spento o in background.
 */
public class PariPariMessagingService extends FirebaseMessagingService {

    private static final String TAG = "PariPariFCM";
    public static final String CANALE_NOTIFICHE_ID = "paripari_spese_channel";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Nuovo token FCM generato: " + token);
        inviaTokenAlServer(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "Messaggio FCM ricevuto da: " + remoteMessage.getFrom());

        String titolo = null;
        String messaggio = null;
        String schedaId = null;

        // 1. Estrazione da notification payload (se presente)
        if (remoteMessage.getNotification() != null) {
            titolo = remoteMessage.getNotification().getTitle();
            messaggio = remoteMessage.getNotification().getBody();
        }

        // 2. Override o fallback su data payload
        if (remoteMessage.getData().size() > 0) {
            if (remoteMessage.getData().containsKey("title")) {
                titolo = remoteMessage.getData().get("title");
            }
            if (remoteMessage.getData().containsKey("body")) {
                messaggio = remoteMessage.getData().get("body");
            }
            if (remoteMessage.getData().containsKey(DettaglioSchedaActivity.EXTRA_SCHEDA_ID)) {
                schedaId = remoteMessage.getData().get(DettaglioSchedaActivity.EXTRA_SCHEDA_ID);
            } else if (remoteMessage.getData().containsKey("scheda_id")) {
                schedaId = remoteMessage.getData().get("scheda_id");
            } else if (remoteMessage.getData().containsKey("schedaId")) {
                schedaId = remoteMessage.getData().get("schedaId");
            }
        }

        if (titolo == null) {
            titolo = getString(R.string.app_name);
        }
        if (messaggio != null) {
            mostraNotifica(titolo, messaggio, schedaId);
        }
    }

    /**
     * Mostra una notifica nativa di sistema con priorità alta.
     */
    private void mostraNotifica(String titolo, String messaggio) {
        mostraNotifica(titolo, messaggio, null);
    }

    private void mostraNotifica(String titolo, String messaggio, @androidx.annotation.Nullable String schedaId) {
        mostraNotificaNativa(this, titolo, messaggio, schedaId);
    }

    private void creaCanaleNotificaSeNecessario() {
        creaCanaleNotificaSeNecessario(this);
    }

    private static void creaCanaleNotificaSeNecessario(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && context != null) {
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null && notificationManager.getNotificationChannel(CANALE_NOTIFICHE_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CANALE_NOTIFICHE_ID,
                        "Spese e Rimborsi PariPari",
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Notifiche per nuove spese, modifiche e rimborsi nel gruppo");
                channel.enableVibration(true);
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Genera e mostra una notifica nativa di sistema direttamente da codice Java.
     */
    public static void mostraNotificaNativa(Context context, String titolo, String messaggio, @androidx.annotation.Nullable String schedaId) {
        if (context == null || messaggio == null || messaggio.trim().isEmpty()) return;

        creaCanaleNotificaSeNecessario(context);

        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (schedaId != null) {
            intent.putExtra(DettaglioSchedaActivity.EXTRA_SCHEDA_ID, schedaId);
        }

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context.getApplicationContext(),
                (int) System.currentTimeMillis(),
                intent,
                flags
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context.getApplicationContext(), CANALE_NOTIFICHE_ID)
                .setSmallIcon(R.drawable.ic_receipt)
                .setContentTitle(titolo != null ? titolo : context.getString(R.string.app_name))
                .setContentText(messaggio)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(messaggio))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    /**
     * Associa il token FCM al profilo dell'utente autenticato su Firestore.
     */
    public static void inviaTokenAlServer(String token) {
        if (token == null || token.isEmpty()) return;

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            Map<String, Object> tokenData = new HashMap<>();
            tokenData.put("token", token);
            tokenData.put("aggiornato_il", FieldValue.serverTimestamp());

            // 1. Salva nella sotto-collezione /users/{uid}/fcm_tokens/{token}
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(user.getUid())
                    .collection("fcm_tokens")
                    .document(token)
                    .set(tokenData)
                    .addOnFailureListener(e -> Log.w(TAG, "Impossibile registrare token FCM per l'utente", e));

            // 2. Salva anche come campo diretto sul documento profilo /users/{uid}
            Map<String, Object> userDirectToken = new HashMap<>();
            userDirectToken.put("fcmToken", token);
            userDirectToken.put("updatedAt", FieldValue.serverTimestamp());
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(user.getUid())
                    .set(userDirectToken, com.google.firebase.firestore.SetOptions.merge());
        }
    }
}
