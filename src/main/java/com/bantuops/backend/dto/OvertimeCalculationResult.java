package com.bantuops.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * DTO pour le résultat des calculs d'heures supplémentaires
 * Conforme aux exigences 1.6, 3.1, 3.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OvertimeCalculationResult {

    private Long employeeId;
    private YearMonth period;
    private BigDecimal hourlyRate;

    // Heures supplémentaires par type
    @Builder.Default
    private BigDecimal regularOvertimeHours = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal nightOvertimeHours = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal weekendOvertimeHours = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal holidayOvertimeHours = BigDecimal.ZERO;

    // Montants par type
    @Builder.Default
    private BigDecimal regularOvertimeAmount = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal nightOvertimeAmount = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal weekendOvertimeAmount = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal holidayOvertimeAmount = BigDecimal.ZERO;

    // Primes de rendement
    @Builder.Default
    private BigDecimal performanceBonus = BigDecimal.ZERO;

    // Total
    @Builder.Default
    private BigDecimal totalOvertimeHours = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal totalOvertimeAmount = BigDecimal.ZERO;

    // Détails des majorations appliquées
    private String overtimeRatesApplied;
    private String calculationNotes;

    /**
     * Calcule le total des heures supplémentaires
     */
    public BigDecimal calculateTotalHours() {
        return regularOvertimeHours
            .add(nightOvertimeHours)
            .add(weekendOvertimeHours)
            .add(holidayOvertimeHours);
    }

    /**
     * Calcule le total des montants
     */
    public BigDecimal calculateTotalAmount() {
        return regularOvertimeAmount
            .add(nightOvertimeAmount)
            .add(weekendOvertimeAmount)
            .add(holidayOvertimeAmount)
            .add(performanceBonus);
    }

    /**
     * Vérifie si le calcul est valide
     */
    public boolean isValid() {
        return totalOvertimeAmount.compareTo(BigDecimal.ZERO) >= 0 &&
               totalOvertimeHours.compareTo(BigDecimal.ZERO) >= 0;
    }
}