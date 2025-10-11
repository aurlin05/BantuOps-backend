package com.bantuops.backend.converter;

import com.bantuops.backend.service.DataEncryptionService;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Convertisseur JPA pour le chiffrement automatique des valeurs Long
 * Utilisé pour les identifiants sensibles et les valeurs numériques critiques
 */
@Converter
@Component
@Slf4j
public class EncryptedLongConverter implements AttributeConverter<Long, String> {

    @Autowired
    private DataEncryptionService encryptionService;

    @Override
    public String convertToDatabaseColumn(Long attribute) {
        if (attribute == null) {
            log.trace("Skipping encryption for null Long");
            return null;
        }

        try {
            String plainValue = attribute.toString();
            String encrypted = encryptionService.encrypt(plainValue);
            log.debug("Long encrypted successfully, value: {}, encrypted length: {}", 
                     attribute, encrypted.length());
            return encrypted;
        } catch (Exception e) {
            log.error("Failed to encrypt Long attribute: {}", attribute, e);
            throw new RuntimeException("Long encryption failed during database write", e);
        }
    }

    @Override
    public Long convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            log.trace("Skipping decryption for null/empty Long database value");
            return null;
        }

        try {
            String decrypted = encryptionService.decrypt(dbData);
            Long result = Long.valueOf(decrypted);
            log.debug("Long decrypted successfully, encrypted length: {}, value: {}", 
                     dbData.length(), result);
            return result;
        } catch (NumberFormatException e) {
            log.error("Failed to parse decrypted Long value", e);
            throw new RuntimeException("Invalid Long format after decryption", e);
        } catch (Exception e) {
            log.error("Failed to decrypt Long attribute of length: {}", dbData.length(), e);
            throw new RuntimeException("Long decryption failed during database read", e);
        }
    }
}