package com.bantuops.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Service de signature numérique pour les documents de paie
 * Conforme aux exigences 2.3, 2.4 pour la sécurisation des documents
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DigitalSignatureService {

    private final DataEncryptionService encryptionService;

    /**
     * Signe numériquement un document
     */
    public String signDocument(byte[] documentContent) {
        log.debug("Signature numérique du document de {} bytes", documentContent.length);

        try {
            // Calcul du hash du document
            String documentHash = calculateSHA256Hash(documentContent);
            
            // Chiffrement du hash pour créer la signature
            String signature = encryptionService.encrypt(documentHash);
            
            log.debug("Signature numérique générée avec succès");
            return signature;

        } catch (Exception e) {
            log.error("Erreur lors de la signature numérique: {}", e.getMessage(), e);
            throw new RuntimeException("Erreur lors de la signature numérique", e);
        }
    }

    /**
     * Valide une signature numérique
     */
    public boolean validateSignature(byte[] documentContent, String signature) {
        log.debug("Validation de la signature numérique");

        try {
            // Calcul du hash du document actuel
            String currentHash = calculateSHA256Hash(documentContent);
            
            // Déchiffrement de la signature pour récupérer le hash original
            String originalHash = encryptionService.decrypt(signature);
            
            // Comparaison des hash
            boolean isValid = currentHash.equals(originalHash);
            
            log.debug("Validation de signature terminée - Résultat: {}", isValid);
            return isValid;

        } catch (Exception e) {
            log.error("Erreur lors de la validation de signature: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Génère un checksum pour vérification d'intégrité
     */
    public String generateChecksum(byte[] content) {
        try {
            return calculateSHA256Hash(content);
        } catch (Exception e) {
            log.error("Erreur lors de la génération du checksum: {}", e.getMessage(), e);
            throw new RuntimeException("Erreur lors de la génération du checksum", e);
        }
    }

    /**
     * Vérifie l'intégrité d'un document avec son checksum
     */
    public boolean verifyIntegrity(byte[] content, String expectedChecksum) {
        try {
            String actualChecksum = calculateSHA256Hash(content);
            return actualChecksum.equals(expectedChecksum);
        } catch (Exception e) {
            log.error("Erreur lors de la vérification d'intégrité: {}", e.getMessage(), e);
            return false;
        }
    }

    // Méthodes privées

    private String calculateSHA256Hash(byte[] content) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(content);
        return Base64.getEncoder().encodeToString(hashBytes);
    }
}