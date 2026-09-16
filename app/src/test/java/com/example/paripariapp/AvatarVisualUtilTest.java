package com.example.paripariapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.paripariapp.util.AvatarVisualUtil;

import org.junit.Test;

/**
 * Test unitari per {@link AvatarVisualUtil}.
 * Verifica il corretto parsing delle stringhe avatar a costo zero:
 * - Emoji + Colore (es. "emoji:🍕:#E64A19")
 * - Iniziale + Colore (es. "initial:N:#673AB7")
 * - URL Foto Google (es. "https://lh3.googleusercontent.com/...")
 * - Fallback automatico su iniziale del nome
 */
public class AvatarVisualUtilTest {

    @Test
    public void testParseEmojiConColore() {
        AvatarVisualUtil.AvatarConfig config = AvatarVisualUtil.parse("emoji:🍕:#E64A19", "Mario");
        assertNotNull(config);
        assertTrue(config.isEmoji);
        assertFalse(config.isInitial);
        assertFalse(config.isImage);
        assertEquals("🍕", config.text);
    }

    @Test
    public void testParseInizialeConColore() {
        AvatarVisualUtil.AvatarConfig config = AvatarVisualUtil.parse("initial:M:#673AB7", "Mario");
        assertNotNull(config);
        assertFalse(config.isEmoji);
        assertTrue(config.isInitial);
        assertFalse(config.isImage);
        assertEquals("M", config.text);
    }

    @Test
    public void testParseFotoGoogleGratuita() {
        String googleUrl = "https://lh3.googleusercontent.com/a/ACg8ocK123456789";
        AvatarVisualUtil.AvatarConfig config = AvatarVisualUtil.parse(googleUrl, "Nico");
        assertNotNull(config);
        assertTrue(config.isImage);
        assertFalse(config.isEmoji);
        assertFalse(config.isInitial);
        assertEquals(googleUrl, config.imageUrl);
    }

    @Test
    public void testParseEmojiDiretta() {
        AvatarVisualUtil.AvatarConfig config = AvatarVisualUtil.parse("🏖️", "Gruppo Vacanze");
        assertNotNull(config);
        assertTrue(config.isEmoji);
        assertEquals("🏖️", config.text);
    }

    @Test
    public void testParseNullUsaFallbackIniziale() {
        AvatarVisualUtil.AvatarConfig config = AvatarVisualUtil.parse(null, "Chiara");
        assertNotNull(config);
        assertTrue(config.isInitial);
        assertFalse(config.isImage);
        assertEquals("C", config.text);
    }

    @Test
    public void testParseVuotoUsaFallbackIniziale() {
        AvatarVisualUtil.AvatarConfig config = AvatarVisualUtil.parse("   ", "Alessandro");
        assertNotNull(config);
        assertTrue(config.isInitial);
        assertEquals("A", config.text);
    }
}
