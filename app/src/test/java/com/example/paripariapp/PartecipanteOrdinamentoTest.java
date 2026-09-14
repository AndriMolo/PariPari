package com.example.paripariapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.Scheda;
import com.example.paripariapp.data.model.SyncStatus;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Test unitario per verificare il posizionamento del proprietario della scheda sempre in cima alla lista.
 */
public class PartecipanteOrdinamentoTest {

    @Test
    public void proprietarioSempreInCima_conIdPartecipante() {
        String schedaId = "scheda-test-1";
        String proprietarioId = "p3-nico";

        Scheda scheda = new Scheda(
                schedaId, "Vacanze", "Viaggio", "EUR",
                proprietarioId, System.currentTimeMillis(), System.currentTimeMillis(), SyncStatus.SYNCED
        );

        List<Partecipante> partecipanti = new ArrayList<>();
        partecipanti.add(new Partecipante("p1-andri", schedaId, "Andri", "andri@test.com", SyncStatus.SYNCED));
        partecipanti.add(new Partecipante("p2-lore", schedaId, "Lore", "lore@test.com", SyncStatus.SYNCED));
        partecipanti.add(new Partecipante("p3-nico", schedaId, "Nico", "nico@test.com", SyncStatus.SYNCED));

        // Inizialmente Nico è al terzo posto (indice 2)
        assertEquals("p1-andri", partecipanti.get(0).getId());
        assertEquals("p2-lore", partecipanti.get(1).getId());
        assertEquals("p3-nico", partecipanti.get(2).getId());

        // Riordina posizionando il proprietario in cima
        List<Partecipante> ordinati = Partecipante.ordinaConProprietarioInCima(scheda, partecipanti, null);

        assertNotNull(ordinati);
        assertEquals(3, ordinati.size());

        // Il proprietario Nico deve essere all'indice 0 (in cima)
        assertEquals("p3-nico", ordinati.get(0).getId());
        assertEquals("Nico", ordinati.get(0).getNome());

        // Gli altri partecipanti mantengono il loro ordine relativo
        assertEquals("p1-andri", ordinati.get(1).getId());
        assertEquals("p2-lore", ordinati.get(2).getId());
    }

    @Test
    public void proprietarioGiaInCima_mantieneOrdine() {
        String schedaId = "scheda-test-2";
        String proprietarioId = "p1-andri";

        Scheda scheda = new Scheda(
                schedaId, "Cena", "", "EUR",
                proprietarioId, System.currentTimeMillis(), System.currentTimeMillis(), SyncStatus.SYNCED
        );

        List<Partecipante> partecipanti = new ArrayList<>();
        partecipanti.add(new Partecipante("p1-andri", schedaId, "Andri", "andri@test.com", SyncStatus.SYNCED));
        partecipanti.add(new Partecipante("p2-lore", schedaId, "Lore", "lore@test.com", SyncStatus.SYNCED));
        partecipanti.add(new Partecipante("p3-nico", schedaId, "Nico", "nico@test.com", SyncStatus.SYNCED));

        List<Partecipante> ordinati = Partecipante.ordinaConProprietarioInCima(scheda, partecipanti, null);

        assertEquals("p1-andri", ordinati.get(0).getId());
        assertEquals("p2-lore", ordinati.get(1).getId());
        assertEquals("p3-nico", ordinati.get(2).getId());
    }

    @Test
    public void trovaProprietario_identificaCorrettamente() {
        String schedaId = "scheda-test-3";
        Scheda scheda = new Scheda(
                schedaId, "Casa", "", "EUR",
                "p2-lore", System.currentTimeMillis(), System.currentTimeMillis(), SyncStatus.SYNCED
        );

        List<Partecipante> partecipanti = new ArrayList<>();
        partecipanti.add(new Partecipante("p1-andri", schedaId, "Andri", null, SyncStatus.SYNCED));
        partecipanti.add(new Partecipante("p2-lore", schedaId, "Lore", null, SyncStatus.SYNCED));

        Partecipante proprietario = Partecipante.trovaProprietario(scheda, partecipanti, null);
        assertNotNull(proprietario);
        assertEquals("p2-lore", proprietario.getId());
        assertEquals("Lore", proprietario.getNome());
    }

    @Test
    public void gestioneListaVuotaESingola() {
        List<Partecipante> vuota = new ArrayList<>();
        List<Partecipante> ordVuota = Partecipante.ordinaConProprietarioInCima(null, vuota, null);
        assertEquals(0, ordVuota.size());

        assertNull(Partecipante.trovaProprietario(null, vuota, null));

        List<Partecipante> singolo = new ArrayList<>();
        singolo.add(new Partecipante("p1", "s1", "Solo", null, SyncStatus.SYNCED));
        List<Partecipante> ordSingolo = Partecipante.ordinaConProprietarioInCima(null, singolo, null);
        assertEquals(1, ordSingolo.size());
        assertEquals("p1", ordSingolo.get(0).getId());
    }

    @Test
    public void testPulisciNome() {
        assertEquals("Nico", Partecipante.pulisciNome("Nico (io)"));
        assertEquals("Andri", Partecipante.pulisciNome("Andri (me)"));
        assertEquals("Marco", Partecipante.pulisciNome("Marco (IO)"));
        assertEquals("Giulia", Partecipante.pulisciNome("Giulia (Me)"));
        assertEquals("Lore", Partecipante.pulisciNome("Lore"));
        assertEquals("io", Partecipante.pulisciNome("io"));
        assertEquals("", Partecipante.pulisciNome(null));
        assertEquals("", Partecipante.pulisciNome("   "));
    }
}
