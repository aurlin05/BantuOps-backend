package com.bantuops.backend.service;

import com.bantuops.backend.util.KeyManagementUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service de chiffrement des données sensibles avec AES-256-GCM
 * Conforme aux exigences de sécurité pour les données financières sénégalaises
 */
@Service
@Slf4j
public class DataEncryptionService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    private static final int KEY_LENGTH = 256;

    @Value("${bantuops.encryption.key:}")
    private String encryptionKeyBase64;

    // Cache pour éviter de recréer la clé à chaque opération
    private volatile SecretKey cachedSecretKey;

    /**
     * Récupère la clé de chiffrement de manière sécurisée
     * Utilise un cache pour éviter les opérations répétées
     */
    private SecretKey getEncryptionKey() {
        if (cachedSecretKey != null) {
            return cachedSecretKey;
        }

        synchronized (this) {
            if (cachedSecretKey != null) {
                return cachedSecretKey;
            }

            if (encryptionKeyBase64 == null || encryptionKeyBase64.isEmpty()) {
                log.error("CRITICAL: No encryption key configured! Set ENCRYPTION_KEY environment variable");
                throw new IllegalStateException("Encryption key must be configured in production environment");
            }

            // Validation de la clé
            if (!KeyManagementUtil.isValidEncryptionKey(encryptionKeyBase64)) {
                log.error("CRITICAL: Invalid encryption key format or length");
                throw new IllegalStateException("Invalid encryption key configuration");
            }

            try {
                byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
                cachedSecretKey = new SecretKeySpec(keyBytes, ALGORITHM);
                log.info("Encryption key loaded and validated successfully");
                return cachedSecretKey;
            } catch (Exception e) {
                log.error("Failed to load encryption key", e);
                throw new RuntimeException("Failed to initialize encryption key", e);
            }
        }
    }

    /**
     * Chiffre une chaîne de caractères avec AES-256-GCM
     * @param plainText Texte en clair à chiffrer
     * @return Texte chiffré encodé en Base64 avec IV inclus
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        try {
            SecretKey secretKey = getEncryptionKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);

            // Génération d'un IV aléatoire sécurisé
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom.getInstanceStrong().nextBytes(iv);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
            byte[] encryptedData = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Combinaison IV + données chiffrées
            byte[] encryptedWithIv = new byte[GCM_IV_LENGTH + encryptedData.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, GCM_IV_LENGTH);
            System.arraycopy(encryptedData, 0, encryptedWithIv, GCM_IV_LENGTH, encryptedData.length);

            String result = Base64.getEncoder().encodeToString(encryptedWithIv);
            log.debug("Data encrypted successfully, length: {}", result.length());
            return result;

        } catch (Exception e) {
            log.error("Encryption failed for data length: {}", plainText.length(), e);
            throw new RuntimeException("Encryption operation failed", e);
        }
    }

    /**
     * Déchiffre une chaîne de caractères chiffrée avec AES-256-GCM
     * @param encryptedText Texte chiffré encodé en Base64 avec IV inclus
     * @return Texte en clair déchiffré
     */
    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }

        try {
            SecretKey secretKey = getEncryptionKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);

            byte[] encryptedWithIv = Base64.getDecoder().decode(encryptedText);

            // Validation de la taille minimale
            if (encryptedWithIv.length < GCM_IV_LENGTH + GCM_TAG_LENGTH) {
                throw new IllegalArgumentException("Invalid encrypted data format");
            }

            // Extraction IV et données chiffrées
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encryptedData = new byte[encryptedWithIv.length - GCM_IV_LENGTH];
            System.arraycopy(encryptedWithIv, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(encryptedWithIv, GCM_IV_LENGTH, encryptedData, 0, encryptedData.length);

            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] decryptedData = cipher.doFinal(encryptedData);
            String result = new String(decryptedData, StandardCharsets.UTF_8);
            log.debug("Data decrypted successfully, length: {}", result.length());
            return result;

        } catch (Exception e) {
            log.error("Decryption failed for data length: {}", encryptedText.length(), e);
            throw new RuntimeException("Decryption operation failed", e);
        }
    }

    public byte[] encryptBytes(byte[] plainBytes) {
        if (plainBytes == null || plainBytes.length == 0) {
            return plainBytes;
        }

        try {
            SecretKey secretKey = getEncryptionKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);

            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
            byte[] encryptedData = cipher.doFinal(plainBytes);

            // Combine IV and encrypted data
            byte[] encryptedWithIv = new byte[GCM_IV_LENGTH + encryptedData.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, GCM_IV_LENGTH);
            System.arraycopy(encryptedData, 0, encryptedWithIv, GCM_IV_LENGTH, encryptedData.length);

            return encryptedWithIv;

        } catch (Exception e) {
            log.error("Byte encryption failed", e);
            throw new RuntimeException("Byte encryption failed", e);
        }
    }

    public byte[] decryptBytes(byte[] encryptedBytes) {
        if (encryptedBytes == null || encryptedBytes.length == 0) {
            return encryptedBytes;
        }

        try {
            SecretKey secretKey = getEncryptionKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);

            // Extract IV and encrypted data
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encryptedData = new byte[encryptedBytes.length - GCM_IV_LENGTH];
            System.arraycopy(encryptedBytes, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(encryptedBytes, GCM_IV_LENGTH, encryptedData, 0, encryptedData.length);

            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            return cipher.doFinal(encryptedData);

        } catch (Exception e) {
            log.error("Byte decryption failed", e);
            throw new RuntimeException("Byte decryption failed", e);
        }
    }

    /**
     * Génère une nouvelle clé AES-256 et la retourne en Base64
     * Cette méthode ne doit être utilisée que pour la génération initiale de clés
     * dans des environnements sécurisés
     */
    public String generateNewEncryptionKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(KEY_LENGTH, SecureRandom.getInstanceStrong());
            SecretKey secretKey = keyGenerator.generateKey();
            String encodedKey = Base64.getEncoder().encodeToString(secretKey.getEncoded());
            log.info("New encryption key generated successfully");
            return encodedKey;
        } catch (Exception e) {
            log.error("Failed to generate encryption key", e);
            throw new RuntimeException("Failed to generate encryption key", e);
        }
    }

    /**
     * Valide l'intégrité de la configuration de chiffrement
     * @return true si la configuration est valide
     */
    public boolean validateEncryptionConfiguration() {
        try {
            if (encryptionKeyBase64 == null || encryptionKeyBase64.isEmpty()) {
                log.warn("No encryption key configured");
                return false;
            }

            if (!KeyManagementUtil.isValidEncryptionKey(encryptionKeyBase64)) {
                log.error("Invalid encryption key format");
                return false;
            }

            // Test de chiffrement/déchiffrement
            String testData = "test-encryption-" + System.currentTimeMillis();
            String encrypted = encrypt(testData);
            String decrypted = decrypt(encrypted);

            boolean isValid = testData.equals(decrypted);
            if (isValid) {
                log.info("Encryption configuration validated successfully");
            } else {
                log.error("Encryption configuration validation failed");
            }
            return isValid;

        } catch (Exception e) {
            log.error("Encryption configuration validation failed", e);
            return false;
        }
    }

    /**
     * Efface le cache de la clé de chiffrement
     * Utile lors de la rotation des clés
     */
    public void clearKeyCache() {
        synchronized (this) {
            cachedSecretKey = null;
            log.info("Encryption key cache cleared");
        }
    }
}