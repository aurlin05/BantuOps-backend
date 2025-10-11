package com.bantuops.backend.dto;

import com.bantuops.backend.service.TaxCalculationService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO pour les taux de taxes sénégalais
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxRates {

    private BigDecimal ipresRate;
    private BigDecimal cssRate;
    private BigDecimal familyAllowanceRate;
    private BigDecimal minimumTaxableIncome;
    private TaxCalculationService.TaxBracket[] taxBrackets;
}