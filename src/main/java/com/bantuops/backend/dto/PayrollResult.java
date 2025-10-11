package com.bantuops.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO pour le résultat des calculs de paie
 * Conforme aux exigences 1.1, 1.2, 1.3, 3.1, 3.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollResult {

    private Long employeeId;
    private YearMonth period;

    // Salaire de base et taux
    private BigDecimal baseSalary;
    private BigDecimal hourlyRate;

    // Heures travaillées
    @Builder.Default
    private BigDecimal regularHours = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal overtimeHours = BigDecimal.ZERO;

    // Calculs de salaire
    private BigDecimal regularSalary;
    @Builder.Default
    private BigDecimal overtimeAmount = BigDecimal.ZERO;
    private BigDecimal grossSalary;
    private BigDecimal netSalary;

    // Primes et indemnités
    @Builder.Default
    private BigDecimal performanceBonus = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal transportAllowance = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal mealAllowance = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal housingAllowance = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal otherAllowances = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal totalAllowances = BigDecimal.ZERO;

    // Taxes et cotisations sociales (Sénégal)
    @Builder.Default
    private BigDecimal incomeTax = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal ipresContribution = BigDecimal.ZERO; // Institution de Prévoyance Retraite du Sénégal
    @Builder.Default
    private BigDecimal cssContribution = BigDecimal.ZERO; // Caisse de Sécurité Sociale
    @Builder.Default
    private BigDecimal familyAllowanceContribution = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal totalSocialContributions = BigDecimal.ZERO;

    // Déductions
    @Builder.Default
    private BigDecimal advanceDeduction = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal loanDeduction = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal absenceDeduction = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal delayPenalty = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal otherDeductions = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    // Détails des calculs
    @Builder.Default
    private List<PayrollCalculationDetail> calculationDetails = new ArrayList<>();

    // Informations de traitement
    private String calculatedBy;
    private String notes;

    /**
     * Calcule le total des indemnités
     */
    public BigDecimal calculateTotalAllowances() {
        return performanceBonus
            .add(transportAllowance)
            .add(mealAllowance)
            .add(housingAllowance)
            .add(otherAllowances)
            .add(overtimeAmount);
    }

    /**
     * Calcule le total des déductions
     */
    public BigDecimal calculateTotalDeductions() {
        return advanceDeduction
            .add(loanDeduction)
            .add(absenceDeduction)
            .add(delayPenalty)
            .add(otherDeductions);
    }

    /**
     * Calcule le total des cotisations sociales
     */
    public BigDecimal calculateTotalSocialContributions() {
        return ipresContribution
            .add(cssContribution)
            .add(familyAllowanceContribution);
    }

    /**
     * Vérifie si le calcul est valide
     */
    public boolean isValid() {
        return netSalary != null && 
               netSalary.compareTo(BigDecimal.ZERO) >= 0 &&
               grossSalary != null &&
               grossSalary.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Détail d'un calcul de paie
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PayrollCalculationDetail {
        private String type;
        private String description;
        private BigDecimal amount;
        private String formula;
        private String notes;
    }
}