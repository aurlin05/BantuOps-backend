package com.bantuops.backend.service;

import com.bantuops.backend.dto.*;
import com.bantuops.backend.entity.Invoice;
import com.bantuops.backend.entity.InvoiceItem;
import com.bantuops.backend.exception.BusinessRuleException;
import com.bantuops.backend.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service financier principal avec gestion des données chiffrées
 * Conforme aux exigences 2.1, 2.2, 2.3, 3.1, 3.2 pour la sécurisation des
 * données financières
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class FinancialService {

    private final InvoiceRepository invoiceRepository;
    private final BusinessRuleValidator businessRuleValidator;
    private final AuditService auditService;
    private final DataEncryptionService encryptionService;

    // Taux de TVA sénégalais par défaut
    private static final BigDecimal DEFAULT_VAT_RATE = new BigDecimal("0.1800");
    private static final BigDecimal VAT_EXEMPTION_THRESHOLD = new BigDecimal("1000000");

    /**
     * Crée une facture avec validation fiscale sénégalaise
     * Conforme aux exigences 2.1, 2.2, 2.3, 3.1, 3.2
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'FINANCE')")
    public Invoice createInvoice(InvoiceRequest request) {
        log.info("Création d'une nouvelle facture: {}", request.getInvoiceNumber());

        // Validation des règles métier
        var validationResult = businessRuleValidator.validateInvoiceData(request);
        if (!validationResult.isValid()) {
            throw new BusinessRuleException("Données de facture invalides: " +
                    String.join(", ", validationResult.getErrors()));
        }

        // Vérification de l'unicité du numéro de facture
        if (invoiceRepository.existsByInvoiceNumber(request.getInvoiceNumber())) {
            throw new BusinessRuleException("Le numéro de facture existe déjà: " + request.getInvoiceNumber());
        }

        // Calcul des montants
        InvoiceCalculation calculation = calculateInvoiceAmounts(request);

        // Création de l'entité facture
        Invoice invoice = Invoice.builder()
                .invoiceNumber(request.getInvoiceNumber())
                .invoiceDate(request.getInvoiceDate())
                .dueDate(request.getDueDate())
                .clientName(request.getClientName())
                .clientAddress(request.getClientAddress())
                .clientTaxNumber(request.getClientTaxNumber())
                .clientEmail(request.getClientEmail())
                .clientPhone(request.getClientPhone())
                .subtotalAmount(calculation.getSubtotalAmount())
                .vatAmount(calculation.getVatAmount())
                .vatRate(calculation.getVatRate())
                .discountAmount(calculation.getDiscountAmount())
                .totalAmount(calculation.getTotalAmount())
                .remainingAmount(calculation.getTotalAmount())
                .currency(request.getCurrency())
                .description(request.getDescription())
                .termsAndConditions(request.getTermsAndConditions())
                .status(Invoice.InvoiceStatus.DRAFT)
                .paymentStatus(Invoice.PaymentStatus.UNPAID)
                .build();

        // Création des éléments de facture
        List<InvoiceItem> items = request.getItems().stream()
                .map(itemRequest -> createInvoiceItem(itemRequest, invoice))
                .collect(Collectors.toList());

        invoice.setItems(items);

        // Sauvegarde
        Invoice savedInvoice = invoiceRepository.save(invoice);

        // Audit
        auditService.logFinancialOperation(
                "CREATE_INVOICE",
                savedInvoice.getId(),
                Map.of(
                        "description", "Création de la facture " + savedInvoice.getInvoiceNumber(),
                        "invoiceNumber", savedInvoice.getInvoiceNumber(),
                        "totalAmount", savedInvoice.getTotalAmount().toString(),
                        "clientName", savedInvoice.getClientName()));

        log.info("Facture créée avec succès: ID={}, Numéro={}",
                savedInvoice.getId(), savedInvoice.getInvoiceNumber());

        return savedInvoice;
    }

    /**
     * Calcule les montants d'une facture avec TVA sénégalaise
     * Conforme aux exigences 2.1, 2.3, 3.1, 3.2
     */
    @Cacheable(value = "invoice-calculations", key = "#request.hashCode()")
    public InvoiceCalculation calculateInvoiceAmounts(InvoiceRequest request) {
        log.debug("Calcul des montants pour la facture: {}", request.getInvoiceNumber());

        // Calcul du sous-total des éléments
        BigDecimal subtotal = request.getItems().stream()
                .map(item -> item.getQuantity().multiply(item.getUnitPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Application de la remise
        BigDecimal discountAmount = request.getDiscountAmount() != null ? request.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal discountedSubtotal = subtotal.subtract(discountAmount);

        // Calcul de la TVA selon les règles sénégalaises
        BigDecimal vatRate = request.getVatRate() != null ? request.getVatRate() : DEFAULT_VAT_RATE;

        // Vérification des exemptions de TVA
        boolean isVatExempt = isVatExempt(request, discountedSubtotal);
        BigDecimal vatAmount = isVatExempt ? BigDecimal.ZERO
                : discountedSubtotal.multiply(vatRate).setScale(2, RoundingMode.HALF_UP);

        // Calcul du montant total
        BigDecimal totalAmount = discountedSubtotal.add(vatAmount);

        // Calculs détaillés par élément
        List<InvoiceCalculation.ItemCalculation> itemCalculations = request.getItems().stream()
                .map(item -> calculateItemAmount(item, discountAmount, subtotal, vatRate, isVatExempt))
                .collect(Collectors.toList());

        return InvoiceCalculation.builder()
                .subtotalAmount(subtotal)
                .discountAmount(discountAmount)
                .discountedSubtotal(discountedSubtotal)
                .vatRate(vatRate)
                .vatAmount(vatAmount)
                .totalAmount(totalAmount)
                .currency(request.getCurrency())
                .itemCalculations(itemCalculations)
                .vatBreakdown(createVatBreakdown(discountedSubtotal, vatRate, vatAmount, isVatExempt))
                .discountBreakdown(createDiscountBreakdown(subtotal, discountAmount))
                .build();
    }

    /**
     * Génère un rapport financier avec permissions
     * Conforme aux exigences 2.2, 2.4, 2.5, 3.5, 3.6
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    @Cacheable(value = "financial-reports", key = "#request.hashCode()")
    public FinancialReport generateFinancialReport(FinancialReportRequest request) {
        log.info("Génération du rapport financier pour la période: {} - {}",
                request.getStartDate(), request.getEndDate());

        String reportId = UUID.randomUUID().toString();

        FinancialReport report = FinancialReport.builder()
                .reportId(reportId)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .generatedAt(LocalDateTime.now())
                .reportType(request.getReportType())
                .currency(request.getCurrency() != null ? request.getCurrency() : "XOF")
                .build();

        // Génération des sections selon la demande
        if (request.getSections().contains(FinancialReportRequest.ReportSection.SUMMARY)) {
            report.setExecutiveSummary(generateExecutiveSummary(request));
        }

        if (request.getSections().contains(FinancialReportRequest.ReportSection.REVENUE_ANALYSIS)) {
            report.setRevenueAnalysis(generateRevenueAnalysis(request));
        }

        if (request.getSections().contains(FinancialReportRequest.ReportSection.VAT_SUMMARY)) {
            report.setVatSummary(generateVatSummary(request));
        }

        if (request.getSections().contains(FinancialReportRequest.ReportSection.OUTSTANDING_INVOICES)) {
            report.setOutstandingInvoices(generateOutstandingInvoices(request));
        }

        if (request.getSections().contains(FinancialReportRequest.ReportSection.CLIENT_RANKING)) {
            report.setClientRankings(generateClientRankings(request));
        }

        if (request.getSections().contains(FinancialReportRequest.ReportSection.PAYMENT_DELAYS)) {
            report.setPaymentDelayAnalysis(generatePaymentDelayAnalysis(request));
        }

        if (request.getSections().contains(FinancialReportRequest.ReportSection.MONTHLY_TRENDS)) {
            report.setMonthlyTrends(generateMonthlyTrends(request));
        }

        if (request.getSections().contains(FinancialReportRequest.ReportSection.PERFORMANCE_METRICS)) {
            report.setPerformanceMetrics(generatePerformanceMetrics(request));
        }

        // Audit de la génération de rapport
        auditService.logFinancialOperation(
                "GENERATE_REPORT",
                null,
                Map.of(
                        "description", "Génération du rapport financier " + request.getReportType(),
                        "reportId", reportId,
                        "startDate", request.getStartDate().toString(),
                        "endDate", request.getEndDate().toString(),
                        "reportType", request.getReportType().toString()));

        log.info("Rapport financier généré avec succès: {}", reportId);
        return report;
    }

    /**
     * Récupère l'historique des transactions avec filtres sécurisés
     * Conforme aux exigences 2.1, 2.2, 2.5, 7.4, 7.5
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Page<Invoice> getTransactionHistory(LocalDate startDate, LocalDate endDate,
            Invoice.InvoiceStatus status, Pageable pageable) {
        log.debug("Récupération de l'historique des transactions: {} - {}", startDate, endDate);

        Page<Invoice> transactions;

        if (status != null) {
            transactions = invoiceRepository.findByInvoiceDateBetweenAndStatusOrderByInvoiceDateDesc(
                    startDate, endDate, status, pageable);
        } else {
            transactions = invoiceRepository.findByInvoiceDateBetweenOrderByInvoiceDateDesc(
                    startDate, endDate, pageable);
        }

        // Audit de l'accès aux données
        auditService.logDataAccess(
                "TRANSACTION_HISTORY_ACCESS",
                "Consultation de l'historique des transactions",
                Map.of(
                        "startDate", startDate.toString(),
                        "endDate", endDate.toString(),
                        "status", status != null ? status.toString() : "ALL",
                        "pageSize", String.valueOf(pageable.getPageSize()),
                        "pageNumber", String.valueOf(pageable.getPageNumber())));

        return transactions;
    }

    // Méthodes privées pour les calculs et générations

    private InvoiceItem createInvoiceItem(InvoiceRequest.InvoiceItemRequest itemRequest, Invoice invoice) {
        BigDecimal totalPrice = itemRequest.getQuantity().multiply(itemRequest.getUnitPrice());

        return InvoiceItem.builder()
                .invoice(invoice)
                .description(itemRequest.getDescription())
                .quantity(itemRequest.getQuantity())
                .unitPrice(itemRequest.getUnitPrice())
                .totalPrice(totalPrice)
                .unit(itemRequest.getUnit())
                .itemOrder(itemRequest.getItemOrder())
                .build();
    }

    private boolean isVatExempt(InvoiceRequest request, BigDecimal amount) {
        // Règles d'exemption de TVA sénégalaises
        if ("XOF".equals(request.getCurrency()) && amount.compareTo(VAT_EXEMPTION_THRESHOLD) <= 0) {
            return false; // Pas d'exemption pour les petits montants
        }

        // Autres règles d'exemption selon la législation sénégalaise
        return false; // À implémenter selon les règles spécifiques
    }

    private InvoiceCalculation.ItemCalculation calculateItemAmount(
            InvoiceRequest.InvoiceItemRequest item,
            BigDecimal totalDiscount,
            BigDecimal subtotal,
            BigDecimal vatRate,
            boolean isVatExempt) {

        BigDecimal lineTotal = item.getQuantity().multiply(item.getUnitPrice());

        // Répartition proportionnelle de la remise
        BigDecimal itemDiscountAmount = BigDecimal.ZERO;
        if (totalDiscount.compareTo(BigDecimal.ZERO) > 0 && subtotal.compareTo(BigDecimal.ZERO) > 0) {
            itemDiscountAmount = lineTotal.multiply(totalDiscount)
                    .divide(subtotal, 2, RoundingMode.HALF_UP);
        }

        BigDecimal discountedAmount = lineTotal.subtract(itemDiscountAmount);
        BigDecimal vatAmount = isVatExempt ? BigDecimal.ZERO
                : discountedAmount.multiply(vatRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = discountedAmount.add(vatAmount);

        return InvoiceCalculation.ItemCalculation.builder()
                .description(item.getDescription())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .lineTotal(lineTotal)
                .discountAmount(itemDiscountAmount)
                .discountedAmount(discountedAmount)
                .vatAmount(vatAmount)
                .totalAmount(totalAmount)
                .itemOrder(item.getItemOrder())
                .build();
    }

    private InvoiceCalculation.VatBreakdown createVatBreakdown(
            BigDecimal vatableAmount, BigDecimal vatRate, BigDecimal vatAmount, boolean isVatExempt) {

        return InvoiceCalculation.VatBreakdown.builder()
                .vatableAmount(vatableAmount)
                .vatRate(vatRate)
                .vatAmount(vatAmount)
                .totalIncludingVat(vatableAmount.add(vatAmount))
                .isVatExempt(isVatExempt)
                .exemptionReason(isVatExempt ? "Exemption selon la législation sénégalaise" : null)
                .build();
    }

    private InvoiceCalculation.DiscountBreakdown createDiscountBreakdown(
            BigDecimal originalAmount, BigDecimal discountAmount) {

        BigDecimal discountPercentage = BigDecimal.ZERO;
        if (originalAmount.compareTo(BigDecimal.ZERO) > 0) {
            discountPercentage = discountAmount.multiply(new BigDecimal("100"))
                    .divide(originalAmount, 2, RoundingMode.HALF_UP);
        }

        return InvoiceCalculation.DiscountBreakdown.builder()
                .originalAmount(originalAmount)
                .discountAmount(discountAmount)
                .discountPercentage(discountPercentage)
                .finalAmount(originalAmount.subtract(discountAmount))
                .discountType("FIXED_AMOUNT")
                .build();
    }

    // Méthodes de génération des sections de rapport (à implémenter dans les
    // prochaines étapes)

    private FinancialReport.ExecutiveSummary generateExecutiveSummary(FinancialReportRequest request) {
        // Implémentation à venir
        return FinancialReport.ExecutiveSummary.builder().build();
    }

    private FinancialReport.RevenueAnalysis generateRevenueAnalysis(FinancialReportRequest request) {
        // Implémentation à venir
        return FinancialReport.RevenueAnalysis.builder().build();
    }

    private FinancialReport.VatSummary generateVatSummary(FinancialReportRequest request) {
        // Implémentation à venir
        return FinancialReport.VatSummary.builder().build();
    }

    private FinancialReport.OutstandingInvoices generateOutstandingInvoices(FinancialReportRequest request) {
        // Implémentation à venir
        return FinancialReport.OutstandingInvoices.builder().build();
    }

    private List<FinancialReport.ClientRanking> generateClientRankings(FinancialReportRequest request) {
        // Implémentation à venir
        return new ArrayList<>();
    }

    private FinancialReport.PaymentDelayAnalysis generatePaymentDelayAnalysis(FinancialReportRequest request) {
        // Implémentation à venir
        return FinancialReport.PaymentDelayAnalysis.builder().build();
    }

    private List<FinancialReport.MonthlyTrend> generateMonthlyTrends(FinancialReportRequest request) {
        // Implémentation à venir
        return new ArrayList<>();
    }

    private PerformanceMetrics generatePerformanceMetrics(FinancialReportRequest request) {
        // Implémentation à venir
        return PerformanceMetrics.builder().build();
    }
}