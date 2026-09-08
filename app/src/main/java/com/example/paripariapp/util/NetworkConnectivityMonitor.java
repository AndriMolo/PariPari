package com.example.paripariapp.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * Monitor reattivo per lo stato della connessione di rete.
 * Rileva in tempo reale quando il dispositivo torna online per avviare
 * la sincronizzazione con Cloud Firestore senza consumare batteria (zero polling).
 */
public class NetworkConnectivityMonitor {

    private static final String TAG = "NetworkMonitor";

    public interface OnNetworkChangeListener {
        void onNetworkAvailable();
        void onNetworkLost();
    }

    private final ConnectivityManager connectivityManager;
    private final OnNetworkChangeListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean isRegistered = false;

    public NetworkConnectivityMonitor(Context context, OnNetworkChangeListener listener) {
        this.connectivityManager = (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        this.listener = listener;
    }

    /**
     * Verifica immediata e sincrona se la connessione Internet è attiva.
     */
    public boolean isConnected() {
        if (connectivityManager == null) return false;
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) return false;
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
        return capabilities != null && (
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        );
    }

    /**
     * Registra il callback di sistema per gli eventi di rete.
     */
    public synchronized void startMonitoring() {
        if (isRegistered || connectivityManager == null) return;

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                Log.d(TAG, "Connessione Internet rilevata!");
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onNetworkAvailable();
                    }
                });
            }

            @Override
            public void onLost(@NonNull Network network) {
                Log.d(TAG, "Connessione Internet persa");
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onNetworkLost();
                    }
                });
            }
        };

        connectivityManager.registerNetworkCallback(request, networkCallback);
        isRegistered = true;
    }

    /**
     * Disattiva il monitoraggio per evitare memory leak.
     */
    public synchronized void stopMonitoring() {
        if (!isRegistered || connectivityManager == null || networkCallback == null) return;
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        } catch (Exception e) {
            Log.w(TAG, "Errore nella disattivazione del monitor di rete: " + e.getMessage());
        }
        isRegistered = false;
    }
}
