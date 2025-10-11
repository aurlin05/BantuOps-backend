package com.bantuops.backend.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO pour les demandes de calcul de TVA
 * Conforme aux exigences 2.1, 2.3, 3.1, 3.2 pour les calculs fiscaux sénégalais
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VATCalculationRequest {

    @NotNull(message = "Le montant hors taxes est obligatoire")
    @DecimalMin(value = "0.0", inclusive = false, message = "Le montant hors taxes doit être positif")
    @Digits(integer = 15, fraction = 2, message = "Le montant doit avoir au maximum 15 chiffres entiers et 2 décimales")
    private BigDecimal amountExcludingVat;

    @DecimalMin(value = "0.0", message = "Le taux de TVA ne peut pas être négatif")
    @DecimalMax(value = "1.0", message = "Le taux de TVA ne peut pas dépasser 100%")
    @Digits(integer = 1, fraction = 4, message = "Le taux de TVA doit avoir au maximum 1 chiffre entier et 4 décimales")
    private BigDecimal vatRate;

    @NotBlank(message = "La devise est obligatoire")
    @Pattern(regexp = "^(XOF|EUR|USD)$", message = "La devise doit être XOF (Franc CFA), EUR ou USD")
    private String currency;

    @Pattern(regexp = "^[0-9]{13}$", message = "Le numéro fiscal sénégalais doit contenir exactement 13 chiffres")
    private String clientTaxNumber;

    @NotNull(message = "La date de transaction est obligatoire")
    private LocalDate transactionDate;

    private String transactionType;

    private String businessSector;

    private Boolean isExportTransaction;

    private Boolean isGovernmentTransaction;

    private String exemptionCode;

    private String exemptionReason;

    @AssertTrue(message = "Le numéro fiscal est obligatoire pour les montants supérieurs à 1 000 000 XOF")
    public boolean isValidTaxNumberRequirement() {
        if (amountExcludingVat == null || !"XOF".equals(currency)) {
            return true;
        }
        
        BigDecimal threshold = new BigDecimal("1000000");
        if (amountExcludingVat.compareTo(threshold) > 0) {
            return clientTaxNumber != null && !clientTaxNumber.trim().isEmpty();
        }
        return true;
    }
}