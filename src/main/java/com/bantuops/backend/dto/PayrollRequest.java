package com.bantuops.backend.dto;

import com.bantuops.backend.validation.SenegaleseBusinessRule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * DTO PayrollRequest avec validation des données salariales
 * Conforme aux exigences 3.1, 3.2, 3.3, 3.4, 3.5, 3.6 pour la validation métier
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@SenegaleseBusinessRule(SenegaleseBusinessRule.RuleType.PAYROLL)
public class PayrollRequest {

    @NotNull(message = "L'ID de l'employé est obligatoire")
    @Positive(message = "L'ID de l'employé doit être positif")
    private Long employeeId;

    @NotNull(message = "La période de paie est obligatoire")
    @PastOrPresent(message = "La période de paie ne peut pas être dans le futur")
    private YearMonth payrollPeriod;

    @NotNull(message = "Le salaire de base est obligatoire")
    @DecimalMin(value = "0.0", inclusive = false, message = "Le salaire de base doit être positif")
    @Digits(integer = 10, fraction = 2, message = "Le salaire de base doit avoir au maximum 10 chiffres entiers et 2 décimales")
    private BigDecimal baseSalary;

    @DecimalMin(value = "0.0", message = "Les heures normales ne peuvent pas être négatives")
    @DecimalMax(value = "744.0", message = "Les heures normales ne peuvent pas dépasser 744 heures par mois")
    @Digits(integer = 3, fraction = 2, message = "Les heures normales doivent avoir au maximum 3 chiffres entiers et 2 décimales")
    private BigDecimal regularHours;

    @DecimalMin(value = "0.0", message = "Les heures supplémentaires ne peuvent pas être négatives")
    @DecimalMax(value = "200.0", message = "Les heures supplémentaires ne peuvent pas dépasser 200 heures par mois")
    @Digits(integer = 3, fraction = 2, message = "Les heures supplémentaires doivent avoir au maximum 3 chiffres entiers et 2 décimales")
    private BigDecimal overtimeHours;

    @DecimalMin(value = "0.0", message = "La prime de performance ne peut pas être négative")
    @Digits(integer = 10, fraction = 2, message = "La prime de performance doit avoir au maximum 10 chiffres entiers et 2 décimales")
    private BigDecimal performanceBonus;

    @DecimalMin(value = "0.0", message = "L'indemnité de transport ne peut pas être négative")
    @Digits(integer = 8, fraction = 2, message = "L'indemnité de transport doit avoir au maximum 8 chiffres entiers et 2 décimales")
    private BigDecimal transportAllowance;

    @DecimalMin(value = "0.0", message = "L'indemnité de repas ne peut pas être négative")
    @Digits(integer = 8, fraction = 2, message = "L'indemnité de repas doit avoir au maximum 8 chiffres entiers et 2 décimales")
    private BigDecimal mealAllowance;

    @DecimalMin(value = "0.0", message = "L'indemnité de logement ne peut pas être négative")
    @Digits(integer = 10, fraction = 2, message = "L'indemnité de logement doit avoir au maximum 10 chiffres entiers et 2 décimales")
    private BigDecimal housingAllowance;

    @DecimalMin(value = "0.0", message = "Les autres indemnités ne peuvent pas être négatives")
    @Digits(integer = 10, fraction = 2, message = "Les autres indemnités doivent avoir au maximum 10 chiffres entiers et 2 décimales")
    private BigDecimal otherAllowances;

    @DecimalMin(value = "0.0", message = "La déduction d'avance ne peut pas être négative")
    @Digits(integer = 10, fraction = 2, message = "La déduction d'avance doit avoir au maximum 10 chiffres entiers et 2 décimales")
    private BigDecimal advanceDeduction;

    @DecimalMin(value = "0.0", message = "La déduction de prêt ne peut pas être négative")
    @Digits(integer = 10, fraction = 2, message = "La déduction de prêt doit avoir au maximum 10 chiffres entiers et 2 décimales")
    private BigDecimal loanDeduction;

    @DecimalMin(value = "0.0", message = "La déduction d'absence ne peut pas être négative")
    @Digits(integer = 10, fraction = 2, message = "La déduction d'absence doit avoir au maximum 10 chiffres entiers et 2 décimales")
    private BigDecimal absenceDeduction;

    @DecimalMin(value = "0.0", message = "La pénalité de retard ne peut pas être négative")
    @Digits(integer = 8, fraction = 2, message = "La pénalité de retard doit avoir au maximum 8 chiffres entiers et 2 décimales")
    private BigDecimal delayPenalty;

    @DecimalMin(value = "0.0", message = "Les autres déductions ne peuvent pas être négatives")
    @Digits(integer = 10, fraction = 2, message = "Les autres déductions doivent avoir au maximum 10 chiffres entiers et 2 décimales")
    private BigDecimal otherDeductions;

    @Valid
    private List<@Valid PayrollAdjustmentRequest> adjustments;

    @Size(max = 1000, message = "Les notes ne peuvent pas dépasser 1000 caractères")
    private String notes;

    @AssertTrue(message = "Le total des déductions ne peut pas dépasser le salaire brut")
    public boolean isValidDeductions() {
        if (baseSalary == null) return true;
        
        BigDecimal totalDeductions = BigDecimal.ZERO;
        if (advanceDeduction != null) totalDeductions = totalDeductions.add(advanceDeduction);
        if (loanDeduction != null) totalDeductions = totalDeductions.add(loanDeduction);
        if (absenceDeduction != null) totalDeductions = totalDeductions.add(absenceDeduction);
        if (delayPenalty != null) totalDeductions = totalDeductions.add(delayPenalty);
        if (otherDeductions != null) totalDeductions = totalDeductions.add(otherDeductions);
        
        // Calcul du salaire brut approximatif
        BigDecimal grossSalary = baseSalary;
        if (performanceBonus != null) grossSalary = grossSalary.add(performanceBonus);
        if (transportAllowance != null) grossSalary = grossSalary.add(transportAllowance);
        if (mealAllowance != null) grossSalary = grossSalary.add(mealAllowance);
        if (housingAllowance != null) grossSalary = grossSalary.add(housingAllowance);
        if (otherAllowances != null) grossSalary = grossSalary.add(otherAllowances);
        
        return totalDeductions.compareTo(grossSalary) <= 0;
    }

    @AssertTrue(message = "Les heures supplémentaires nécessitent des heures normales")
    public boolean isValidOvertimeHours() {
        if (overtimeHours == null || overtimeHours.compareTo(BigDecimal.ZERO) == 0) {
            return true;
        }
        return regularHours != null && regularHours.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * DTO pour les ajustements de paie
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PayrollAdjustmentRequest {

        @NotNull(message = "Le type d'ajustement est obligatoire")
        private String adjustmentType;

        @NotBlank(message = "La description de l'ajustement est obligatoire")
        @Size(max = 500, message = "La description ne peut pas dépasser 500 caractères")
        private String description;

        @NotNull(message = "Le montant de l'ajustement est obligatoire")
        @Digits(integer = 10, fraction = 2, message = "Le montant doit avoir au maximum 10 chiffres entiers et 2 décimales")
        private BigDecimal amount;

        @Size(max = 1000, message = "La raison ne peut pas dépasser 1000 caractères")
        private String reason;
    }
}