# Piano di Implementazione - Modifiche e Nuove Funzionalità PariPari

Questo piano descrive le modifiche richieste per l'applicazione PariPari, coprendo la gestione del database (locale prima di Firestore), l'audit della cronologia personale, il filtraggio dei pagamenti per pareggiare, la gestione multivaluta nelle spese, il contatore delle spese personali, la categoria predefinita e la nuova schermata di dettaglio spesa con le relative interazioni (click, long click, modifica, eliminazione con restituzione fondi e controllo membri usciti).

## User Review Required

> [!IMPORTANT]
> **Ordine di scrittura DB**: Per ogni operazione di scrittura (inserimento, modifica, eliminazione), il database locale Room viene aggiornato per primo, seguito dalla sincronizzazione con Firestore.
> **Eliminazione Spesa**: L'eliminazione di una spesa ricalcolerà i saldi/fondi rimborsando l'utente che aveva pagato e verificando se i partecipanti sono ancora attivi nel gruppo (se qualcuno è uscito, la spesa viene bloccata e compare il popup "questa spesa non è modificabile").

## Open Questions

Nessuna domanda aperta; i requisiti sono dettagliati.

## Proposed Changes

### Componente Dati & Repository (DB & Firestore)
- **[MODIFY] [PariPariRepository.java](file:///C:/Users/molod/PariPari/app/src/main/java/com/example/paripariapp/data/repository/PariPariRepository.java)**
  - Assicurare che tutte le operazioni di scrittura modifichino prima il DB locale e poi Firestore.
  - Gestire la logica di eliminazione della spesa (restituzione fondi a chi ha pagato e controllo se qualche partecipante è uscito dal gruppo).

### Componente UI & Interfaccia Spese
- **[MODIFY] [DettaglioSchedaFragment.java](file:///C:/Users/molod/PariPari/app/src/main/java/com/example/paripariapp/ui/view/DettaglioSchedaFragment.java)**
  - Aggiungere il contatore "Le mie spese" sotto il Totale delle Spese di gruppo.
  - Gestire il click sulla spesa per aprire la nuova schermata di dettaglio spesa (titolo, giorno pagamento, pagato da in rosso, partecipanti con quote).
  - Gestire il long click sulla spesa per mostrare il popup (Modifica / Elimina).
  - Implementare la logica di controllo eliminazione (controllo membri usciti -> popup "questa spesa non è modificabile", altrimenti elimina e ridà i soldi).
  - Filtrare "Pagamenti per pareggiare" affinché mostrino solo quelli corrispondenti all'utente che deve pagare o rimborsare.

- **[MODIFY] [SpesaAdapter.java](file:///C:/Users/molod/PariPari/app/src/main/java/com/example/paripariapp/ui/view/SpesaAdapter.java)**
  - Supportare click e long click sugli elementi della lista spese.

- **[MODIFY] [SpesaUiHelper.java](file:///C:/Users/molod/PariPari/app/src/main/java/com/example/paripariapp/ui/view/SpesaUiHelper.java)**
  - Gestire la visualizzazione multivaluta: se la valuta della spesa è diversa da quella del gruppo, mostrare spese e contributi in entrambe le valute (sia nella lista che nel dettaglio spesa).
  - Impostare la categoria predefinita a "Altro" (`CAT_ALTRO`) ovunque sia necessario.

- **[NEW] [DettaglioSpesaBottomSheet.java](file:///C:/Users/molod/PariPari/app/src/main/java/com/example/paripariapp/ui/view/DettaglioSpesaBottomSheet.java)**
  - Schermata carina (BottomSheet o Dialog) che mostra: Titolo, Giorno del Pagamento, Pagato Da (con i soldi pagati in colore rosso) e Partecipanti con le rispettive quote in denaro (ed eventuale doppia valuta se diversa dal gruppo).

### Componente Audit & Storico
- **[MODIFY] Audit Cronologia (History)**: Verificare e garantire la consistenza degli eventi tracciati nel log storico delle attività personali.

## Verification Plan

### Automated Tests
- Esecuzione dei test unitari esistenti (`gradle_build("app:testDebugUnitTest")`).
- Creazione di unit test per la logica di calcolo delle spese personali, valuta multipla e filtri di pareggio.

### Manual Verification
- Verifica su emulatore del flusso completo: inserimento spesa (anche in valuta estera), visualizzazione del contatore "Le mie spese", controllo "Pagamenti per pareggiare", click e long click su spesa con dialog dettaglio, modifica ed eliminazione con restituzione fondi e blocco se un utente è uscito.
