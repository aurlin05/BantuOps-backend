package com.bantuops.backend.converter;

import com.bantuops.backend.service.DataEncryptionService;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Convertisseur JPA pour le chiffrement automatique des montants BigDecimal
 * Essentiel pour la sécurité des données financières sensibles
 */
@Converter
@Component
@Slf4j
public class EncryptedBigDecimalConverter implements AttributeConverter<BigDecimal, String> {

    @Autowired
    private DataEncryptionService encryptionService;

    @Override
    public String convertToDatabaseColumn(BigDecimal attribute) {
        if (attribute == null) {
            log.trace("Skipping encryption for null BigDecimal");
            return null;
        }

        try {
            String plainValue = attribute.toPlainString();
            String encrypted = encryptionService.encrypt(plainValue);
            log.debug("BigDecimal encrypted successfully, value: {}, encrypted length: {}", 
                     attribute.toPlainString(), encrypted.length());
            return encrypted;
        } catch (Exception e) {
            log.error("Failed to encrypt BigDecimal attribute: {}", attribute.toPlainString(), e);
            throw new RuntimeException("BigDecimal encryption failed during database write", e);
        }
    }

    @Override
    public BigDecimal convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            log.trace("Skipping decryption for null/empty BigDecimal database value");
            return null;
        }

        try {
            String decrypted = encryptionService.decrypt(dbData);
            BigDecimal result = new BigDecimal(decrypted);
            log.debug("BigDecimal decrypted successfully, encrypted length: {}, value: {}", 
                     dbData.length(), result.toPlainString());
            return result;
        } catch (NumberFormatException e) {
            log.error("Failed to parse decrypted BigDecimal value", e);
            throw new RuntimeException("Invalid BigDecimal format after decryption", e);
        } catch (Exception e) {
            log.error("Failed to decrypt BigDecimal attribute of length: {}", dbData.length(), e);
            throw new RuntimeException("BigDecimal decryption failed during database read", e);
        }
    }
}