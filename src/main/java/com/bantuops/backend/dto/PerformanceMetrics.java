package com.bantuops.backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO pour les métriques de performance d'un employé
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceMetrics {

    @DecimalMin(value = "0.0", message = "Le score de productivité doit être entre 0 et 100")
    @DecimalMax(value = "100.0", message = "Le score de productivité doit être entre 0 et 100")
    @Builder.Default
    private BigDecimal productivityScore = BigDecimal.ZERO;

    @DecimalMin(value = "0.0", message = "Le score de qualité doit être entre 0 et 100")
    @DecimalMax(value = "100.0", message = "Le score de qualité doit être entre 0 et 100")
    @Builder.Default
    private BigDecimal qualityScore = BigDecimal.ZERO;

    @DecimalMin(value = "0.0", message = "Le score d'assiduité doit être entre 0 et 100")
    @DecimalMax(value = "100.0", message = "Le score d'assiduité doit être entre 0 et 100")
    @Builder.Default
    private BigDecimal attendanceScore = BigDecimal.ZERO;

    @DecimalMin(value = "0.0", message = "Le score de collaboration doit être entre 0 et 100")
    @DecimalMax(value = "100.0", message = "Le score de collaboration doit être entre 0 et 100")
    @Builder.Default
    private BigDecimal collaborationScore = BigDecimal.ZERO;

    private String evaluationPeriod;
    private String evaluatedBy;
    private String comments;

    /**
     * Calcule le score global de performance
     */
    public BigDecimal calculateOverallScore() {
        return productivityScore
            .add(qualityScore)
            .add(attendanceScore)
            .add(collaborationScore)
            .divide(new BigDecimal("4"), 2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Vérifie si les métriques sont valides
     */
    public boolean isValid() {
        return productivityScore.compareTo(BigDecimal.ZERO) >= 0 &&
               productivityScore.compareTo(new BigDecimal("100")) <= 0 &&
               qualityScore.compareTo(BigDecimal.ZERO) >= 0 &&
               qualityScore.compareTo(new BigDecimal("100")) <= 0 &&
               attendanceScore.compareTo(BigDecimal.ZERO) >= 0 &&
               attendanceScore.compareTo(new BigDecimal("100")) <= 0;
    }
}