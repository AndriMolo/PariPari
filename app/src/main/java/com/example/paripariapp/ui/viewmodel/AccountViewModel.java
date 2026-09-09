package com.example.paripariapp.ui.viewmodel;

import android.app.Application;
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
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<String> successMessage = new MutableLiveData<>();

    private final FirebaseAuth.AuthStateListener authListener;

    public AccountViewModel(@NonNull Application application) {
        super(application);
        repository = PariPariRepository.getInstance(application);
        preferencesRepository = UserPreferencesRepository.getInstance(application);
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        // Listener reattivo sullo stato di autenticazione
        authListener = firebaseAuth -> {
            FirebaseUser user = firebaseAuth.getCurrentUser();
            userLiveData.setValue(user);
            boolean guest = (user == null || user.isAnonymous());
            isGuestMode.setValue(guest);
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

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<String> getSuccessMessage() {
        return successMessage;
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
                        successMessage.setValue(isNewAccount ? "Account creato con successo!" : "Profilo aggiornato!");
                    });
        });
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
                        successMessage.setValue("Accesso effettuato con successo!");
                    } else {
                        String err = task.getException() != null ? task.getException().getLocalizedMessage() : "Credenziali non valide";
                        errorMessage.setValue(err);
                    }
                });
    }

    /**
     * Disconnessione: effettua il logout e ripristina una sessione anonima ospite.
     */
    public void logout() {
        auth.signOut();
        auth.signInAnonymously();
        successMessage.setValue("Disconnesso. Sei ora in modalità Account ospite.");
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (authListener != null) {
            auth.removeAuthStateListener(authListener);
        }
    }
}
