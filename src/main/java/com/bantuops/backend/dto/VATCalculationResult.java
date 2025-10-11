package com.bantuops.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO pour les résultats de calcul de TVA
 * Conforme aux exigences 2.1, 2.3, 3.1, 3.2 pour les calculs fiscaux sénégalais
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VATCalculationResult {

    private String calculationId;
    private LocalDateTime calculatedAt;
    private LocalDate transactionDate;

    // Montants de base
    private BigDecimal amountExcludingVat;
    private BigDecimal vatRate;
    private BigDecimal vatAmount;
    private BigDecimal amountIncludingVat;
    private String currency;

    // Informations sur l'exemption
    private Boolean isVatExempt;
    private String exemptionCode;
    private String exemptionReason;
    private BigDecimal exemptionAmount;

    // Détails du calcul
    private VATBreakdown vatBreakdown;
    private VATCompliance compliance;
    private VATReporting reporting;

    // Informations client
    private String clientTaxNumber;
    private Boolean isValidTaxNumber;
    private String businessSector;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VATBreakdown {
        private BigDecimal baseAmount;
        private BigDecimal standardRateAmount;
        private BigDecimal reducedRateAmount;
        private BigDecimal exemptAmount;
        private BigDecimal totalVatAmount;
        private BigDecimal effectiveVatRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VATCompliance {
        private Boolean isCompliant;
        private String complianceStatus;
        private String[] complianceIssues;
        private String[] recommendations;
        private Boolean requiresDeclaration;
        private LocalDate declarationDueDate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VATReporting {
        private String reportingPeriod;
        private String vatRegime;
        private Boolean isSubjectToWithholding;
        private BigDecimal withholdingRate;
        private BigDecimal withholdingAmount;
        private String dgiReportingCode;
    }
}