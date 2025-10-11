package com.bantuops.backend.util;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour l'utilitaire de gestion des clés
 */
class KeyManagementUtilTest {

    @Test
    void testGenerateEncryptionKey() {
        // When
        String key = KeyManagementUtil.generateEncryptionKey();

        // Then
        assertNotNull(key);
        assertTrue(KeyManagementUtil.isValidEncryptionKey(key));
    }

    @Test
    void testIsValidEncryptionKeyWithValidKey() {
        // Given - Generate a valid key
        String validKey = KeyManagementUtil.generateEncryptionKey();

        // When
        boolean isValid = KeyManagementUtil.isValidEncryptionKey(validKey);

        // Then
        assertTrue(isValid);
    }

    @Test
    void testIsValidEncryptionKeyWithNullKey() {
        // When
        boolean isValid = KeyManagementUtil.isValidEncryptionKey(null);

        // Then
        assertFalse(isValid);
    }

    @Test
    void testIsValidEncryptionKeyWithEmptyKey() {
        // When
        boolean isValid = KeyManagementUtil.isValidEncryptionKey("");

        // Then
        assertFalse(isValid);
    }

    @Test
    void testIsValidEncryptionKeyWithInvalidLength() {
        // Given - Create a key with wrong length (16 bytes instead of 32)
        byte[] shortKey = new byte[16];
        String invalidKey = Base64.getEncoder().encodeToString(shortKey);

        // When
        boolean isValid = KeyManagementUtil.isValidEncryptionKey(invalidKey);

        // Then
        assertFalse(isValid);
    }

    @Test
    void testIsValidEncryptionKeyWithInvalidBase64() {
        // Given
        String invalidBase64 = "This is not valid Base64!@#$";

        // When
        boolean isValid = KeyManagementUtil.isValidEncryptionKey(invalidBase64);

        // Then
        assertFalse(isValid);
    }

    @Test
    void testGenerateSalt() {
        // When
        String salt1 = KeyManagementUtil.generateSalt();
        String salt2 = KeyManagementUtil.generateSalt();

        // Then
        assertNotNull(salt1);
        assertNotNull(salt2);
        assertNotEquals(salt1, salt2, "Each salt should be unique");
        
        // Verify it's valid Base64
        assertDoesNotThrow(() -> Base64.getDecoder().decode(salt1));
        assertDoesNotThrow(() -> Base64.getDecoder().decode(salt2));
    }

    @Test
    void testValidateKeyStrengthWithStrongKey() {
        // Given
        String strongKey = KeyManagementUtil.generateEncryptionKey();

        // When
        int strength = KeyManagementUtil.validateKeyStrength(strongKey);

        // Then
        assertTrue(strength >= 70, "Generated keys should have good strength");
    }

    @Test
    void testValidateKeyStrengthWithWeakKey() {
        // Given - Create a weak key (all zeros)
        byte[] weakKeyBytes = new byte[32]; // All zeros
        String weakKey = Base64.getEncoder().encodeToString(weakKeyBytes);

        // When
        int strength = KeyManagementUtil.validateKeyStrength(weakKey);

        // Then
        assertTrue(strength <= 30, "All-zero key should have low strength");
    }

    @Test
    void testValidateKeyStrengthWithInvalidKey() {
        // Given
        String invalidKey = "invalid";

        // When
        int strength = KeyManagementUtil.validateKeyStrength(invalidKey);

        // Then
        assertEquals(0, strength);
    }

    @Test
    void testGenerateStrongEncryptionKey() {
        // Given
        int minStrength = 80;

        // When
        String strongKey = KeyManagementUtil.generateStrongEncryptionKey(minStrength);

        // Then
        assertNotNull(strongKey);
        assertTrue(KeyManagementUtil.isValidEncryptionKey(strongKey));
        assertTrue(KeyManagementUtil.validateKeyStrength(strongKey) >= minStrength);
    }

    @Test
    void testGenerateStrongEncryptionKeyWithInvalidStrength() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            KeyManagementUtil.generateStrongEncryptionKey(-1);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            KeyManagementUtil.generateStrongEncryptionKey(101);
        });
    }

    @Test
    void testWipeString() {
        // Given
        String sensitiveData = "sensitive information";

        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> KeyManagementUtil.wipeString(sensitiveData));
        assertDoesNotThrow(() -> KeyManagementUtil.wipeString(null));
    }

    @Test
    void testKeyUniqueness() {
        // When - Generate multiple keys
        String key1 = KeyManagementUtil.generateEncryptionKey();
        String key2 = KeyManagementUtil.generateEncryptionKey();
        String key3 = KeyManagementUtil.generateEncryptionKey();

        // Then - All keys should be unique
        assertNotEquals(key1, key2);
        assertNotEquals(key2, key3);
        assertNotEquals(key1, key3);
    }

    @Test
    void testValidKeyLength() {
        // Given
        String validKey = KeyManagementUtil.generateEncryptionKey();
        byte[] keyBytes = Base64.getDecoder().decode(validKey);

        // Then
        assertEquals(32, keyBytes.length, "AES-256 key should be 32 bytes");
    }

    @Test
    void testRepeatingPatternDetection() {
        // Given - Create a key with repeating pattern
        byte[] patternBytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            patternBytes[i] = (byte) (i % 4); // Repeating pattern: 0,1,2,3,0,1,2,3...
        }
        String patternKey = Base64.getEncoder().encodeToString(patternBytes);

        // When
        int strength = KeyManagementUtil.validateKeyStrength(patternKey);

        // Then
        assertTrue(strength <= 30, "Key with repeating pattern should have low strength");
    }
}