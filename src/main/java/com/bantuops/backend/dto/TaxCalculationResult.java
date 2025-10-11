package com.bantuops.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO pour le résultat des calculs fiscaux sénégalais
 * Conforme aux exigences 1.2, 1.4, 3.1, 3.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxCalculationResult {

    private BigDecimal grossSalary;
    private BigDecimal annualSalary;

    // Impôt sur le revenu
    @Builder.Default
    private BigDecimal incomeTax = BigDecimal.ZERO;

    // Cotisations sociales sénégalaises
    @Builder.Default
    private BigDecimal ipresContribution = BigDecimal.ZERO; // Institution de Prévoyance Retraite du Sénégal
    
    @Builder.Default
    private BigDecimal cssContribution = BigDecimal.ZERO; // Caisse de Sécurité Sociale
    
    @Builder.Default
    private BigDecimal familyAllowanceContribution = BigDecimal.ZERO; // Allocations familiales

    // Totaux
    @Builder.Default
    private BigDecimal totalSocialContributions = BigDecimal.ZERO;
    
    @Builder.Default
    private BigDecimal totalTaxes = BigDecimal.ZERO;

    // Détails des calculs
    private String calculationMethod;
    private String taxBracketApplied;
    private BigDecimal effectiveTaxRate;

    /**
     * Calcule le taux d'imposition effectif
     */
    public BigDecimal calculateEffectiveTaxRate() {
        if (grossSalary.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return totalTaxes.divide(grossSalary, 4, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Vérifie si le calcul est valide
     */
    public boolean isValid() {
        return grossSalary != null && 
               grossSalary.compareTo(BigDecimal.ZERO) >= 0 &&
               totalTaxes.compareTo(BigDecimal.ZERO) >= 0 &&
               totalTaxes.compareTo(grossSalary) <= 0;
    }
}