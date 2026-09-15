const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");

admin.initializeApp();

/**
 * Cloud Function scatenata quando una nuova spesa o rimborso viene creata su Firestore:
 * groups/{groupId}/expenses/{expenseId}
 * Invia una notifica push ad alta priorità a tutti i partecipanti registrati,
 * escludendo chi ha registrato la spesa.
 */
exports.notificaNuovaSpesa = onDocumentCreated("groups/{groupId}/expenses/{expenseId}", async (event) => {
    const snap = event.data;
    if (!snap) return;

    const spesa = snap.data();
    const groupId = event.params.groupId;
    const expenseId = event.params.expenseId;

    const titoloSpesa = spesa.titolo || "Nuova spesa";
    const importoVal = spesa.importo != null ? Number(spesa.importo).toFixed(2) : "0.00";
    const valuta = spesa.valuta || "EUR";
    const pagatoDaId = spesa.pagatoDaId || "";
    const isRimborso = (spesa.categoria && spesa.categoria.toLowerCase() === "rimborso") || (spesa.tipo === 1);

    const db = admin.firestore();

    try {
        // 1. Recupero titolo del gruppo
        const groupDoc = await db.collection("groups").doc(groupId).get();
        const groupName = groupDoc.exists && groupDoc.data().titolo ? groupDoc.data().titolo : "Gruppo";

        // 2. Recupero tutti i partecipanti della scheda
        const participantsSnap = await db.collection("groups").doc(groupId).collection("participants").get();
        
        let nomePagatore = "Un partecipante";
        const recipientUserIds = new Set();

        participantsSnap.forEach((doc) => {
            const p = doc.data();
            if (doc.id === pagatoDaId) {
                nomePagatore = p.nome || nomePagatore;
            } else if (p.userId) {
                recipientUserIds.add(p.userId);
            }
        });

        if (recipientUserIds.size === 0) {
            console.log(`Nessun partecipante con account autenticato (userId) trovato nel gruppo ${groupId}.`);
            return;
        }

        // 3. Raccoglie i token FCM per ciascun utente destinatario
        const tokens = [];
        for (const uid of recipientUserIds) {
            const tokensSnap = await db.collection("users").doc(uid).collection("fcm_tokens").get();
            tokensSnap.forEach((tDoc) => {
                if (tDoc.id) {
                    tokens.push(tDoc.id);
                }
            });
        }

        if (tokens.length === 0) {
            console.log(`Nessun dispositivo FCM registrato per i membri del gruppo ${groupId}.`);
            return;
        }

        // 4. Configurazione messaggio
        const notifTitle = isRimborso ? `Rimborso in "${groupName}"` : `Nuova spesa in "${groupName}"`;
        const notifBody = isRimborso
            ? `${nomePagatore} ha saldato ${importoVal} ${valuta}`
            : `${nomePagatore} ha aggiunto "${titoloSpesa}" (${importoVal} ${valuta})`;

        const message = {
            tokens: tokens,
            notification: {
                title: notifTitle,
                body: notifBody
            },
            data: {
                groupId: groupId,
                expenseId: expenseId,
                title: notifTitle,
                body: notifBody
            },
            android: {
                priority: "high",
                notification: {
                    channelId: "paripari_spese_channel",
                    sound: "default",
                    defaultVibrateTimings: true
                }
            }
        };

        const response = await admin.messaging().sendEachForMulticast(message);
        console.log(`FCM broadcast completato per gruppo ${groupId}: ${response.successCount} riusciti, ${response.failureCount} falliti.`);

    } catch (err) {
        console.error("Errore nell'invio della notifica push FCM:", err);
    }
});
