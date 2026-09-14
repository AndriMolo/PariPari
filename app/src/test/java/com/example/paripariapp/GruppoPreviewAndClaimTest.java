package com.example.paripariapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.paripariapp.data.model.MembroGruppoPreview;
import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.data.model.SpesaPartecipante;
import com.example.paripariapp.data.model.SyncStatus;
import com.example.paripariapp.data.remote.FirestoreSyncManager;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GruppoPreviewAndClaimTest {

    @Test
    public void testMembroGruppoPreview_classificazioneAutenticatoVsSostituibile() {
        MembroGruppoPreview autenticato = new MembroGruppoPreview(
                "p-real", "scheda-1", "Mario Creatore", "mario@email.com", "uid-123", null, "ATTIVO", true
        );
        MembroGruppoPreview anonimo = new MembroGruppoPreview(
                "p-offline", "scheda-1", "Giulia", null, null, null, "ATTIVO", false
        );

        assertTrue(autenticato.isAutenticato());
        assertFalse(autenticato.isSostituibile());

        assertFalse(anonimo.isAutenticato());
        assertTrue(anonimo.isSostituibile());
    }

    @Test
    public void testMembroGruppoPreview_relinkEsclusivoUserId() {
        MembroGruppoPreview mioMembro = new MembroGruppoPreview(
                "p-1", "scheda-1", "Marco", "marco@test.com", "uid-owner-123", null, "ATTIVO", true
        );
        MembroGruppoPreview mioVecchioMembro = new MembroGruppoPreview(
                "p-2", "scheda-1", "Marco Vecchio", null, null, "uid-migrated-456", "ATTIVO", false
        );
        MembroGruppoPreview altroUtente = new MembroGruppoPreview(
                "p-3", "scheda-1", "Giulia", "giulia@test.com", "uid-other-789", null, "ATTIVO", true
        );
        MembroGruppoPreview omonimoAnonimo = new MembroGruppoPreview(
                "p-4", "scheda-1", "Marco", null, null, null, "ATTIVO", false
        );

        // Match rigoroso su userId
        assertTrue(mioMembro.isMyProfile("uid-owner-123"));
        assertFalse(mioMembro.isMyProfile("uid-other-789"));
        assertFalse(mioMembro.isMyProfile(null));

        // Match su previousUserId
        assertTrue(mioVecchioMembro.isMyProfile("uid-migrated-456"));
        assertFalse(mioVecchioMembro.isMyProfile("uid-owner-123"));

        // Membro di un altro utente: NON è mio profilo
        assertFalse(altroUtente.isMyProfile("uid-owner-123"));

        // Omonimo con stesso nome ("Marco") ma userId nullo: NON è mio profilo (no euristiche di nome!)
        assertFalse(omonimoAnonimo.isMyProfile("uid-owner-123"));

        // Selezionabilità: il mio profilo è sempre selezionabile da me
        assertTrue(mioMembro.isSelezionabileDa("uid-owner-123"));
        // Il profilo di un altro NON è selezionabile da me
        assertFalse(altroUtente.isSelezionabileDa("uid-owner-123"));
        // L'anonimo libero è selezionabile (subentro)
        assertTrue(omonimoAnonimo.isSelezionabileDa("uid-owner-123"));
    }

    @Test
    public void testGruppoPreview_costruzioneEFiltraggio() {
        List<MembroGruppoPreview> membri = new ArrayList<>();
        membri.add(new MembroGruppoPreview("p1", "scheda-123", "Mario", "mario@email.com", "uid-1", null, "ATTIVO", true));
        membri.add(new MembroGruppoPreview("p2", "scheda-123", "Giulia", null, null, null, "ATTIVO", false));
        membri.add(new MembroGruppoPreview("p3", "scheda-123", "Luca", null, null, null, "ATTIVO", false));

        FirestoreSyncManager.GruppoPreview preview = new FirestoreSyncManager.GruppoPreview(
                "scheda-123",
                "Vacanza a Roma",
                "Spese condivise",
                "EUR",
                membri
        );

        assertEquals("scheda-123", preview.getGroupId());
        assertEquals("Vacanza a Roma", preview.getTitolo());
        assertEquals("EUR", preview.getValutaPredefinita());
        assertEquals(3, preview.getMembri().size());

        List<MembroGruppoPreview> sostituibili = preview.getMembriSostituibili();
        assertEquals(2, sostituibili.size());
        assertEquals("Giulia", sostituibili.get(0).getNome());
        assertEquals("Luca", sostituibili.get(1).getNome());
    }

    @Test
    public void testGruppoPreview_listaPartecipantiNullSafe() {
        FirestoreSyncManager.GruppoPreview preview = new FirestoreSyncManager.GruppoPreview(
                "scheda-999",
                "Gruppo Vuoto",
                null,
                "USD",
                null
        );

        assertNotNull(preview.getMembri());
        assertTrue(preview.getMembri().isEmpty());
        assertTrue(preview.getMembriSostituibili().isEmpty());
        assertTrue(preview.getPartecipantiDisponibili().isEmpty());
    }

    @Test
    public void testClaimPartecipante_mantieneIntegritaSpeseEQuote() {
        String offlinePartecipanteId = UUID.randomUUID().toString();
        String schedaId = "scheda-test";

        Partecipante partecipanteOffline = new Partecipante(
                offlinePartecipanteId,
                schedaId,
                "Marco",
                null,
                SyncStatus.SYNCED
        );

        Spesa spesa = Spesa.createNew(
                schedaId,
                "Cena",
                60.0,
                "EUR",
                "Ristorante",
                offlinePartecipanteId,
                null
        );

        SpesaPartecipante quota = new SpesaPartecipante(
                spesa.getId(),
                offlinePartecipanteId,
                30.0,
                SyncStatus.SYNCED
        );

        String userEmail = "marco.rossi@email.com";
        Partecipante partecipanteClaimed = new Partecipante(
                partecipanteOffline.getId(),
                partecipanteOffline.getSchedaId(),
                partecipanteOffline.getNome(),
                userEmail,
                SyncStatus.SYNCED
        );

        assertEquals(offlinePartecipanteId, partecipanteClaimed.getId());
        assertEquals(userEmail, partecipanteClaimed.getEmail());
        assertEquals(spesa.getPagatoDaId(), partecipanteClaimed.getId());
        assertEquals(quota.getPartecipanteId(), partecipanteClaimed.getId());
    }
}
