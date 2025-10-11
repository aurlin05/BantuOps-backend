package com.bantuops.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO pour les rapports de TVA destinés à la DGI
 * Conforme aux exigences 2.1, 2.3, 3.1, 3.2 pour les rapports fiscaux sénégalais
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VATReport {

    private String reportId;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime generatedAt;
    private String generatedBy;
    private String reportingPeriod;
    private String currency;

    // Résumé TVA
    private VATSummary vatSummary;

    // Détails par transaction
    private List<VATTransactionDetail> transactions;

    // Réconciliation
    private VATReconciliation reconciliation;

    // Conformité DGI
    private DGICompliance dgiCompliance;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VATSummary {
        private BigDecimal totalSalesExcludingVat;
        private BigDecimal totalVatCollected;
        private BigDecimal totalSalesIncludingVat;
        private BigDecimal standardRateVat;
        private BigDecimal reducedRateVat;
        private BigDecimal exemptSales;
        private BigDecimal vatToPay;
        private BigDecimal vatCredit;
        private Integer numberOfTransactions;
        private BigDecimal averageTransactionAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VATTransactionDetail {
        private String transactionId;
        private LocalDate transactionDate;
        private String invoiceNumber;
        private String clientName;
        private String clientTaxNumber;
        private BigDecimal amountExcludingVat;
        private BigDecimal vatRate;
        private BigDecimal vatAmount;
        private BigDecimal totalAmount;
        private String transactionType;
        private Boolean isExempt;
        private String exemptionReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VATReconciliation {
        private BigDecimal bookVatAmount;
        private BigDecimal calculatedVatAmount;
        private BigDecimal variance;
        private BigDecimal variancePercentage;
        private List<ReconciliationItem> reconciliationItems;
        private Boolean isReconciled;
        private String[] reconciliationIssues;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReconciliationItem {
        private String description;
        private BigDecimal amount;
        private String type;
        private String explanation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DGICompliance {
        private Boolean isCompliant;
        private String complianceLevel;
        private List<ComplianceIssue> issues;
        private List<String> recommendations;
        private LocalDate submissionDeadline;
        private String dgiFormReference;
        private Boolean requiresAudit;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceIssue {
        private String issueType;
        private String description;
        private String severity;
        private String resolution;
        private BigDecimal potentialPenalty;
    }
}