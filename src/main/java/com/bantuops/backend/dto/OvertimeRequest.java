package com.bantuops.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * DTO pour les demandes de calcul d'heures supplémentaires
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OvertimeRequest {

    @NotNull(message = "L'ID de l'employé est obligatoire")
    @Positive(message = "L'ID de l'employé doit être positif")
    private Long employeeId;

    @NotNull(message = "La période est obligatoire")
    private YearMonth period;

    @DecimalMin(value = "0.0", message = "Les heures supplémentaires normales ne peuvent pas être négatives")
    @Builder.Default
    private BigDecimal regularOvertimeHours = BigDecimal.ZERO;

    @DecimalMin(value = "0.0", message = "Les heures supplémentaires de nuit ne peuvent pas être négatives")
    @Builder.Default
    private BigDecimal nightOvertimeHours = BigDecimal.ZERO;

    @DecimalMin(value = "0.0", message = "Les heures supplémentaires de weekend ne peuvent pas être négatives")
    @Builder.Default
    private BigDecimal weekendOvertimeHours = BigDecimal.ZERO;

    @DecimalMin(value = "0.0", message = "Les heures supplémentaires de jours fériés ne peuvent pas être négatives")
    @Builder.Default
    private BigDecimal holidayOvertimeHours = BigDecimal.ZERO;

    private PerformanceMetrics performanceMetrics;

    private String notes;
}