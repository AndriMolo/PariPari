package com.example.paripariapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.paripariapp.util.SpesaUtil;

import org.junit.Test;

public class SpesaUtilTest {

    @Test
    public void testIsPatternRimborsoTutteLeLingue() {
        // Italiano
        assertTrue(SpesaUtil.isPatternRimborso("Rimborso: da Marco a Laura"));
        assertTrue(SpesaUtil.isPatternRimborso("rimborso: da Mario a Luigi"));

        // Inglese
        assertTrue(SpesaUtil.isPatternRimborso("Reimbursement: from John to Sarah"));
        assertTrue(SpesaUtil.isPatternRimborso("reimbursement: from Alice to Bob"));

        // Spagnolo
        assertTrue(SpesaUtil.isPatternRimborso("Reembolso: de Carlos a Maria"));
        assertTrue(SpesaUtil.isPatternRimborso("reembolso: de Juan a Lucia"));

        // Francese
        assertTrue(SpesaUtil.isPatternRimborso("Remboursement: de Pierre à Claire"));
        assertTrue(SpesaUtil.isPatternRimborso("remboursement: de Paul a Jeanne"));

        // Tedesco
        assertTrue(SpesaUtil.isPatternRimborso("Rückzahlung: von Hans an Anna"));
        assertTrue(SpesaUtil.isPatternRimborso("rückzahlung: von Max an Lisa"));

        // Casi negativi (spese ordinarie o formati non validi)
        assertFalse(SpesaUtil.isPatternRimborso("Pizza con amici"));
        assertFalse(SpesaUtil.isPatternRimborso("Spesa supermercato"));
        assertFalse(SpesaUtil.isPatternRimborso("Rimborso"));
        assertFalse(SpesaUtil.isPatternRimborso(""));
        assertFalse(SpesaUtil.isPatternRimborso(null));
    }

    @Test
    public void testEstraiMittenteEDestinatario() {
        // Italiano
        String[] it = SpesaUtil.estraiMittenteEDestinatario("Rimborso: da Marco Rossi a Laura Bianchi");
        assertNotNull(it);
        assertEquals("Marco Rossi", it[0]);
        assertEquals("Laura Bianchi", it[1]);

        // Inglese
        String[] en = SpesaUtil.estraiMittenteEDestinatario("Reimbursement: from John Smith to Sarah Connor");
        assertNotNull(en);
        assertEquals("John Smith", en[0]);
        assertEquals("Sarah Connor", en[1]);

        // Spagnolo
        String[] es = SpesaUtil.estraiMittenteEDestinatario("Reembolso: de Carlos Gomez a Maria Fernandez");
        assertNotNull(es);
        assertEquals("Carlos Gomez", es[0]);
        assertEquals("Maria Fernandez", es[1]);

        // Francese
        String[] fr = SpesaUtil.estraiMittenteEDestinatario("Remboursement: de Jean Valjean à Cosette");
        assertNotNull(fr);
        assertEquals("Jean Valjean", fr[0]);
        assertEquals("Cosette", fr[1]);

        // Tedesco
        String[] de = SpesaUtil.estraiMittenteEDestinatario("Rückzahlung: von Hans Gruber an Anna Schmidt");
        assertNotNull(de);
        assertEquals("Hans Gruber", de[0]);
        assertEquals("Anna Schmidt", de[1]);

        // Casi non corrispondenti
        assertNull(SpesaUtil.estraiMittenteEDestinatario("Cena aziendale"));
        assertNull(SpesaUtil.estraiMittenteEDestinatario(null));
        assertNull(SpesaUtil.estraiMittenteEDestinatario(""));
    }
}
