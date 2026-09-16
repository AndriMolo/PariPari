package com.example.paripariapp.service;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.paripariapp.data.local.AppDatabase;
import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.Scheda;
import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.data.model.SpesaPartecipante;
import com.example.paripariapp.data.model.SyncStatus;
import com.example.paripariapp.data.repository.UserPreferencesRepository;
import com.example.paripariapp.util.CategoriaUtil;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Worker in background eseguito periodicamente da Android WorkManager
 * quando l'applicazione è chiusa o in background.
 * Controlla su Firestore se sono state aggiunte nuove spese da altri partecipanti
 * e mostra la relativa notifica nativa di sistema.
 * 
 * Segue i golden standard Android:
 * - Nessun leak di memoria (usa ApplicationContext, zero riferimenti ad Activity/View).
 * - Esecuzione interamente in thread di background fornito da Worker.
 * - Sincronizzazione atomica con il database Room locale per prevenire notifiche duplicate.
 */
public class SyncNotificheWorker extends Worker {

    private static final String TAG = "SyncNotificheWorker";
    private static final long TIMEOUT_SECONDS = 15L;

    public SyncNotificheWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || currentUser.isAnonymous()) {
            Log.d(TAG, "Nessun utente registrato autenticato: worker completato senza azioni");
            return Result.success();
        }

        Context appContext = getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(appContext);
        UserPreferencesRepository prefs = UserPreferencesRepository.getInstance(appContext);
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();

        try {
            List<Scheda> schede = db.schedaDao().getAllSchedeSync();
            if (schede == null || schede.isEmpty()) {
                Log.d(TAG, "Nessuna scheda presente in locale: worker completato");
                return Result.success();
            }

            long adesso = System.currentTimeMillis();

            for (Scheda scheda : schede) {
                String schedaId = scheda.getId();
                long lastChecked = prefs.getLastNotificationCheckTime(schedaId);

                // Se è la prima volta che eseguiamo il controllo per questa scheda,
                // impostiamo il timestamp ad adesso per evitare di notificare spese storiche già viste.
                if (lastChecked == 0L) {
                    prefs.setLastNotificationCheckTime(schedaId, adesso);
                    continue;
                }

                // Recupera le spese remote del gruppo
                QuerySnapshot expensesSnap = Tasks.await(
                        firestore.collection("groups").document(schedaId)
                                .collection("expenses")
                                .get(),
                        TIMEOUT_SECONDS, TimeUnit.SECONDS
                );

                if (expensesSnap != null && !expensesSnap.isEmpty()) {
                    List<Partecipante> partGroup = db.partecipanteDao().getPartecipantiBySchedaSync(schedaId);
                    String myPartId = Partecipante.findCurrentUserId(partGroup, currentUser, prefs, schedaId);

                    for (DocumentSnapshot eDoc : expensesSnap.getDocuments()) {
                        String eId = eDoc.getId();
                        String titolo = eDoc.getString("titolo");
                        Double importo = eDoc.getDouble("importo");
                        String valuta = eDoc.getString("valuta");
                        Long dataSpesa = eDoc.getLong("dataSpesa");
                        String categoria = eDoc.getString("categoria");
                        String pagatoDaId = eDoc.getString("pagatoDaId");
                        String scontrinoUrl = eDoc.getString("scontrinoUrl");
                        String scontrinoJson = eDoc.getString("scontrinoJson");

                        if (titolo == null || importo == null) continue;

                        // Verifica se la spesa è già salvata nel DB Room locale
                        Spesa spesaEsistente = db.spesaDao().getSpesaByIdSync(eId);
                        if (spesaEsistente == null) {
                            // Nuova spesa non presente in locale!
                            Spesa nuovaSpesa = new Spesa(
                                    eId,
                                    schedaId,
                                    titolo,
                                    importo,
                                    valuta != null ? valuta : "EUR",
                                    dataSpesa != null ? dataSpesa : adesso,
                                    categoria != null ? categoria : "Generale",
                                    pagatoDaId != null ? pagatoDaId : "",
                                    scontrinoUrl,
                                    SyncStatus.SYNCED
                            );
                            nuovaSpesa.setScontrinoJson(scontrinoJson);
                            db.spesaDao().insert(nuovaSpesa);

                            // Scarica anche le quote per consistenza DB locale
                            try {
                                QuerySnapshot sharesSnap = Tasks.await(
                                        eDoc.getReference().collection("shares").get(),
                                        TIMEOUT_SECONDS, TimeUnit.SECONDS
                                );
                                if (sharesSnap != null && !sharesSnap.isEmpty()) {
                                    List<SpesaPartecipante> quote = new ArrayList<>();
                                    for (DocumentSnapshot sDoc : sharesSnap.getDocuments()) {
                                        Double quota = sDoc.getDouble("quota");
                                        Double quotaPagata = sDoc.getDouble("quotaPagata");
                                        if (quota != null) {
                                            quote.add(new SpesaPartecipante(
                                                    eId,
                                                    sDoc.getId(),
                                                    quota,
                                                    quotaPagata != null ? quotaPagata : 0.0,
                                                    SyncStatus.SYNCED
                                            ));
                                        }
                                    }
                                    if (!quote.isEmpty()) {
                                        db.spesaDao().insertQuote(quote);
                                    }
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "Download quote per spesa " + eId + " fallito: " + e.getMessage());
                            }

                            // Mostra la notifica se la spesa non è stata inserita/pagata dall'utente stesso
                            boolean sonoIo = (myPartId != null && myPartId.equals(pagatoDaId));
                            if (!sonoIo) {
                                String nomeGruppo = (scheda.getTitolo() != null) ? scheda.getTitolo() : "Gruppo";

                                Partecipante pPagante = null;
                                if (pagatoDaId != null && !pagatoDaId.isEmpty() && partGroup != null) {
                                    for (Partecipante p : partGroup) {
                                        if (p.getId().equals(pagatoDaId)) {
                                            pPagante = p;
                                            break;
                                        }
                                    }
                                }
                                String nomePagatore = (pPagante != null && pPagante.getNome() != null)
                                        ? pPagante.getNome() : null;

                                boolean isRimborso = CategoriaUtil.isCategoriaSaldi(categoria);
                                String valutaStr = (valuta != null) ? valuta : "EUR";
                                String importoFmt = String.format(Locale.getDefault(), "%.2f %s", importo, valutaStr);

                                String titoloNotifica;
                                String corpoNotifica;

                                if (isRimborso) {
                                    titoloNotifica = "Pagamento saldato in \"" + nomeGruppo + "\"";
                                    corpoNotifica = importoFmt + " da " + (nomePagatore != null ? nomePagatore : "un partecipante") + " pagati.";
                                } else {
                                    titoloNotifica = "Nuova spesa in \"" + nomeGruppo + "\"";
                                    corpoNotifica = titolo + ": " + importoFmt + (nomePagatore != null ? " da " + nomePagatore : "");
                                }

                                PariPariMessagingService.mostraNotificaNativa(appContext, titoloNotifica, corpoNotifica, schedaId);
                                Log.i(TAG, "Notifica generata con successo da background per: " + titolo);
                            }
                        }
                    }
                }

                // Aggiorna il timestamp dell'ultimo controllo per questa scheda
                prefs.setLastNotificationCheckTime(schedaId, adesso);
            }

            return Result.success();

        } catch (Exception e) {
            Log.w(TAG, "Errore durante il ciclo di controllo notifiche in background: " + e.getMessage());
            return Result.retry();
        }
    }
}
