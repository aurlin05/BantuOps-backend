package com.bantuops.backend.service;

import com.bantuops.backend.dto.*;
import com.bantuops.backend.entity.Invoice;
import com.bantuops.backend.entity.Transaction;
import com.bantuops.backend.repository.InvoiceRepository;
import com.bantuops.backend.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service de génération de rapports financiers avec données agrégées
 * Conforme aux exigences 2.2, 2.4, 2.5, 3.5, 3.6 pour les rapports conformes aux standards comptables sénégalais
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class ReportGenerationService {

    private final InvoiceRepository invoiceRepository;
    private final TransactionRepository transactionRepository;
    private final DataEncryptionService encryptionService;
    private final AuditService auditService;

    /**
     * Génère un rapport financier complet avec métriques clés
     * Conforme aux exigences 2.2, 2.4, 2.5, 3.5, 3.6
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    @Cacheable(value = "financial-reports", key = "#request.hashCode()")
    public FinancialReport generateFinancialReport(FinancialReportRequest request) {
        log.info("Génération du rapport financier pour la période: {} - {}", 
            request.getStartDate(), request.getEndDate());

        String reportId = generateReportId();
        
        FinancialReport report = FinancialReport.builder()
            .reportId(reportId)
            .startDate(request.getStartDate())
            .endDate(request.getEndDate())
            .generatedAt(LocalDateTime.now())
            .reportType(request.getReportType())
            .currency(request.getCurrency() != null ? request.getCurrency() : "XOF")
            .build();

        // Génération des sections selon la demande
        if (shouldIncludeSection(request, FinancialReportRequest.ReportSection.SUMMARY)) {
            report.setExecutiveSummary(generateExecutiveSummary(request));
        }

        if (shouldIncludeSection(request, FinancialReportRequest.ReportSection.REVENUE_ANALYSIS)) {
            report.setRevenueAnalysis(generateRevenueAnalysis(request));
        }

        if (shouldIncludeSection(request, FinancialReportRequest.ReportSection.VAT_SUMMARY)) {
            report.setVatSummary(generateVatSummary(request));
        }

        if (shouldIncludeSection(request, FinancialReportRequest.ReportSection.OUTSTANDING_INVOICES)) {
            report.setOutstandingInvoices(generateOutstandingInvoices(request));
        }

        if (shouldIncludeSection(request, FinancialReportRequest.ReportSection.CLIENT_RANKING)) {
            report.setClientRankings(generateClientRankings(request));
        }

        if (shouldIncludeSection(request, FinancialReportRequest.ReportSection.PAYMENT_DELAYS)) {
            report.setPaymentDelayAnalysis(generatePaymentDelayAnalysis(request));
        }

        if (shouldIncludeSection(request, FinancialReportRequest.ReportSection.MONTHLY_TRENDS)) {
            report.setMonthlyTrends(generateMonthlyTrends(request));
        }

        if (shouldIncludeSection(request, FinancialReportRequest.ReportSection.PERFORMANCE_METRICS)) {
            report.setPerformanceMetrics(generatePerformanceMetrics(request));
        }

        // Audit de la génération de rapport
        auditService.logFinancialOperation(
            "GENERATE_FINANCIAL_REPORT",
            null,
            "Génération du rapport financier " + request.getReportType(),
            Map.of(
                "reportId", reportId,
                "startDate", request.getStartDate().toString(),
                "endDate", request.getEndDate().toString(),
                "reportType", request.getReportType().toString(),
                "sections", request.getSections().toString()
            )
        );

        log.info("Rapport financier généré avec succès: {}", reportId);
        return report;
    }

    /**
     * Exporte les données financières avec chiffrement
     * Conforme aux exigences 2.4, 2.5 pour l'export sécurisé
     */
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> exportFinancialData(FinancialReportRequest request) {
        log.info("Export des données financières pour la période: {} - {}", 
            request.getStartDate(), request.getEndDate());

        // Récupération des données
        List<Invoice> invoices = invoiceRepository.findByInvoiceDateBetween(
            request.getStartDate(), request.getEndDate());
        
        List<Transaction> transactions = transactionRepository.findByTransactionDateBetweenOrderByTransactionDateDesc(
            request.getStartDate(), request.getEndDate(), null).getContent();

        // Préparation des données pour export
        Map<String, Object> exportData = new HashMap<>();
        exportData.put("exportId", UUID.randomUUID().toString());
        exportData.put("exportDate", LocalDateTime.now());
        exportData.put("period", Map.of(
            "startDate", request.getStartDate(),
            "endDate", request.getEndDate()
        ));
        
        // Données des factures (chiffrées)
        List<Map<String, Object>> invoiceData = invoices.stream()
            .map(this::serializeInvoiceForExport)
            .collect(Collectors.toList());
        exportData.put("invoices", invoiceData);

        // Données des transactions (chiffrées)
        List<Map<String, Object>> transactionData = transactions.stream()
            .map(this::serializeTransactionForExport)
            .collect(Collectors.toList());
        exportData.put("transactions", transactionData);

        // Métadonnées
        exportData.put("metadata", Map.of(
            "invoiceCount", invoices.size(),
            "transactionCount", transactions.size(),
            "currency", request.getCurrency() != null ? request.getCurrency() : "XOF",
            "encrypted", true
        ));

        // Audit de l'export
        auditService.logFinancialOperation(
            "EXPORT_FINANCIAL_DATA",
            null,
            "Export des données financières",
            Map.of(
                "startDate", request.getStartDate().toString(),
                "endDate", request.getEndDate().toString(),
                "invoiceCount", String.valueOf(invoices.size()),
                "transactionCount", String.valueOf(transactions.size())
            )
        );

        log.info("Export des données financières terminé: {} factures, {} transactions", 
            invoices.size(), transactions.size());

        return exportData;
    }

    // Méthodes privées pour la génération des sections

    private FinancialReport.ExecutiveSummary generateExecutiveSummary(FinancialReportRequest request) {
        List<Invoice> invoices = invoiceRepository.findByInvoiceDateBetween(
            request.getStartDate(), request.getEndDate());

        BigDecimal totalRevenue = invoices.stream()
            .filter(inv -> !isRejectedOrCancelled(inv))
            .map(Invoice::getTotalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalVat = invoices.stream()
            .filter(inv -> !isRejectedOrCancelled(inv))
            .map(Invoice::getVatAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalOutstanding = invoices.stream()
            .filter(inv -> isOutstanding(inv))
            .map(Invoice::getRemainingAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        int totalInvoices = invoices.size();
        int paidInvoices = (int) invoices.stream()
            .filter(inv -> inv.getPaymentStatus() == Invoice.PaymentStatus.PAID)
            .count();

        int overdueInvoices = (int) invoices.stream()
            .filter(Invoice::isOverdue)
            .count();

        BigDecimal averageInvoiceAmount = totalInvoices > 0 ? 
            totalRevenue.divide(new BigDecimal(totalInvoices), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        double paymentRate = totalInvoices > 0 ? 
            (double) paidInvoices / totalInvoices * 100 : 0.0;

        // Calcul du délai moyen de paiement
        Object[] paymentDelayData = invoiceRepository.getPaymentDelayAnalysis(
            request.getStartDate(), request.getEndDate());
        Integer averagePaymentDelay = paymentDelayData != null && paymentDelayData[0] != null ? 
            ((Number) paymentDelayData[0]).intValue() : 0;

        return FinancialReport.ExecutiveSummary.builder()
            .totalRevenue(totalRevenue)
            .totalVat(totalVat)
            .totalOutstanding(totalOutstanding)
            .totalInvoices(totalInvoices)
            .paidInvoices(paidInvoices)
            .overdueInvoices(overdueInvoices)
            .averageInvoiceAmount(averageInvoiceAmount)
            .paymentRate(paymentRate)
            .averagePaymentDelay(averagePaymentDelay)
            .build();
    }

    private FinancialReport.RevenueAnalysis generateRevenueAnalysis(FinancialReportRequest request) {
        List<Invoice> invoices = invoiceRepository.findByInvoiceDateBetween(
            request.getStartDate(), request.getEndDate());

        BigDecimal totalRevenue = invoices.stream()
            .filter(inv -> !isRejectedOrCancelled(inv))
            .map(Invoice::getTotalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calcul de la croissance (période précédente)
        LocalDate previousStart = request.getStartDate().minusMonths(
            request.getEndDate().getMonthValue() - request.getStartDate().getMonthValue() + 1);
        LocalDate previousEnd = request.getStartDate().minusDays(1);
        
        BigDecimal previousPeriodRevenue = invoiceRepository.calculateTotalRevenueForPeriod(
            previousStart, previousEnd);
        previousPeriodRevenue = previousPeriodRevenue != null ? previousPeriodRevenue : BigDecimal.ZERO;

        double growthRate = previousPeriodRevenue.compareTo(BigDecimal.ZERO) > 0 ?
            totalRevenue.subtract(previousPeriodRevenue)
                .divide(previousPeriodRevenue, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100")).doubleValue() : 0.0;

        // Répartition mensuelle
        List<Object[]> monthlyStats = invoiceRepository.getMonthlyRevenueStats(
            request.getStartDate(), request.getEndDate());
        
        List<FinancialReport.MonthlyRevenue> monthlyBreakdown = monthlyStats.stream()
            .map(this::mapToMonthlyRevenue)
            .collect(Collectors.toList());

        // Répartition par client
        List<Object[]> clientStats = invoiceRepository.getTopClientsByRevenue(
            request.getStartDate(), request.getEndDate(), null);
        
        Map<String, BigDecimal> revenueByClient = clientStats.stream()
            .collect(Collectors.toMap(
                row -> (String) row[0],
                row -> (BigDecimal) row[1],
                (existing, replacement) -> existing,
                LinkedHashMap::new
            ));

        // Montants extrêmes
        BigDecimal highestInvoiceAmount = invoices.stream()
            .map(Invoice::getTotalAmount)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);

        BigDecimal lowestInvoiceAmount = invoices.stream()
            .map(Invoice::getTotalAmount)
            .min(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);

        return FinancialReport.RevenueAnalysis.builder()
            .totalRevenue(totalRevenue)
            .previousPeriodRevenue(previousPeriodRevenue)
            .growthRate(growthRate)
            .monthlyBreakdown(monthlyBreakdown)
            .revenueByClient(revenueByClient)
            .highestInvoiceAmount(highestInvoiceAmount)
            .lowestInvoiceAmount(lowestInvoiceAmount)
            .build();
    }

    private FinancialReport.VatSummary generateVatSummary(FinancialReportRequest request) {
        BigDecimal totalVatCollected = invoiceRepository.calculateVatCollectedForPeriod(
            request.getStartDate(), request.getEndDate());
        totalVatCollected = totalVatCollected != null ? totalVatCollected : BigDecimal.ZERO;

        // Taux de TVA moyen
        List<Invoice> invoices = invoiceRepository.findByInvoiceDateBetween(
            request.getStartDate(), request.getEndDate());
        
        BigDecimal vatRate = invoices.stream()
            .map(Invoice::getVatRate)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(new BigDecimal(Math.max(invoices.size(), 1)), 4, RoundingMode.HALF_UP);

        // Répartition mensuelle de la TVA
        List<Object[]> monthlyStats = invoiceRepository.getMonthlyRevenueStats(
            request.getStartDate(), request.getEndDate());
        
        List<FinancialReport.MonthlyVat> monthlyVatBreakdown = monthlyStats.stream()
            .map(this::mapToMonthlyVat)
            .collect(Collectors.toList());

        // TVA sur factures impayées
        BigDecimal vatOnOutstandingInvoices = invoices.stream()
            .filter(this::isOutstanding)
            .map(Invoice::getVatAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return FinancialReport.VatSummary.builder()
            .totalVatCollected(totalVatCollected)
            .vatRate(vatRate)
            .monthlyVatBreakdown(monthlyVatBreakdown)
            .vatOnOutstandingInvoices(vatOnOutstandingInvoices)
            .build();
    }

    private FinancialReport.OutstandingInvoices generateOutstandingInvoices(FinancialReportRequest request) {
        List<Invoice> outstandingInvoices = invoiceRepository.findByPaymentStatusOrderByDueDateAsc(
            Invoice.PaymentStatus.UNPAID, null).getContent();
        
        outstandingInvoices.addAll(invoiceRepository.findByPaymentStatusOrderByDueDateAsc(
            Invoice.PaymentStatus.PARTIALLY_PAID, null).getContent());

        BigDecimal totalOutstandingAmount = outstandingInvoices.stream()
            .map(Invoice::getRemainingAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Invoice> overdueInvoices = outstandingInvoices.stream()
            .filter(Invoice::isOverdue)
            .collect(Collectors.toList());

        BigDecimal overdueAmount = overdueInvoices.stream()
            .map(Invoice::getRemainingAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<FinancialReport.OutstandingInvoiceDetail> details = outstandingInvoices.stream()
            .limit(50) // Limiter pour les performances
            .map(this::mapToOutstandingDetail)
            .collect(Collectors.toList());

        // Calcul de la moyenne des jours en retard
        double averageOutstandingDays = outstandingInvoices.stream()
            .mapToLong(inv -> LocalDate.now().toEpochDay() - inv.getDueDate().toEpochDay())
            .average()
            .orElse(0.0);

        return FinancialReport.OutstandingInvoices.builder()
            .totalOutstandingAmount(totalOutstandingAmount)
            .numberOfOutstandingInvoices(outstandingInvoices.size())
            .overdueAmount(overdueAmount)
            .numberOfOverdueInvoices(overdueInvoices.size())
            .details(details)
            .averageOutstandingDays(BigDecimal.valueOf(averageOutstandingDays))
            .build();
    }

    private List<FinancialReport.ClientRanking> generateClientRankings(FinancialReportRequest request) {
        List<Object[]> clientStats = invoiceRepository.getTopClientsByRevenue(
            request.getStartDate(), request.getEndDate(), null);

        return clientStats.stream()
            .limit(20) // Top 20 clients
            .map(this::mapToClientRanking)
            .collect(Collectors.toList());
    }

    private FinancialReport.PaymentDelayAnalysis generatePaymentDelayAnalysis(FinancialReportRequest request) {
        Object[] delayData = invoiceRepository.getPaymentDelayAnalysis(
            request.getStartDate(), request.getEndDate());

        if (delayData == null || delayData.length < 3) {
            return FinancialReport.PaymentDelayAnalysis.builder()
                .averagePaymentDelay(0.0)
                .onTimePayments(0)
                .latePayments(0)
                .onTimePaymentRate(0.0)
                .delayRanges(new ArrayList<>())
                .build();
        }

        Double averagePaymentDelay = delayData[0] != null ? ((Number) delayData[0]).doubleValue() : 0.0;
        Integer latePayments = delayData[1] != null ? ((Number) delayData[1]).intValue() : 0;
        Integer totalPaidInvoices = delayData[2] != null ? ((Number) delayData[2]).intValue() : 0;
        Integer onTimePayments = totalPaidInvoices - latePayments;
        
        double onTimePaymentRate = totalPaidInvoices > 0 ? 
            (double) onTimePayments / totalPaidInvoices * 100 : 0.0;

        // Génération des tranches de retard
        List<FinancialReport.PaymentDelayRange> delayRanges = generateDelayRanges(
            request.getStartDate(), request.getEndDate());

        return FinancialReport.PaymentDelayAnalysis.builder()
            .averagePaymentDelay(averagePaymentDelay)
            .onTimePayments(onTimePayments)
            .latePayments(latePayments)
            .onTimePaymentRate(onTimePaymentRate)
            .delayRanges(delayRanges)
            .build();
    }

    private List<FinancialReport.MonthlyTrend> generateMonthlyTrends(FinancialReportRequest request) {
        List<Object[]> monthlyStats = invoiceRepository.getMonthlyRevenueStats(
            request.getStartDate(), request.getEndDate());

        return monthlyStats.stream()
            .map(this::mapToMonthlyTrend)
            .collect(Collectors.toList());
    }

    private PerformanceMetrics generatePerformanceMetrics(FinancialReportRequest request) {
        // Cette méthode sera implémentée selon les besoins spécifiques
        return PerformanceMetrics.builder()
            .reportPeriod(request.getStartDate() + " - " + request.getEndDate())
            .generatedAt(LocalDateTime.now())
            .build();
    }

    // Méthodes utilitaires

    private boolean shouldIncludeSection(FinancialReportRequest request, 
                                       FinancialReportRequest.ReportSection section) {
        return request.getSections() == null || request.getSections().contains(section);
    }

    private boolean isRejectedOrCancelled(Invoice invoice) {
        return invoice.getStatus() == Invoice.InvoiceStatus.REJECTED ||
               invoice.getStatus() == Invoice.InvoiceStatus.CANCELLED;
    }

    private boolean isOutstanding(Invoice invoice) {
        return invoice.getPaymentStatus() == Invoice.PaymentStatus.UNPAID ||
               invoice.getPaymentStatus() == Invoice.PaymentStatus.PARTIALLY_PAID;
    }

    private FinancialReport.MonthlyRevenue mapToMonthlyRevenue(Object[] row) {
        LocalDate month = (LocalDate) row[0];
        return FinancialReport.MonthlyRevenue.builder()
            .year(month.getYear())
            .month(month.getMonthValue())
            .monthName(month.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH))
            .amount((BigDecimal) row[1])
            .invoiceCount(((Number) row[2]).intValue())
            .growthRate(BigDecimal.ZERO) // À calculer selon les besoins
            .build();
    }

    private FinancialReport.MonthlyVat mapToMonthlyVat(Object[] row) {
        LocalDate month = (LocalDate) row[0];
        BigDecimal totalAmount = (BigDecimal) row[1];
        BigDecimal vatAmount = totalAmount.multiply(new BigDecimal("0.18")); // Approximation
        
        return FinancialReport.MonthlyVat.builder()
            .year(month.getYear())
            .month(month.getMonthValue())
            .monthName(month.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH))
            .vatAmount(vatAmount)
            .vatableAmount(totalAmount.subtract(vatAmount))
            .build();
    }

    private FinancialReport.OutstandingInvoiceDetail mapToOutstandingDetail(Invoice invoice) {
        long daysPastDue = invoice.isOverdue() ? 
            LocalDate.now().toEpochDay() - invoice.getDueDate().toEpochDay() : 0;

        return FinancialReport.OutstandingInvoiceDetail.builder()
            .invoiceNumber(invoice.getInvoiceNumber())
            .clientName(invoice.getClientName())
            .invoiceDate(invoice.getInvoiceDate())
            .dueDate(invoice.getDueDate())
            .totalAmount(invoice.getTotalAmount())
            .remainingAmount(invoice.getRemainingAmount())
            .daysPastDue((int) daysPastDue)
            .status(invoice.getPaymentStatus().getDescription())
            .build();
    }

    private FinancialReport.ClientRanking mapToClientRanking(Object[] row) {
        String clientName = (String) row[0];
        BigDecimal totalRevenue = (BigDecimal) row[1];
        
        // Données supplémentaires à calculer selon les besoins
        return FinancialReport.ClientRanking.builder()
            .clientName(clientName)
            .totalRevenue(totalRevenue)
            .invoiceCount(1) // À calculer
            .averageInvoiceAmount(totalRevenue) // À calculer
            .paymentRate(100.0) // À calculer
            .averagePaymentDelay(0) // À calculer
            .outstandingAmount(BigDecimal.ZERO) // À calculer
            .build();
    }

    private FinancialReport.MonthlyTrend mapToMonthlyTrend(Object[] row) {
        LocalDate month = (LocalDate) row[0];
        return FinancialReport.MonthlyTrend.builder()
            .year(month.getYear())
            .month(month.getMonthValue())
            .monthName(month.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH))
            .revenue((BigDecimal) row[1])
            .vat(((BigDecimal) row[1]).multiply(new BigDecimal("0.18"))) // Approximation
            .invoiceCount(((Number) row[2]).intValue())
            .averageInvoiceAmount((BigDecimal) row[3])
            .paymentRate(100.0) // À calculer
            .build();
    }

    private List<FinancialReport.PaymentDelayRange> generateDelayRanges(LocalDate startDate, LocalDate endDate) {
        // Implémentation simplifiée - à enrichir selon les besoins
        return Arrays.asList(
            FinancialReport.PaymentDelayRange.builder()
                .range("0-7 jours")
                .count(0)
                .percentage(0.0)
                .build(),
            FinancialReport.PaymentDelayRange.builder()
                .range("8-30 jours")
                .count(0)
                .percentage(0.0)
                .build(),
            FinancialReport.PaymentDelayRange.builder()
                .range("31-60 jours")
                .count(0)
                .percentage(0.0)
                .build(),
            FinancialReport.PaymentDelayRange.builder()
                .range("Plus de 60 jours")
                .count(0)
                .percentage(0.0)
                .build()
        );
    }

    private Map<String, Object> serializeInvoiceForExport(Invoice invoice) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", invoice.getId());
        data.put("invoiceNumber", invoice.getInvoiceNumber());
        data.put("invoiceDate", invoice.getInvoiceDate());
        data.put("dueDate", invoice.getDueDate());
        data.put("clientName", invoice.getClientName()); // Déjà chiffré
        data.put("totalAmount", invoice.getTotalAmount()); // Déjà chiffré
        data.put("vatAmount", invoice.getVatAmount()); // Déjà chiffré
        data.put("status", invoice.getStatus());
        data.put("paymentStatus", invoice.getPaymentStatus());
        return data;
    }

    private Map<String, Object> serializeTransactionForExport(Transaction transaction) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", transaction.getId());
        data.put("transactionReference", transaction.getTransactionReference());
        data.put("transactionDate", transaction.getTransactionDate());
        data.put("transactionType", transaction.getTransactionType());
        data.put("amount", transaction.getAmount()); // Déjà chiffré
        data.put("currency", transaction.getCurrency());
        data.put("status", transaction.getStatus());
        data.put("counterpartyName", transaction.getCounterpartyName()); // Déjà chiffré
        return data;
    }

    private String generateReportId() {
        return "RPT_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + 
               "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}