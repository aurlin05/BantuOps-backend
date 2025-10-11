package com.bantuops.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO pour les exonérations fiscales sénégalaises
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxExemption {

    private Long employeeId;
    private BigDecimal grossSalary;

    @Builder.Default
    private BigDecimal newGraduateExemption = BigDecimal.ZERO;
    
    @Builder.Default
    private BigDecimal investmentExemption = BigDecimal.ZERO;
    
    @Builder.Default
    private BigDecimal familyExemption = BigDecimal.ZERO;
    
    @Builder.Default
    private BigDecimal disabilityExemption = BigDecimal.ZERO;
    
    @Builder.Default
    private BigDecimal totalExemption = BigDecimal.ZERO;

    private String exemptionReason;
    private String validityPeriod;
}