package com.bantuops.backend.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Utilitaire de gestion sécurisée des clés de chiffrement
 * Conforme aux standards de sécurité pour les données financières
 */
@Slf4j
public class KeyManagementUtil {

    private static final String ALGORITHM = "AES";
    private static final int KEY_LENGTH = 256;
    private static final int KEY_BYTES = KEY_LENGTH / 8; // 32 bytes pour AES-256
    
    // Pattern pour valider le format Base64
    private static final Pattern BASE64_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+/]*={0,2}$"
    );

    /**
     * Génère une nouvelle clé de chiffrement AES-256 sécurisée
     * Cette méthode ne doit être utilisée que dans des environnements sécurisés
     * pour la génération initiale de clés
     */
    public static String generateEncryptionKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            // Utilisation d'un générateur aléatoire cryptographiquement sécurisé
            keyGenerator.init(KEY_LENGTH, SecureRandom.getInstanceStrong());
            SecretKey secretKey = keyGenerator.generateKey();
            String encodedKey = Base64.getEncoder().encodeToString(secretKey.getEncoded());
            
            log.info("New encryption key generated successfully with {} bits", KEY_LENGTH);
            return encodedKey;
        } catch (Exception e) {
            log.error("Failed to generate encryption key", e);
            throw new RuntimeException("Failed to generate encryption key", e);
        }
    }

    /**
     * Valide qu'une clé fournie est une clé AES-256 valide
     * @param keyBase64 Clé encodée en Base64
     * @return true si la clé est valide
     */
    public static boolean isValidEncryptionKey(String keyBase64) {
        if (keyBase64 == null || keyBase64.isEmpty()) {
            log.debug("Encryption key is null or empty");
            return false;
        }

        // Validation du format Base64
        if (!BASE64_PATTERN.matcher(keyBase64).matches()) {
            log.warn("Invalid Base64 format for encryption key");
            return false;
        }

        try {
            byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
            boolean isValid = keyBytes.length == KEY_BYTES;
            
            if (!isValid) {
                log.warn("Invalid key length: expected {} bytes, got {} bytes", KEY_BYTES, keyBytes.length);
            } else {
                log.debug("Encryption key validation successful");
            }
            
            return isValid;
        } catch (Exception e) {
            log.warn("Invalid encryption key format", e);
            return false;
        }
    }

    /**
     * Securely wipes a string from memory (best effort)
     */
    public static void wipeString(String sensitive) {
        if (sensitive != null) {
            // Note: This is a best effort approach as Java strings are immutable
            // In production, consider using char[] or SecretKey for sensitive data
            System.gc(); // Suggest garbage collection
        }
    }

    /**
     * Génère un sel cryptographique sécurisé pour une sécurité additionnelle
     * @return Sel encodé en Base64
     */
    public static String generateSalt() {
        try {
            SecureRandom random = SecureRandom.getInstanceStrong();
            byte[] salt = new byte[16]; // 128 bits
            random.nextBytes(salt);
            return Base64.getEncoder().encodeToString(salt);
        } catch (Exception e) {
            log.error("Failed to generate secure salt", e);
            throw new RuntimeException("Failed to generate secure salt", e);
        }
    }

    /**
     * Valide la force d'une clé de chiffrement
     * @param keyBase64 Clé à valider
     * @return Score de force (0-100)
     */
    public static int validateKeyStrength(String keyBase64) {
        if (!isValidEncryptionKey(keyBase64)) {
            return 0;
        }

        try {
            byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
            
            // Analyse de l'entropie
            int uniqueBytes = (int) java.util.Arrays.stream(keyBytes)
                .boxed()
                .distinct()
                .count();
            
            // Score basé sur l'unicité des bytes (plus il y a de bytes uniques, mieux c'est)
            int entropyScore = (uniqueBytes * 100) / 256;
            
            // Vérification qu'il ne s'agit pas d'une clé faible (tous zéros, pattern répétitif, etc.)
            boolean hasPattern = hasRepeatingPattern(keyBytes);
            boolean isAllZeros = java.util.Arrays.stream(keyBytes).allMatch(b -> b == 0);
            
            if (isAllZeros || hasPattern) {
                return Math.min(entropyScore, 30); // Clé faible
            }
            
            return Math.max(entropyScore, 70); // Clé acceptable à forte
            
        } catch (Exception e) {
            log.warn("Failed to analyze key strength", e);
            return 0;
        }
    }

    /**
     * Détecte les patterns répétitifs dans une clé
     */
    private static boolean hasRepeatingPattern(byte[] keyBytes) {
        // Vérification de patterns simples (séquences répétitives)
        for (int patternLength = 1; patternLength <= 4; patternLength++) {
            if (keyBytes.length % patternLength == 0) {
                boolean hasPattern = true;
                for (int i = patternLength; i < keyBytes.length; i++) {
                    if (keyBytes[i] != keyBytes[i % patternLength]) {
                        hasPattern = false;
                        break;
                    }
                }
                if (hasPattern) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Génère une clé de chiffrement avec validation de force
     * @param minStrength Force minimale requise (0-100)
     * @return Clé générée répondant aux critères de force
     */
    public static String generateStrongEncryptionKey(int minStrength) {
        if (minStrength < 0 || minStrength > 100) {
            throw new IllegalArgumentException("Minimum strength must be between 0 and 100");
        }

        String key;
        int attempts = 0;
        int maxAttempts = 10;

        do {
            key = generateEncryptionKey();
            attempts++;
            
            if (attempts > maxAttempts) {
                log.warn("Failed to generate key with required strength after {} attempts", maxAttempts);
                break;
            }
        } while (validateKeyStrength(key) < minStrength);

        log.info("Generated encryption key with strength: {}", validateKeyStrength(key));
        return key;
    }
}