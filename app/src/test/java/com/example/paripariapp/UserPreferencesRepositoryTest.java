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

    @Test
    public void testApplicaOrdineSchede() {
        com.example.paripariapp.data.model.Scheda s1 = new com.example.paripariapp.data.model.Scheda("id-1", "Gruppo 1", "", "EUR", "user-1", 1000L, 1000L, com.example.paripariapp.data.model.SyncStatus.SYNCED);
        com.example.paripariapp.data.model.Scheda s2 = new com.example.paripariapp.data.model.Scheda("id-2", "Gruppo 2", "", "EUR", "user-1", 2000L, 2000L, com.example.paripariapp.data.model.SyncStatus.SYNCED);
        com.example.paripariapp.data.model.Scheda s3 = new com.example.paripariapp.data.model.Scheda("id-3", "Gruppo 3", "", "EUR", "user-1", 3000L, 3000L, com.example.paripariapp.data.model.SyncStatus.SYNCED);

        java.util.List<com.example.paripariapp.data.model.Scheda> schede = java.util.Arrays.asList(s1, s2, s3);

        // Ordine personalizzato: id-3, poi id-1, poi id-2
        java.util.List<String> customOrder = java.util.Arrays.asList("id-3", "id-1", "id-2");
        java.util.List<com.example.paripariapp.data.model.Scheda> ordinati = com.example.paripariapp.ui.viewmodel.SpeseViewModel.applicaOrdine(schede, customOrder);

        assertEquals("id-3", ordinati.get(0).getId());
        assertEquals("id-1", ordinati.get(1).getId());
        assertEquals("id-2", ordinati.get(2).getId());

        // Se un nuovo gruppo non è ancora nell'ordine salvato, deve comparire in cima
        com.example.paripariapp.data.model.Scheda sNuova = new com.example.paripariapp.data.model.Scheda("id-new", "Nuovo", "", "EUR", "user-1", 4000L, 4000L, com.example.paripariapp.data.model.SyncStatus.SYNCED);
        java.util.List<com.example.paripariapp.data.model.Scheda> conNuova = java.util.Arrays.asList(s1, s2, s3, sNuova);
        java.util.List<com.example.paripariapp.data.model.Scheda> ordinatiConNuova = com.example.paripariapp.ui.viewmodel.SpeseViewModel.applicaOrdine(conNuova, customOrder);

        assertEquals("id-new", ordinatiConNuova.get(0).getId());
        assertEquals("id-3", ordinatiConNuova.get(1).getId());
        assertEquals("id-1", ordinatiConNuova.get(2).getId());
        assertEquals("id-2", ordinatiConNuova.get(3).getId());
    }
}
