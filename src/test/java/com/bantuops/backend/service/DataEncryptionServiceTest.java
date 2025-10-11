package com.bantuops.backend.service;

import com.bantuops.backend.util.KeyManagementUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour le service de chiffrement des données
 */
@SpringBootTest
@TestPropertySource(properties = {
    "bantuops.encryption.key=" + DataEncryptionServiceTest.TEST_KEY,
    "bantuops.encryption.validate-on-startup=false"
})
class DataEncryptionServiceTest {

    // Clé de test générée pour les tests unitaires
    public static final String TEST_KEY = "dGVzdC1lbmNyeXB0aW9uLWtleS0yNTYtYml0cy1mb3ItdGVzdGluZw==";

    private DataEncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        encryptionService = new DataEncryptionService();
        ReflectionTestUtils.setField(encryptionService, "encryptionKeyBase64", TEST_KEY);
    }

    @Test
    void testEncryptDecryptString() {
        // Given
        String originalText = "Données sensibles à chiffrer";

        // When
        String encrypted = encryptionService.encrypt(originalText);
        String decrypted = encryptionService.decrypt(encrypted);

        // Then
        assertNotNull(encrypted);
        assertNotEquals(originalText, encrypted);
        assertEquals(originalText, decrypted);
    }

    @Test
    void testEncryptDecryptEmptyString() {
        // Given
        String emptyText = "";

        // When
        String encrypted = encryptionService.encrypt(emptyText);
        String decrypted = encryptionService.decrypt(encrypted);

        // Then
        assertEquals(emptyText, encrypted);
        assertEquals(emptyText, decrypted);
    }

    @Test
    void testEncryptDecryptNullString() {
        // Given
        String nullText = null;

        // When
        String encrypted = encryptionService.encrypt(nullText);
        String decrypted = encryptionService.decrypt(encrypted);

        // Then
        assertNull(encrypted);
        assertNull(decrypted);
    }

    @Test
    void testEncryptDecryptBytes() {
        // Given
        byte[] originalBytes = "Test data for byte encryption".getBytes();

        // When
        byte[] encrypted = encryptionService.encryptBytes(originalBytes);
        byte[] decrypted = encryptionService.decryptBytes(encrypted);

        // Then
        assertNotNull(encrypted);
        assertFalse(java.util.Arrays.equals(originalBytes, encrypted));
        assertArrayEquals(originalBytes, decrypted);
    }

    @Test
    void testEncryptionProducesUniqueResults() {
        // Given
        String text = "Same text for multiple encryptions";

        // When
        String encrypted1 = encryptionService.encrypt(text);
        String encrypted2 = encryptionService.encrypt(text);

        // Then
        assertNotEquals(encrypted1, encrypted2, "Each encryption should produce unique results due to random IV");
        
        // But both should decrypt to the same original text
        assertEquals(text, encryptionService.decrypt(encrypted1));
        assertEquals(text, encryptionService.decrypt(encrypted2));
    }

    @Test
    void testValidateEncryptionConfiguration() {
        // When
        boolean isValid = encryptionService.validateEncryptionConfiguration();

        // Then
        assertTrue(isValid);
    }

    @Test
    void testGenerateNewEncryptionKey() {
        // When
        String newKey = encryptionService.generateNewEncryptionKey();

        // Then
        assertNotNull(newKey);
        assertTrue(KeyManagementUtil.isValidEncryptionKey(newKey));
    }

    @Test
    void testClearKeyCache() {
        // Given
        encryptionService.encrypt("test"); // Initialize cache

        // When
        encryptionService.clearKeyCache();

        // Then
        // Should still work after cache clear
        String result = encryptionService.encrypt("test after cache clear");
        assertNotNull(result);
    }

    @Test
    void testEncryptionWithSenegaleseCharacters() {
        // Given
        String senegaleseText = "Données financières sénégalaises: 500 000 FCFA";

        // When
        String encrypted = encryptionService.encrypt(senegaleseText);
        String decrypted = encryptionService.decrypt(encrypted);

        // Then
        assertEquals(senegaleseText, decrypted);
    }

    @Test
    void testEncryptionWithSpecialCharacters() {
        // Given
        String specialText = "Special chars: !@#$%^&*()_+-=[]{}|;':\",./<>?";

        // When
        String encrypted = encryptionService.encrypt(specialText);
        String decrypted = encryptionService.decrypt(encrypted);

        // Then
        assertEquals(specialText, decrypted);
    }

    @Test
    void testEncryptionFailsWithInvalidKey() {
        // Given
        DataEncryptionService serviceWithInvalidKey = new DataEncryptionService();
        ReflectionTestUtils.setField(serviceWithInvalidKey, "encryptionKeyBase64", "invalid-key");

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            serviceWithInvalidKey.encrypt("test");
        });
    }
}