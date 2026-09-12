package com.example.paripariapp.ui.viewmodel;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.paripariapp.data.repository.PariPariRepository;
import com.example.paripariapp.data.repository.UserPreferencesRepository;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * ViewModel per la gestione del profilo, dell'Account Ospite e dell'autenticazione Firebase.
 */
public class AccountViewModel extends AndroidViewModel {

    private static final String TAG = "AccountViewModel";

    private final PariPariRepository repository;
    private final UserPreferencesRepository preferencesRepository;
    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;

    private final MutableLiveData<FirebaseUser> userLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isGuestMode = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isEmailVerifiedLive = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<String> successMessage = new MutableLiveData<>();

    private final FirebaseAuth.AuthStateListener authListener;

    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private Runnable pollRunnable;
    private boolean isPollingActive = false;

    public AccountViewModel(@NonNull Application application) {
        super(application);
        repository = PariPariRepository.getInstance(application);
        preferencesRepository = UserPreferencesRepository.getInstance(application);
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        // Inizializzazione sincrona immediata dello stato con l'utente corrente
        FirebaseUser initialUser = auth.getCurrentUser();
        userLiveData.setValue(initialUser);
        boolean initialGuest = (initialUser == null || initialUser.isAnonymous());
        isGuestMode.setValue(initialGuest);
        boolean initialVerified = (initialUser != null && !initialUser.isAnonymous() && initialUser.isEmailVerified());
        isEmailVerifiedLive.setValue(initialVerified);

        // Listener reattivo sullo stato di autenticazione per aggiornamenti successivi
        authListener = firebaseAuth -> {
            FirebaseUser user = firebaseAuth.getCurrentUser();
            userLiveData.setValue(user);
            boolean guest = (user == null || user.isAnonymous());
            isGuestMode.setValue(guest);
            boolean verified = (user != null && !user.isAnonymous() && user.isEmailVerified());
            isEmailVerifiedLive.setValue(verified);
        };
        auth.addAuthStateListener(authListener);

        // Assicura che ci sia almeno un utente anonimo all'avvio
        ensureUserSession();
    }

    private void ensureUserSession() {
        FirebaseUser current = auth.getCurrentUser();
        if (current == null) {
            auth.signInAnonymously().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Log.d(TAG, "Accesso anonimo iniziale completato");
                } else {
                    Log.w(TAG, "Accesso anonimo non riuscito: " + task.getException());
                }
            });
        }
    }

    // ====================================================================
    // GETTERS DEI DATI
    // ====================================================================

    public LiveData<FirebaseUser> getUserLiveData() {
        return userLiveData;
    }

    public LiveData<Boolean> getIsGuestMode() {
        return isGuestMode;
    }

    public LiveData<Boolean> getIsEmailVerifiedLive() {
        return isEmailVerifiedLive;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<String> getSuccessMessage() {
        return successMessage;
    }

    public void clearSuccessMessage() {
        successMessage.setValue(null);
    }

    public void clearErrorMessage() {
        errorMessage.setValue(null);
    }

    public LiveData<String> getDefaultCurrencyLive() {
        return preferencesRepository.getDefaultCurrencyLive();
    }

    public String getDefaultCurrency() {
        return preferencesRepository.getDefaultCurrency();
    }

    public void setDefaultCurrency(String currencyCode) {
        preferencesRepository.setDefaultCurrency(currencyCode);

        // Se l'utente è autenticato con account registrato, sincronizza su Firestore
        FirebaseUser user = auth.getCurrentUser();
        if (user != null && !user.isAnonymous()) {
            Map<String, Object> update = new HashMap<>();
            update.put("valutaPredefinita", preferencesRepository.extractCurrencyCode(currencyCode));
            update.put("updatedAt", FieldValue.serverTimestamp());
            firestore.collection("users").document(user.getUid())
                    .update(update)
                    .addOnFailureListener(e -> Log.w(TAG, "Aggiornamento valuta profilo fallito: " + e.getMessage()));
        }
    }

    public LiveData<String> getAppLanguageLive() {
        return preferencesRepository.getAppLanguageLive();
    }

    public String getAppLanguage() {
        return preferencesRepository.getAppLanguage();
    }

    public void setAppLanguage(String langCode) {
        preferencesRepository.setAppLanguage(langCode);

        // Se l'utente è autenticato con account registrato, sincronizza su Firestore
        FirebaseUser user = auth.getCurrentUser();
        if (user != null && !user.isAnonymous()) {
            Map<String, Object> update = new HashMap<>();
            update.put("linguaPredefinita", UserPreferencesRepository.extractLanguageCode(langCode));
            update.put("updatedAt", FieldValue.serverTimestamp());
            firestore.collection("users").document(user.getUid())
                    .update(update)
                    .addOnFailureListener(e -> Log.w(TAG, "Aggiornamento lingua profilo fallito: " + e.getMessage()));
        }
    }

    // ====================================================================
    // AZIONI DI AUTENTICAZIONE
    // ====================================================================

    /**
     * Registrazione con Nome, Email e Password.
     * Se l'utente è attualmente anonimo, esegue l'upgrade trasparente (Account Linking)
     * preservando tutte le spese create in modalità ospite.
     */
    public void register(String nome, String email, String password) {
        isLoading.setValue(true);
        FirebaseUser current = auth.getCurrentUser();

        if (current != null && current.isAnonymous()) {
            // Upgrade dell'account anonimo esistente
            AuthCredential credential = EmailAuthProvider.getCredential(email, password);
            current.linkWithCredential(credential)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            aggiornaProfiloEDb(current, nome, email, true);
                        } else {
                            // Se il link fallisce (es. email già usata), prova creazione diretta
                            createUserDirectly(nome, email, password);
                        }
                    });
        } else {
            createUserDirectly(nome, email, password);
        }
    }

    private void createUserDirectly(String nome, String email, String password) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        FirebaseUser newUser = task.getResult().getUser();
                        aggiornaProfiloEDb(newUser, nome, email, true);
                    } else {
                        isLoading.setValue(false);
                        String err = task.getException() != null ? task.getException().getLocalizedMessage() : "Errore durante la registrazione";
                        errorMessage.setValue(err);
                    }
                });
    }

    private void aggiornaProfiloEDb(FirebaseUser user, String nome, String email, boolean isNewAccount) {
        if (user == null) {
            isLoading.setValue(false);
            return;
        }

        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                .setDisplayName(nome)
                .build();

        user.updateProfile(profileUpdates).addOnCompleteListener(profileTask -> {
            // Salva il documento profilo su Firestore
            Map<String, Object> userData = new HashMap<>();
            userData.put("nome", nome);
            userData.put("email", email);
            userData.put("updatedAt", FieldValue.serverTimestamp());
            if (isNewAccount) {
                userData.put("createdAt", FieldValue.serverTimestamp());
            }

            firestore.collection("users").document(user.getUid())
                    .set(userData)
                    .addOnCompleteListener(dbTask -> {
                        isLoading.setValue(false);
                        userLiveData.setValue(user);
                        isGuestMode.setValue(false);
                        isEmailVerifiedLive.setValue(user.isEmailVerified());
                        if (isNewAccount) {
                            user.sendEmailVerification()
                                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Email di verifica inviata con successo"))
                                    .addOnFailureListener(e -> Log.w(TAG, "Invio email di verifica fallito: " + e.getMessage()));
                        }
                        successMessage.setValue(isNewAccount
                                ? getApplication().getString(com.example.paripariapp.R.string.msg_registrazione_ok)
                                : getApplication().getString(com.example.paripariapp.R.string.msg_profilo_aggiornato));
                    });
        });
    }

    /**
     * Callback per notificare il completamento della ricarica dello stato utente.
     */
    public interface UserReloadCallback {
        void onReloadComplete(boolean isVerified);
    }

    /**
     * Accesso con Email e Password per utenti già registrati.
     */
    public void login(String email, String password) {
        isLoading.setValue(true);
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    isLoading.setValue(false);
                    if (task.isSuccessful() && task.getResult() != null) {
                        FirebaseUser user = task.getResult().getUser();
                        userLiveData.setValue(user);
                        isGuestMode.setValue(false);
                        isEmailVerifiedLive.setValue(user != null && user.isEmailVerified());
                        successMessage.setValue(getApplication().getString(com.example.paripariapp.R.string.msg_login_ok));
                    } else {
                        String err = task.getException() != null ? task.getException().getLocalizedMessage() : "Credenziali non valide";
                        errorMessage.setValue(err);
                    }
                });
    }

    /**
     * Ricarica lo stato dell'utente da Firebase Auth (versione silenziosa per onResume).
     */
    public void reloadUser() {
        reloadUser(null);
    }

    /**
     * Ricarica lo stato dell'utente da Firebase Auth e notifica l'esito al completamento.
     */
    public void reloadUser(UserReloadCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null && !user.isAnonymous()) {
            user.reload().addOnCompleteListener(task -> {
                boolean verified = false;
                if (task.isSuccessful()) {
                    FirebaseUser reloaded = auth.getCurrentUser();
                    userLiveData.setValue(reloaded);
                    verified = (reloaded != null && reloaded.isEmailVerified());
                    boolean wasUnverified = !Boolean.TRUE.equals(isEmailVerifiedLive.getValue());
                    isEmailVerifiedLive.setValue(verified);
                    if (verified) {
                        stopEmailVerificationPolling();
                        if (wasUnverified) {
                            successMessage.setValue(getApplication().getString(com.example.paripariapp.R.string.msg_email_confermata_successo));
                        }
                    }
                } else {
                    Log.w(TAG, "Ricarica utente fallita: " + task.getException());
                }
                if (callback != null) {
                    callback.onReloadComplete(verified);
                }
            });
        } else if (callback != null) {
            callback.onReloadComplete(false);
        }
    }

    private static final int MAX_POLL_ATTEMPTS = 30; // ~75 secondi di tentativi massimi a schermo acceso
    private int pollAttempts = 0;

    /**
     * Avvia il polling periodico (ogni 2.5s) per rilevare la verifica dell'email
     * in tempo reale, ad esempio mentre l'utente clicca il link nella mail o torna nell'app.
     * Si interrompe automaticamente dopo MAX_POLL_ATTEMPTS se l'utente non verifica l'email.
     */
    public void startEmailVerificationPolling() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || user.isAnonymous() || user.isEmailVerified()) {
            stopEmailVerificationPolling();
            return;
        }

        if (isPollingActive) {
            return;
        }

        isPollingActive = true;
        pollAttempts = 0;
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isPollingActive) return;

                pollAttempts++;
                if (pollAttempts > MAX_POLL_ATTEMPTS) {
                    // Timeout raggiunto: ferma il polling per non consumare batteria/rete
                    stopEmailVerificationPolling();
                    return;
                }

                FirebaseUser currentUser = auth.getCurrentUser();
                if (currentUser == null || currentUser.isAnonymous()) {
                    stopEmailVerificationPolling();
                    return;
                }

                currentUser.reload().addOnCompleteListener(task -> {
                    if (!isPollingActive) return;
                    if (task.isSuccessful()) {
                        FirebaseUser reloaded = auth.getCurrentUser();
                        if (reloaded != null) {
                            userLiveData.setValue(reloaded);
                            if (reloaded.isEmailVerified()) {
                                isEmailVerifiedLive.setValue(true);
                                successMessage.setValue(getApplication().getString(com.example.paripariapp.R.string.msg_email_confermata_successo));
                                stopEmailVerificationPolling();
                                return;
                            }
                        }
                    }
                    if (isPollingActive) {
                        pollHandler.postDelayed(this, 2500);
                    }
                });
            }
        };
        pollHandler.postDelayed(pollRunnable, 1000);
    }

    /**
     * Interrompe il polling della verifica email.
     */
    public void stopEmailVerificationPolling() {
        isPollingActive = false;
        if (pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
            pollRunnable = null;
        }
    }

    /**
     * Invia nuovamente l'email di verifica all'indirizzo dell'utente.
     * Prima controlla se l'utente ha già confermato l'email tramite reload();
     * se non è ancora confermata, invia l'email di verifica e notifica l'utente.
     */
    public void reinviaEmailVerifica() {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null && !user.isAnonymous()) {
            isLoading.setValue(true);
            user.reload().addOnCompleteListener(reloadTask -> {
                FirebaseUser reloaded = auth.getCurrentUser();
                if (reloaded != null && reloaded.isEmailVerified()) {
                    isLoading.setValue(false);
                    userLiveData.setValue(reloaded);
                    isEmailVerifiedLive.setValue(true);
                    successMessage.setValue(getApplication().getString(com.example.paripariapp.R.string.msg_email_confermata_successo));
                    return;
                }

                user.sendEmailVerification().addOnCompleteListener(task -> {
                    isLoading.setValue(false);
                    if (task.isSuccessful()) {
                        successMessage.setValue(getApplication().getString(com.example.paripariapp.R.string.msg_email_verifica_inviata));
                        startEmailVerificationPolling();
                    } else {
                        String err = task.getException() != null && task.getException().getLocalizedMessage() != null
                                ? task.getException().getLocalizedMessage()
                                : getApplication().getString(com.example.paripariapp.R.string.msg_errore_invio_email);
                        errorMessage.setValue(err);
                    }
                });
            });
        }
    }

    /**
     * Invia un'email per reimpostare la password.
     */
    public void inviaEmailRecuperoPassword(String email) {
        if (email == null || email.trim().isEmpty()) {
            errorMessage.setValue(getApplication().getString(com.example.paripariapp.R.string.error_inserisci_email));
            return;
        }
        isLoading.setValue(true);
        auth.sendPasswordResetEmail(email.trim())
                .addOnCompleteListener(task -> {
                    isLoading.setValue(false);
                    if (task.isSuccessful()) {
                        successMessage.setValue(getApplication().getString(com.example.paripariapp.R.string.msg_password_reset_inviata));
                    } else {
                        String err = task.getException() != null && task.getException().getLocalizedMessage() != null
                                ? task.getException().getLocalizedMessage()
                                : getApplication().getString(com.example.paripariapp.R.string.msg_errore_invio_email);
                        errorMessage.setValue(err);
                    }
                });
    }

    /**
     * Aggiorna l'indirizzo email dell'utente.
     * Richiede la password attuale per la riautenticazione di sicurezza.
     */
    public void modificaEmail(String nuovaEmail, String passwordAttuale) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || user.isAnonymous()) {
            return;
        }
        String currentEmail = user.getEmail();
        if (currentEmail != null && currentEmail.equalsIgnoreCase(nuovaEmail.trim())) {
            errorMessage.setValue(getApplication().getString(com.example.paripariapp.R.string.error_email_uguale));
            return;
        }
        if (passwordAttuale == null || passwordAttuale.trim().isEmpty()) {
            errorMessage.setValue(getApplication().getString(com.example.paripariapp.R.string.error_password_vuota));
            return;
        }

        isLoading.setValue(true);
        AuthCredential credential = EmailAuthProvider.getCredential(currentEmail != null ? currentEmail : "", passwordAttuale);
        user.reauthenticate(credential).addOnCompleteListener(reauthTask -> {
            if (!reauthTask.isSuccessful()) {
                isLoading.setValue(false);
                String err = reauthTask.getException() != null && reauthTask.getException().getLocalizedMessage() != null
                        ? reauthTask.getException().getLocalizedMessage()
                        : getApplication().getString(com.example.paripariapp.R.string.error_password_vuota);
                errorMessage.setValue(err);
                return;
            }

            // Invia verifica per aggiornamento email (Firebase 20.0.0+)
            user.verifyBeforeUpdateEmail(nuovaEmail.trim()).addOnCompleteListener(updateTask -> {
                isLoading.setValue(false);
                if (updateTask.isSuccessful()) {
                    // Aggiorna anche Firestore
                    Map<String, Object> update = new HashMap<>();
                    update.put("email", nuovaEmail.trim());
                    update.put("updatedAt", FieldValue.serverTimestamp());
                    firestore.collection("users").document(user.getUid())
                            .update(update)
                            .addOnFailureListener(e -> Log.w(TAG, "Aggiornamento email su Firestore fallito: " + e.getMessage()));

                    successMessage.setValue(getApplication().getString(com.example.paripariapp.R.string.msg_email_modifica_inviata));
                } else {
                    String err = updateTask.getException() != null && updateTask.getException().getLocalizedMessage() != null
                            ? updateTask.getException().getLocalizedMessage()
                            : getApplication().getString(com.example.paripariapp.R.string.msg_errore_invio_email);
                    errorMessage.setValue(err);
                }
            });
        });
    }

    /**
     * Disconnessione: effettua il logout e ripristina una sessione anonima ospite.
     */
    public void logout() {
        stopEmailVerificationPolling();
        auth.signOut();
        auth.signInAnonymously();
        successMessage.setValue(getApplication().getString(com.example.paripariapp.R.string.msg_logout_ok));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopEmailVerificationPolling();
        if (authListener != null) {
            auth.removeAuthStateListener(authListener);
        }
    }
}
