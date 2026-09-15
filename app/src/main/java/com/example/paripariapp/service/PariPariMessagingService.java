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
        }

        if (titolo == null) {
            titolo = getString(R.string.app_name);
        }
        if (messaggio != null) {
            mostraNotifica(titolo, messaggio);
        }
    }

    /**
     * Mostra una notifica nativa di sistema con priorità alta.
     */
    private void mostraNotifica(String titolo, String messaggio) {
        creaCanaleNotificaSeNecessario();

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(this, (int) System.currentTimeMillis(), intent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CANALE_NOTIFICHE_ID)
                .setSmallIcon(R.drawable.ic_receipt)
                .setContentTitle(titolo)
                .setContentText(messaggio)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    private void creaCanaleNotificaSeNecessario() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
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
     * Associa il token FCM al profilo dell'utente autenticato su Firestore.
     */
    public static void inviaTokenAlServer(String token) {
        if (token == null || token.isEmpty()) return;

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && !user.isAnonymous()) {
            Map<String, Object> tokenData = new HashMap<>();
            tokenData.put("token", token);
            tokenData.put("aggiornato_il", FieldValue.serverTimestamp());

            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(user.getUid())
                    .collection("fcm_tokens")
                    .document(token)
                    .set(tokenData)
                    .addOnFailureListener(e -> Log.w(TAG, "Impossibile registrare token FCM per l'utente", e));
        }
    }
}
