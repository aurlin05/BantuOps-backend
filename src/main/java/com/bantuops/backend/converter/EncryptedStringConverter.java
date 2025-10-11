package com.bantuops.backend.converter;

import com.bantuops.backend.service.DataEncryptionService;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Convertisseur JPA pour le chiffrement automatique des chaînes de caractères
 * Conforme aux exigences de sécurité pour les données personnelles sensibles
 */
@Converter
@Component
@Slf4j
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Autowired
    private DataEncryptionService encryptionService;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            log.trace("Skipping encryption for null/empty string");
            return attribute;
        }

        try {
            String encrypted = encryptionService.encrypt(attribute);
            log.debug("String encrypted successfully, original length: {}, encrypted length: {}", 
                     attribute.length(), encrypted.length());
            return encrypted;
        } catch (Exception e) {
            log.error("Failed to encrypt string attribute of length: {}", attribute.length(), e);
            throw new RuntimeException("String encryption failed during database write", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            log.trace("Skipping decryption for null/empty database value");
            return dbData;
        }

        try {
            String decrypted = encryptionService.decrypt(dbData);
            log.debug("String decrypted successfully, encrypted length: {}, decrypted length: {}", 
                     dbData.length(), decrypted.length());
            return decrypted;
        } catch (Exception e) {
            log.error("Failed to decrypt string attribute of length: {}", dbData.length(), e);
            throw new RuntimeException("String decryption failed during database read", e);
        }
    }
}