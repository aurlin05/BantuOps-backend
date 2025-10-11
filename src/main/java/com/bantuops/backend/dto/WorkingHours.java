package com.bantuops.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO pour les heures de travail
 * Utilisé dans les calculs de paie
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkingHours {

    @Builder.Default
    private BigDecimal regularHours = BigDecimal.ZERO;
    
    @Builder.Default
    private BigDecimal overtimeHours = BigDecimal.ZERO;
    
    @Builder.Default
    private BigDecimal nightHours = BigDecimal.ZERO;
    
    @Builder.Default
    private BigDecimal holidayHours = BigDecimal.ZERO;
    
    @Builder.Default
    private BigDecimal weekendHours = BigDecimal.ZERO;

    /**
     * Calcule le total des heures travaillées
     */
    public BigDecimal getTotalHours() {
        return regularHours
            .add(overtimeHours)
            .add(nightHours)
            .add(holidayHours)
            .add(weekendHours);
    }

    /**
     * Vérifie si les heures sont valides
     */
    public boolean isValid() {
        return regularHours.compareTo(BigDecimal.ZERO) >= 0 &&
               overtimeHours.compareTo(BigDecimal.ZERO) >= 0 &&
               nightHours.compareTo(BigDecimal.ZERO) >= 0 &&
               holidayHours.compareTo(BigDecimal.ZERO) >= 0 &&
               weekendHours.compareTo(BigDecimal.ZERO) >= 0;
    }
}