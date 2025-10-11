package com.bantuops.backend.config;

import com.bantuops.backend.service.DataEncryptionService;
import com.bantuops.backend.util.KeyManagementUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Configuration du système de chiffrement
 * Assure l'initialisation et la validation de la sécurité au démarrage
 */
@Configuration
@Slf4j
public class EncryptionConfig {

    @Autowired
    private DataEncryptionService encryptionService;

    @Value("${bantuops.encryption.key:}")
    private String encryptionKey;

    @Value("${bantuops.encryption.validate-on-startup:true}")
    private boolean validateOnStartup;

    @Value("${bantuops.encryption.min-key-strength:70}")
    private int minKeyStrength;

    /**
     * Validation du système de chiffrement au démarrage de l'application
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validateEncryptionOnStartup() {
        log.info("Initializing encryption system validation...");

        try {
            // Vérification de la présence de la clé
            if (encryptionKey == null || encryptionKey.isEmpty()) {
                log.error("CRITICAL SECURITY ISSUE: No encryption key configured!");
                log.error("Please set the ENCRYPTION_KEY environment variable");
                log.error("You can generate a new key using KeyManagementUtil.generateEncryptionKey()");
                
                if (validateOnStartup) {
                    throw new IllegalStateException("Encryption key must be configured for production use");
                }
                return;
            }

            // Validation du format de la clé
            if (!KeyManagementUtil.isValidEncryptionKey(encryptionKey)) {
                log.error("CRITICAL SECURITY ISSUE: Invalid encryption key format!");
                throw new IllegalStateException("Invalid encryption key configuration");
            }

            // Validation de la force de la clé
            int keyStrength = KeyManagementUtil.validateKeyStrength(encryptionKey);
            log.info("Encryption key strength: {}/100", keyStrength);

            if (keyStrength < minKeyStrength) {
                log.warn("WARNING: Encryption key strength ({}) is below recommended minimum ({})", 
                        keyStrength, minKeyStrength);
                
                if (validateOnStartup) {
                    log.error("Consider generating a stronger key for production use");
                }
            }

            // Test de fonctionnement du chiffrement
            if (validateOnStartup) {
                boolean isValid = encryptionService.validateEncryptionConfiguration();
                if (!isValid) {
                    throw new IllegalStateException("Encryption system validation failed");
                }
            }

            log.info("Encryption system initialized and validated successfully");
            log.info("AES-256-GCM encryption is active for sensitive data protection");

        } catch (Exception e) {
            log.error("Failed to initialize encryption system", e);
            if (validateOnStartup) {
                throw new RuntimeException("Encryption system initialization failed", e);
            }
        }
    }

    /**
     * Génère une nouvelle clé de chiffrement pour l'environnement de développement
     * Cette méthode ne doit PAS être utilisée en production
     */
    public String generateDevelopmentKey() {
        if (!"development".equals(System.getProperty("spring.profiles.active"))) {
            throw new IllegalStateException("Key generation is only allowed in development environment");
        }

        String newKey = KeyManagementUtil.generateStrongEncryptionKey(minKeyStrength);
        log.info("Generated new development encryption key");
        log.info("Add this to your environment variables: ENCRYPTION_KEY={}", newKey);
        
        return newKey;
    }

    /**
     * Affiche des informations sur la configuration de chiffrement
     */
    public void displayEncryptionInfo() {
        log.info("=== Encryption Configuration ===");
        log.info("Algorithm: AES-256-GCM");
        log.info("Key configured: {}", encryptionKey != null && !encryptionKey.isEmpty());
        log.info("Validation on startup: {}", validateOnStartup);
        log.info("Minimum key strength: {}", minKeyStrength);
        
        if (encryptionKey != null && !encryptionKey.isEmpty()) {
            int strength = KeyManagementUtil.validateKeyStrength(encryptionKey);
            log.info("Current key strength: {}/100", strength);
        }
        log.info("===============================");
    }
}