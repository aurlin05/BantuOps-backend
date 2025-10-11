package com.bantuops.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO pour les rapports financiers générés
 * Conforme aux exigences 2.2, 2.4, 2.5, 3.5, 3.6 pour les rapports sécurisés
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialReport {

    private String reportId;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime generatedAt;
    private String generatedBy;
    private FinancialReportRequest.ReportType reportType;
    private String currency;

    // Résumé exécutif
    private ExecutiveSummary executiveSummary;

    // Analyse du chiffre d'affaires
    private RevenueAnalysis revenueAnalysis;

    // Résumé TVA
    private VatSummary vatSummary;

    // Factures impayées
    private OutstandingInvoices outstandingInvoices;

    // Classement clients
    private List<ClientRanking> clientRankings;

    // Analyse des retards de paiement
    private PaymentDelayAnalysis paymentDelayAnalysis;

    // Tendances mensuelles
    private List<MonthlyTrend> monthlyTrends;

    // Métriques de performance
    private PerformanceMetrics performanceMetrics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutiveSummary {
        private BigDecimal totalRevenue;
        private BigDecimal totalVat;
        private BigDecimal totalOutstanding;
        private Integer totalInvoices;
        private Integer paidInvoices;
        private Integer overdueInvoices;
        private BigDecimal averageInvoiceAmount;
        private Double paymentRate;
        private Integer averagePaymentDelay;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevenueAnalysis {
        private BigDecimal totalRevenue;
        private BigDecimal previousPeriodRevenue;
        private Double growthRate;
        private List<MonthlyRevenue> monthlyBreakdown;
        private Map<String, BigDecimal> revenueByClient;
        private BigDecimal highestInvoiceAmount;
        private BigDecimal lowestInvoiceAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VatSummary {
        private BigDecimal totalVatCollected;
        private BigDecimal vatRate;
        private List<MonthlyVat> monthlyVatBreakdown;
        private BigDecimal vatOnOutstandingInvoices;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OutstandingInvoices {
        private BigDecimal totalOutstandingAmount;
        private Integer numberOfOutstandingInvoices;
        private BigDecimal overdueAmount;
        private Integer numberOfOverdueInvoices;
        private List<OutstandingInvoiceDetail> details;
        private BigDecimal averageOutstandingDays;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientRanking {
        private String clientName;
        private BigDecimal totalRevenue;
        private Integer invoiceCount;
        private BigDecimal averageInvoiceAmount;
        private Double paymentRate;
        private Integer averagePaymentDelay;
        private BigDecimal outstandingAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentDelayAnalysis {
        private Double averagePaymentDelay;
        private Integer onTimePayments;
        private Integer latePayments;
        private Double onTimePaymentRate;
        private List<PaymentDelayRange> delayRanges;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyTrend {
        private Integer year;
        private Integer month;
        private String monthName;
        private BigDecimal revenue;
        private BigDecimal vat;
        private Integer invoiceCount;
        private BigDecimal averageInvoiceAmount;
        private Double paymentRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyRevenue {
        private Integer year;
        private Integer month;
        private String monthName;
        private BigDecimal amount;
        private Integer invoiceCount;
        private BigDecimal growthRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyVat {
        private Integer year;
        private Integer month;
        private String monthName;
        private BigDecimal vatAmount;
        private BigDecimal vatableAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OutstandingInvoiceDetail {
        private String invoiceNumber;
        private String clientName;
        private LocalDate invoiceDate;
        private LocalDate dueDate;
        private BigDecimal totalAmount;
        private BigDecimal remainingAmount;
        private Integer daysPastDue;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentDelayRange {
        private String range;
        private Integer count;
        private Double percentage;
    }
}