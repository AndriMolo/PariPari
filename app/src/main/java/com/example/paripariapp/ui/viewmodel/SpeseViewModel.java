package com.example.paripariapp.ui.viewmodel;

import android.app.Application;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.Scheda;
import com.example.paripariapp.data.repository.PariPariRepository;
import com.example.paripariapp.data.repository.UserPreferencesRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ViewModel per la gestione delle Schede Spese.
 * Fornisce l'elenco osservabile delle schede e i metodi per la creazione,
 * eliminazione e ripristino di una scheda.
 */
public class SpeseViewModel extends AndroidViewModel {

    private final PariPariRepository repository;
    private final UserPreferencesRepository preferencesRepository;
    private final FirebaseAuth auth;
    private final LiveData<List<Scheda>> schede;

    public SpeseViewModel(@NonNull Application application) {
        super(application);
        repository = PariPariRepository.getInstance(application);
        preferencesRepository = UserPreferencesRepository.getInstance(application);
        auth = FirebaseAuth.getInstance();
        schede = repository.getAllSchede();
    }

    public LiveData<List<Scheda>> getSchede() {
        return schede;
    }

    public String getDefaultCurrency() {
        return preferencesRepository.getDefaultCurrency();
    }

    /**
     * Crea una nuova scheda spese con la lista di partecipanti specificata.
     * Include sempre "Io" come primo partecipante se non diversamente specificato.
     *
     * @param titolo           Nome obbligatorio della scheda (es. "Vacanza", "Cena")
     * @param valuta           Valuta predefinita (se null o vuota usa la preferenza impostata)
     * @param nomiPartecipanti Lista dei nomi degli amici inseriti dall'utente
     */
    public void creaScheda(String titolo, String valuta, List<String> nomiPartecipanti) {
        if (TextUtils.isEmpty(titolo)) {
            return;
        }

        String titoloPulito = titolo.trim();
        String valutaScelta = !TextUtils.isEmpty(valuta) ? valuta.trim().toUpperCase() : preferencesRepository.getDefaultCurrency();

        FirebaseUser user = auth.getCurrentUser();
        String creatoreId = user != null ? user.getUid() : "guest";

        // Creazione dell'entità Scheda (senza descrizione, come richiesto)
        Scheda nuovaScheda = Scheda.createNew(titoloPulito, "", valutaScelta, creatoreId);

        // Creazione partecipanti: garantiamo sempre la presenza di "Io"
        List<Partecipante> partecipanti = new ArrayList<>();
        Set<String> nomiInseriti = new HashSet<>();

        String nomeIo = getApplication().getString(R.string.partecipante_io);
        partecipanti.add(Partecipante.createNew(nuovaScheda.getId(), nomeIo, user != null ? user.getEmail() : null));
        nomiInseriti.add(nomeIo.toLowerCase());

        if (nomiPartecipanti != null) {
            for (String nome : nomiPartecipanti) {
                if (!TextUtils.isEmpty(nome)) {
                    String nomeTrim = nome.trim();
                    if (!nomiInseriti.contains(nomeTrim.toLowerCase())) {
                        partecipanti.add(Partecipante.createNew(nuovaScheda.getId(), nomeTrim, null));
                        nomiInseriti.add(nomeTrim.toLowerCase());
                    }
                }
            }
        }

        // Inserimento asincrono nel DB Room (e sync cloud se connesso)
        repository.insertScheda(nuovaScheda, partecipanti);
    }

    /**
     * Elimina una scheda esistente (chiamato dallo swipe verso sinistra).
     */
    public void eliminaScheda(Scheda scheda) {
        if (scheda != null) {
            repository.deleteScheda(scheda.getId());
        }
    }

    /**
     * Ripristina la scheda eliminata (chiamato dal tasto ANNULLA della Snackbar).
     */
    public void ripristinaScheda(Scheda scheda) {
        if (scheda != null) {
            repository.insertScheda(scheda, null);
        }
    }
}