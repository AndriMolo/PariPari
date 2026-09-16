package com.example.paripariapp;

import static org.junit.Assert.*;

import com.example.paripariapp.util.CategoriaUtil;
import com.example.paripariapp.util.ImportatoreCsvUtil;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public class ImportatoreCsvUtilTest {

    @Test
    public void testPariPariFormatStandardImport() {
        String csvContent = "\ufeff# PARIPARI_EXPORT_VERSION;1.0\n" +
                "# GRUPPO;Vacanze Mare\n" +
                "# VALUTA;EUR\n" +
                "# MEMBRI;Mario;Luigi;Peach\n" +
                "#\n" +
                "Data;Tipo;Descrizione;Categoria;Importo;Valuta;PagatoDa;RipartizioneQuote\n" +
                "15/08/2026 20:30;Spesa;\"Cena Pesce\";\"Ristorante\";90.00;EUR;\"Mario\";\"Mario:30.00|Luigi:30.00|Peach:30.00\"\n" +
                "16/08/2026 11:00;Spesa;\"Ombrellone\";\"Svago\";45.50;EUR;\"Luigi\";\"Mario:15.17|Luigi:15.17|Peach:15.16\"\n";

        ByteArrayInputStream is = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));
        ImportatoreCsvUtil.RisultatoImportazione res = ImportatoreCsvUtil.analizzaCsv(is, "scheda-test-1");

        assertNotNull(res);
        assertEquals("Vacanze Mare", res.nomeScheda);
        assertEquals("EUR", res.valuta);
        assertEquals(3, res.partecipanti.size());
        assertEquals(2, res.speseConQuote.size());

        // Verifica prima spesa
        ImportatoreCsvUtil.SpesaConQuote spesa1 = res.speseConQuote.get(0);
        assertEquals("Cena Pesce", spesa1.spesa.getTitolo());
        assertEquals("Ristorante", spesa1.spesa.getCategoria());
        assertEquals(90.0, spesa1.spesa.getImporto(), 0.001);
        assertEquals(3, spesa1.quote.size());

        // Verifica seconda spesa
        ImportatoreCsvUtil.SpesaConQuote spesa2 = res.speseConQuote.get(1);
        assertEquals("Ombrellone", spesa2.spesa.getTitolo());
        assertEquals(45.50, spesa2.spesa.getImporto(), 0.001);
    }

    @Test
    public void testCommaSeparatedWithRimborso() {
        String csvContent = "# GRUPPO,Weekend Montagna\n" +
                "# VALUTA,USD\n" +
                "# MEMBRI,Alice,Bob\n" +
                "Date,Type,Description,Category,Amount,Currency,PaidBy,Shares\n" +
                "2026-09-01 10:00,Spesa,\"Noleggio Sci\",\"Svago\",100.00,USD,\"Alice\",\"Alice:50.00|Bob:50.00\"\n" +
                "2026-09-02 18:00,Rimborso,\"Rimborso Alice\",\"Saldi\",50.00,USD,\"Bob\",\"\"\n";

        ByteArrayInputStream is = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));
        ImportatoreCsvUtil.RisultatoImportazione res = ImportatoreCsvUtil.analizzaCsv(is, "scheda-test-2");

        assertNotNull(res);
        assertEquals("Weekend Montagna", res.nomeScheda);
        assertEquals("USD", res.valuta);
        assertEquals(2, res.partecipanti.size());
        assertEquals(2, res.speseConQuote.size());

        // Rimborso categorizzato correttamente
        ImportatoreCsvUtil.SpesaConQuote rimborso = res.speseConQuote.get(1);
        assertTrue(CategoriaUtil.isCategoriaSaldi(rimborso.spesa.getCategoria()));
        assertEquals(50.0, rimborso.spesa.getImporto(), 0.001);
    }

    @Test
    public void testEscapedQuotesAndAutoShares() {
        String csvContent = "Data;Descrizione;Importo;PagatoDa\n" +
                "10/05/2026;\"Pizza \"\"Speciale\"\" con funghi\";30.00;\"Marco\"\n";

        ByteArrayInputStream is = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));
        ImportatoreCsvUtil.RisultatoImportazione res = ImportatoreCsvUtil.analizzaCsv(is, "scheda-test-3");

        assertNotNull(res);
        assertEquals(1, res.speseConQuote.size());
        assertEquals("Pizza \"Speciale\" con funghi", res.speseConQuote.get(0).spesa.getTitolo());
        assertEquals(30.0, res.speseConQuote.get(0).spesa.getImporto(), 0.001);
        // Poiché Marco è l'unico membro rilevato, la quota è assegnata a Marco
        assertEquals(1, res.speseConQuote.get(0).quote.size());
        assertEquals(30.0, res.speseConQuote.get(0).quote.get(0).getQuota(), 0.001);
    }
}
