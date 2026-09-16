package com.example.paripariapp;

import static org.junit.Assert.assertEquals;

import com.example.paripariapp.data.repository.UserPreferencesRepository;

import org.junit.Test;

public class UserPreferencesRepositoryTest {

    @Test
    public void testCleanPaypalHandle() {
        assertEquals("mario.rossi", UserPreferencesRepository.cleanPaypalHandle("mario.rossi"));
        assertEquals("mario.rossi", UserPreferencesRepository.cleanPaypalHandle("@mario.rossi"));
        assertEquals("mario.rossi", UserPreferencesRepository.cleanPaypalHandle("https://paypal.me/mario.rossi"));
        assertEquals("mario.rossi", UserPreferencesRepository.cleanPaypalHandle("paypal.me/mario.rossi/15.00EUR"));
        assertEquals("mario.rossi", UserPreferencesRepository.cleanPaypalHandle("paypal.me/mario.rossi?locale=it"));
        assertEquals("", UserPreferencesRepository.cleanPaypalHandle(""));
        assertEquals("", UserPreferencesRepository.cleanPaypalHandle(null));
    }

    @Test
    public void testCleanRevolutHandle() {
        assertEquals("mario", UserPreferencesRepository.cleanRevolutHandle("mario"));
        assertEquals("mario", UserPreferencesRepository.cleanRevolutHandle("@mario"));
        assertEquals("mario", UserPreferencesRepository.cleanRevolutHandle("https://revolut.me/mario"));
        assertEquals("mario", UserPreferencesRepository.cleanRevolutHandle("revolut.me/mario?amount=20"));
        assertEquals("", UserPreferencesRepository.cleanRevolutHandle(""));
        assertEquals("", UserPreferencesRepository.cleanRevolutHandle(null));
    }

    @Test
    public void testGeneratePaypalLink() {
        assertEquals("https://paypal.me/mario/15.50",
                UserPreferencesRepository.generatePaypalLink("mario", 15.50, "EUR"));
        assertEquals("https://paypal.me/mario/20.00",
                UserPreferencesRepository.generatePaypalLink("@mario", 20.0, "USD"));
        assertEquals("https://paypal.me/mario",
                UserPreferencesRepository.generatePaypalLink("mario", 0.0, "EUR"));
        assertEquals("",
                UserPreferencesRepository.generatePaypalLink("", 15.0, "EUR"));
    }

    @Test
    public void testGenerateRevolutLink() {
        assertEquals("https://revolut.me/mario",
                UserPreferencesRepository.generateRevolutLink("mario", 15.50, "EUR"));
        assertEquals("https://revolut.me/mario",
                UserPreferencesRepository.generateRevolutLink("@mario", 20.0, "USD"));
        assertEquals("https://revolut.me/mario",
                UserPreferencesRepository.generateRevolutLink("mario", 0.0, "EUR"));
        assertEquals("",
                UserPreferencesRepository.generateRevolutLink("", 15.0, "EUR"));
    }
}
