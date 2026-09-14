package com.example.paripariapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.paripariapp.util.CodiceInvitoUtil;
import com.example.paripariapp.util.QrCodeUtil;
import com.google.zxing.common.BitMatrix;

import org.junit.Test;

public class QrCodeUtilTest {

    @Test
    public void testGeneraMatriceQr_linkInvitoValido() throws Exception {
        String link = CodiceInvitoUtil.generaLinkInvito("ABC123");
        BitMatrix matrix = QrCodeUtil.generaMatriceQr(link, 512);

        assertNotNull(matrix);
        assertEquals(512, matrix.getWidth());
        assertEquals(512, matrix.getHeight());

        // Verifica che contenga sia pixel accesi che spenti (matrice valida)
        boolean hasBlack = false;
        boolean hasWhite = false;
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                if (matrix.get(x, y)) {
                    hasBlack = true;
                } else {
                    hasWhite = true;
                }
            }
        }
        assertTrue("La matrice QR deve avere moduli neri", hasBlack);
        assertTrue("La matrice QR deve avere moduli bianchi", hasWhite);
    }
}
