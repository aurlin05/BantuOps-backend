package com.bantuops.backend.converter;

import com.bantuops.backend.service.DataEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour les convertisseurs de chiffrement JPA
 */
@ExtendWith(MockitoExtension.class)
class EncryptionConvertersTest {

    @Mock
    private DataEncryptionService encryptionService;

    private EncryptedStringConverter stringConverter;
    private EncryptedBigDecimalConverter bigDecimalConverter;
    private EncryptedLongConverter longConverter;

    @BeforeEach
    void setUp() {
        stringConverter = new EncryptedStringConverter();
        bigDecimalConverter = new EncryptedBigDecimalConverter();
        longConverter = new EncryptedLongConverter();

        ReflectionTestUtils.setField(stringConverter, "encryptionService", encryptionService);
        ReflectionTestUtils.setField(bigDecimalConverter, "encryptionService", encryptionService);
        ReflectionTestUtils.setField(longConverter, "encryptionService", encryptionService);
    }

    @Test
    void testStringConverterEncryption() {
        // Given
        String originalValue = "Sensitive data";
        String encryptedValue = "encrypted_data";
        when(encryptionService.encrypt(originalValue)).thenReturn(encryptedValue);

        // When
        String result = stringConverter.convertToDatabaseColumn(originalValue);

        // Then
        assertEquals(encryptedValue, result);
        verify(encryptionService).encrypt(originalValue);
    }

    @Test
    void testStringConverterDecryption() {
        // Given
        String encryptedValue = "encrypted_data";
        String decryptedValue = "Sensitive data";
        when(encryptionService.decrypt(encryptedValue)).thenReturn(decryptedValue);

        // When
        String result = stringConverter.convertToEntityAttribute(encryptedValue);

        // Then
        assertEquals(decryptedValue, result);
        verify(encryptionService).decrypt(encryptedValue);
    }

    @Test
    void testStringConverterWithNullValue() {
        // When
        String encryptResult = stringConverter.convertToDatabaseColumn(null);
        String decryptResult = stringConverter.convertToEntityAttribute(null);

        // Then
        assertNull(encryptResult);
        assertNull(decryptResult);
        verify(encryptionService, never()).encrypt(anyString());
        verify(encryptionService, never()).decrypt(anyString());
    }

    @Test
    void testStringConverterWithEmptyValue() {
        // When
        String encryptResult = stringConverter.convertToDatabaseColumn("");
        String decryptResult = stringConverter.convertToEntityAttribute("");

        // Then
        assertEquals("", encryptResult);
        assertEquals("", decryptResult);
        verify(encryptionService, never()).encrypt(anyString());
        verify(encryptionService, never()).decrypt(anyString());
    }

    @Test
    void testBigDecimalConverterEncryption() {
        // Given
        BigDecimal originalValue = new BigDecimal("1234567.89");
        String encryptedValue = "encrypted_amount";
        when(encryptionService.encrypt("1234567.89")).thenReturn(encryptedValue);

        // When
        String result = bigDecimalConverter.convertToDatabaseColumn(originalValue);

        // Then
        assertEquals(encryptedValue, result);
        verify(encryptionService).encrypt("1234567.89");
    }

    @Test
    void testBigDecimalConverterDecryption() {
        // Given
        String encryptedValue = "encrypted_amount";
        String decryptedValue = "1234567.89";
        when(encryptionService.decrypt(encryptedValue)).thenReturn(decryptedValue);

        // When
        BigDecimal result = bigDecimalConverter.convertToEntityAttribute(encryptedValue);

        // Then
        assertEquals(new BigDecimal("1234567.89"), result);
        verify(encryptionService).decrypt(encryptedValue);
    }

    @Test
    void testBigDecimalConverterWithNullValue() {
        // When
        String encryptResult = bigDecimalConverter.convertToDatabaseColumn(null);
        BigDecimal decryptResult = bigDecimalConverter.convertToEntityAttribute(null);

        // Then
        assertNull(encryptResult);
        assertNull(decryptResult);
        verify(encryptionService, never()).encrypt(anyString());
        verify(encryptionService, never()).decrypt(anyString());
    }

    @Test
    void testBigDecimalConverterWithZeroValue() {
        // Given
        BigDecimal zeroValue = BigDecimal.ZERO;
        String encryptedValue = "encrypted_zero";
        when(encryptionService.encrypt("0")).thenReturn(encryptedValue);

        // When
        String result = bigDecimalConverter.convertToDatabaseColumn(zeroValue);

        // Then
        assertEquals(encryptedValue, result);
        verify(encryptionService).encrypt("0");
    }

    @Test
    void testLongConverterEncryption() {
        // Given
        Long originalValue = 123456789L;
        String encryptedValue = "encrypted_long";
        when(encryptionService.encrypt("123456789")).thenReturn(encryptedValue);

        // When
        String result = longConverter.convertToDatabaseColumn(originalValue);

        // Then
        assertEquals(encryptedValue, result);
        verify(encryptionService).encrypt("123456789");
    }

    @Test
    void testLongConverterDecryption() {
        // Given
        String encryptedValue = "encrypted_long";
        String decryptedValue = "123456789";
        when(encryptionService.decrypt(encryptedValue)).thenReturn(decryptedValue);

        // When
        Long result = longConverter.convertToEntityAttribute(encryptedValue);

        // Then
        assertEquals(Long.valueOf(123456789L), result);
        verify(encryptionService).decrypt(encryptedValue);
    }

    @Test
    void testLongConverterWithNullValue() {
        // When
        String encryptResult = longConverter.convertToDatabaseColumn(null);
        Long decryptResult = longConverter.convertToEntityAttribute(null);

        // Then
        assertNull(encryptResult);
        assertNull(decryptResult);
        verify(encryptionService, never()).encrypt(anyString());
        verify(encryptionService, never()).decrypt(anyString());
    }

    @Test
    void testStringConverterEncryptionFailure() {
        // Given
        String originalValue = "Sensitive data";
        when(encryptionService.encrypt(originalValue)).thenThrow(new RuntimeException("Encryption failed"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            stringConverter.convertToDatabaseColumn(originalValue);
        });
        
        assertTrue(exception.getMessage().contains("String encryption failed"));
    }

    @Test
    void testBigDecimalConverterDecryptionFailure() {
        // Given
        String encryptedValue = "invalid_encrypted_data";
        when(encryptionService.decrypt(encryptedValue)).thenThrow(new RuntimeException("Decryption failed"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            bigDecimalConverter.convertToEntityAttribute(encryptedValue);
        });
        
        assertTrue(exception.getMessage().contains("BigDecimal decryption failed"));
    }

    @Test
    void testBigDecimalConverterInvalidNumberFormat() {
        // Given
        String encryptedValue = "encrypted_data";
        String invalidDecryptedValue = "not_a_number";
        when(encryptionService.decrypt(encryptedValue)).thenReturn(invalidDecryptedValue);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            bigDecimalConverter.convertToEntityAttribute(encryptedValue);
        });
        
        assertTrue(exception.getMessage().contains("Invalid BigDecimal format"));
    }

    @Test
    void testLongConverterInvalidNumberFormat() {
        // Given
        String encryptedValue = "encrypted_data";
        String invalidDecryptedValue = "not_a_long";
        when(encryptionService.decrypt(encryptedValue)).thenReturn(invalidDecryptedValue);

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            longConverter.convertToEntityAttribute(encryptedValue);
        });
        
        assertTrue(exception.getMessage().contains("Invalid Long format"));
    }
}