package com.bantuops.backend.service;

import com.bantuops.backend.dto.*;
import com.bantuops.backend.entity.Invoice;
import com.bantuops.backend.exception.BusinessRuleException;
import com.bantuops.backend.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service de calcul de TVA avec taux sénégalais (18%)
 * Conforme aux exigences 2.1, 2.3, 3.1, 3.2 pour les calculs fiscaux sénégalais
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class VATCalculationService {

    private final InvoiceRepository invoiceRepository;
    private final SenegalTaxNumberValidator taxNumberValidator;
    private final AuditService auditService;

    // Taux de TVA sénégalais
    private static final BigDecimal SENEGAL_STANDARD_VAT_RATE = new BigDecimal("0.1800"); // 18%
    private static final BigDecimal SENEGAL_REDUCED_VAT_RATE = new BigDecimal("0.1000"); // 10% pour certains produits
    private static final BigDecimal VAT_EXEMPTION_THRESHOLD = new BigDecimal("1000000"); // 1M XOF
    private static final BigDecimal WITHHOLDING_TAX_RATE = new BigDecimal("0.05"); // 5% retenue à la source

    // Secteurs avec taux réduit
    private static final Set<String> REDUCED_VAT_SECTORS = Set.of(
            "AGRICULTURE", "EDUCATION", "HEALTH", "TRANSPORT_PUBLIC");

    // Secteurs exemptés
    private static final Set<String> EXEMPT_SECTORS = Set.of(
            "BANKING", "INSURANCE", "MEDICAL_SERVICES", "EDUCATION_PUBLIC");

    /**
     * Calcule la TVA selon les règles sénégalaises
     * Conforme aux exigences 2.1, 2.3, 3.1, 3.2
     */
    @Cacheable(value = "vat-calculations", key = "#request.hashCode()")
    public VATCalculationResult calculateVAT(VATCalculationRequest request) {
        log.debug("Calcul de la TVA pour le montant: {} {}",
                request.getAmountExcludingVat(), request.getCurrency());

        // Validation du numéro fiscal si fourni
        boolean isValidTaxNumber = validateTaxNumber(request.getClientTaxNumber());

        // Détermination du taux de TVA applicable
        BigDecimal applicableVatRate = determineVatRate(request);

        // Vérification des exemptions
        boolean isVatExempt = isVatExempt(request);
        String exemptionReason = getExemptionReason(request);

        // Calcul de la TVA
        BigDecimal vatAmount = BigDecimal.ZERO;
        if (!isVatExempt) {
            vatAmount = request.getAmountExcludingVat()
                    .multiply(applicableVatRate)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal amountIncludingVat = request.getAmountExcludingVat().add(vatAmount);

        // Génération de l'ID de calcul
        String calculationId = generateCalculationId();

        // Construction du résultat
        VATCalculationResult result = VATCalculationResult.builder()
                .calculationId(calculationId)
                .calculatedAt(LocalDateTime.now())
                .transactionDate(request.getTransactionDate())
                .amountExcludingVat(request.getAmountExcludingVat())
                .vatRate(applicableVatRate)
                .vatAmount(vatAmount)
                .amountIncludingVat(amountIncludingVat)
                .currency(request.getCurrency())
                .isVatExempt(isVatExempt)
                .exemptionReason(exemptionReason)
                .clientTaxNumber(request.getClientTaxNumber())
                .isValidTaxNumber(isValidTaxNumber)
                .businessSector(request.getBusinessSector())
                .vatBreakdown(createVatBreakdown(request, applicableVatRate, vatAmount))
                .compliance(createComplianceInfo(request, vatAmount))
                .reporting(createReportingInfo(request, vatAmount))
                .build();

        // Audit du calcul
        auditService.logFinancialOperation(
                "VAT_CALCULATION",
                null,
                Map.of(
                        "calculationId", calculationId,
                        "amount", request.getAmountExcludingVat().toString(),
                        "vatRate", applicableVatRate.toString(),
                        "vatAmount", vatAmount.toString(),
                        "currency", request.getCurrency(),
                        "description", "Calcul de TVA effectué"));

        log.debug("TVA calculée: {} {} (taux: {}%)",
                vatAmount, request.getCurrency(), applicableVatRate.multiply(new BigDecimal("100")));

        return result;
    }

    /**
     * Valide un numéro de TVA sénégalais
     * Conforme aux exigences 2.1, 2.3, 3.1, 3.2
     */
    public boolean validateVATNumber(String vatNumber) {
        if (vatNumber == null || vatNumber.trim().isEmpty()) {
            return false;
        }

        // Validation du format sénégalais (13 chiffres)
        if (!vatNumber.matches("^[0-9]{13}$")) {
            return false;
        }

        // Validation avec le service de validation des numéros fiscaux
        return taxNumberValidator.isValid(vatNumber);
    }

    /**
     * Génère un rapport de TVA pour la DGI
     * Conforme aux exigences 2.1, 2.3, 3.1, 3.2
     */
    @Cacheable(value = "vat-reports", key = "#startDate + '_' + #endDate")
    public VATReport generateVATReport(LocalDate startDate, LocalDate endDate) {
        log.info("Génération du rapport de TVA pour la période: {} - {}", startDate, endDate);

        String reportId = generateReportId();

        // Récupération des factures de la période
        List<Invoice> invoices = invoiceRepository.findByInvoiceDateBetween(startDate, endDate);

        // Calcul du résumé TVA
        VATReport.VATSummary vatSummary = calculateVATSummary(invoices);

        // Détails des transactions
        List<VATReport.VATTransactionDetail> transactions = invoices.stream()
                .map(this::createTransactionDetail)
                .collect(Collectors.toList());

        // Réconciliation
        VATReport.VATReconciliation reconciliation = performVATReconciliation(invoices);

        // Conformité DGI
        VATReport.DGICompliance dgiCompliance = assessDGICompliance(invoices, vatSummary);

        VATReport report = VATReport.builder()
                .reportId(reportId)
                .startDate(startDate)
                .endDate(endDate)
                .generatedAt(LocalDateTime.now())
                .reportingPeriod(formatReportingPeriod(startDate, endDate))
                .currency("XOF")
                .vatSummary(vatSummary)
                .transactions(transactions)
                .reconciliation(reconciliation)
                .dgiCompliance(dgiCompliance)
                .build();

        // Audit de la génération de rapport
        auditService.logFinancialOperation(
                "VAT_REPORT_GENERATION",
                null,
                Map.of(
                        "reportId", reportId,
                        "startDate", startDate.toString(),
                        "endDate", endDate.toString(),
                        "totalVat", vatSummary.getTotalVatCollected().toString(),
                        "transactionCount", String.valueOf(transactions.size()),
                        "description", "Génération du rapport de TVA pour la DGI"));

        log.info("Rapport de TVA généré avec succès: {}", reportId);
        return report;
    }

    // Méthodes privées

    private boolean validateTaxNumber(String taxNumber) {
        if (taxNumber == null || taxNumber.trim().isEmpty()) {
            return true; // Pas obligatoire dans tous les cas
        }
        return taxNumberValidator.isValid(taxNumber);
    }

    private BigDecimal determineVatRate(VATCalculationRequest request) {
        // Si un taux spécifique est fourni, l'utiliser
        if (request.getVatRate() != null) {
            return request.getVatRate();
        }

        // Taux réduit pour certains secteurs
        if (request.getBusinessSector() != null &&
                REDUCED_VAT_SECTORS.contains(request.getBusinessSector())) {
            return SENEGAL_REDUCED_VAT_RATE;
        }

        // Taux standard par défaut
        return SENEGAL_STANDARD_VAT_RATE;
    }

    private boolean isVatExempt(VATCalculationRequest request) {
        // Exemption pour les secteurs spécifiques
        if (request.getBusinessSector() != null &&
                EXEMPT_SECTORS.contains(request.getBusinessSector())) {
            return true;
        }

        // Exemption pour les transactions d'exportation
        if (Boolean.TRUE.equals(request.getIsExportTransaction())) {
            return true;
        }

        // Exemption pour les transactions gouvernementales
        if (Boolean.TRUE.equals(request.getIsGovernmentTransaction())) {
            return true;
        }

        // Code d'exemption spécifique
        if (request.getExemptionCode() != null && !request.getExemptionCode().trim().isEmpty()) {
            return true;
        }

        return false;
    }

    private String getExemptionReason(VATCalculationRequest request) {
        if (request.getExemptionReason() != null) {
            return request.getExemptionReason();
        }

        if (Boolean.TRUE.equals(request.getIsExportTransaction())) {
            return "Exemption pour exportation";
        }

        if (Boolean.TRUE.equals(request.getIsGovernmentTransaction())) {
            return "Exemption pour transaction gouvernementale";
        }

        if (request.getBusinessSector() != null &&
                EXEMPT_SECTORS.contains(request.getBusinessSector())) {
            return "Exemption sectorielle - " + request.getBusinessSector();
        }

        return null;
    }

    private VATCalculationResult.VATBreakdown createVatBreakdown(
            VATCalculationRequest request, BigDecimal vatRate, BigDecimal vatAmount) {

        return VATCalculationResult.VATBreakdown.builder()
                .baseAmount(request.getAmountExcludingVat())
                .standardRateAmount(
                        SENEGAL_STANDARD_VAT_RATE.equals(vatRate) ? request.getAmountExcludingVat() : BigDecimal.ZERO)
                .reducedRateAmount(
                        SENEGAL_REDUCED_VAT_RATE.equals(vatRate) ? request.getAmountExcludingVat() : BigDecimal.ZERO)
                .exemptAmount(vatAmount.equals(BigDecimal.ZERO) ? request.getAmountExcludingVat() : BigDecimal.ZERO)
                .totalVatAmount(vatAmount)
                .effectiveVatRate(vatRate)
                .build();
    }

    private VATCalculationResult.VATCompliance createComplianceInfo(
            VATCalculationRequest request, BigDecimal vatAmount) {

        boolean isCompliant = true;
        List<String> issues = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        // Vérification du numéro fiscal pour les gros montants
        if ("XOF".equals(request.getCurrency()) &&
                request.getAmountExcludingVat().compareTo(VAT_EXEMPTION_THRESHOLD) > 0) {
            if (request.getClientTaxNumber() == null || request.getClientTaxNumber().trim().isEmpty()) {
                isCompliant = false;
                issues.add("Numéro fiscal client obligatoire pour les montants > 1M XOF");
                recommendations.add("Obtenir le numéro fiscal du client");
            }
        }

        return VATCalculationResult.VATCompliance.builder()
                .isCompliant(isCompliant)
                .complianceStatus(isCompliant ? "CONFORME" : "NON_CONFORME")
                .complianceIssues(issues.toArray(new String[0]))
                .recommendations(recommendations.toArray(new String[0]))
                .requiresDeclaration(vatAmount.compareTo(BigDecimal.ZERO) > 0)
                .declarationDueDate(calculateDeclarationDueDate(request.getTransactionDate()))
                .build();
    }

    private VATCalculationResult.VATReporting createReportingInfo(
            VATCalculationRequest request, BigDecimal vatAmount) {

        boolean isSubjectToWithholding = isSubjectToWithholdingTax(request);
        BigDecimal withholdingAmount = BigDecimal.ZERO;

        if (isSubjectToWithholding) {
            withholdingAmount = vatAmount.multiply(WITHHOLDING_TAX_RATE)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        return VATCalculationResult.VATReporting.builder()
                .reportingPeriod(formatReportingPeriod(request.getTransactionDate()))
                .vatRegime("REGIME_NORMAL")
                .isSubjectToWithholding(isSubjectToWithholding)
                .withholdingRate(isSubjectToWithholding ? WITHHOLDING_TAX_RATE : BigDecimal.ZERO)
                .withholdingAmount(withholdingAmount)
                .dgiReportingCode(getDGIReportingCode(request))
                .build();
    }

    private VATReport.VATSummary calculateVATSummary(List<Invoice> invoices) {
        BigDecimal totalSalesExcludingVat = invoices.stream()
                .map(Invoice::getSubtotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalVatCollected = invoices.stream()
                .map(Invoice::getVatAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalSalesIncludingVat = invoices.stream()
                .map(Invoice::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculs par taux
        BigDecimal standardRateVat = invoices.stream()
                .filter(inv -> SENEGAL_STANDARD_VAT_RATE.equals(inv.getVatRate()))
                .map(Invoice::getVatAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal reducedRateVat = invoices.stream()
                .filter(inv -> SENEGAL_REDUCED_VAT_RATE.equals(inv.getVatRate()))
                .map(Invoice::getVatAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal exemptSales = invoices.stream()
                .filter(inv -> BigDecimal.ZERO.equals(inv.getVatAmount()))
                .map(Invoice::getSubtotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal averageTransactionAmount = invoices.isEmpty() ? BigDecimal.ZERO
                : totalSalesIncludingVat.divide(new BigDecimal(invoices.size()), 2, RoundingMode.HALF_UP);

        return VATReport.VATSummary.builder()
                .totalSalesExcludingVat(totalSalesExcludingVat)
                .totalVatCollected(totalVatCollected)
                .totalSalesIncludingVat(totalSalesIncludingVat)
                .standardRateVat(standardRateVat)
                .reducedRateVat(reducedRateVat)
                .exemptSales(exemptSales)
                .vatToPay(totalVatCollected)
                .vatCredit(BigDecimal.ZERO) // À calculer selon les règles spécifiques
                .numberOfTransactions(invoices.size())
                .averageTransactionAmount(averageTransactionAmount)
                .build();
    }

    private VATReport.VATTransactionDetail createTransactionDetail(Invoice invoice) {
        return VATReport.VATTransactionDetail.builder()
                .transactionId(invoice.getId().toString())
                .transactionDate(invoice.getInvoiceDate())
                .invoiceNumber(invoice.getInvoiceNumber())
                .clientName(invoice.getClientName())
                .clientTaxNumber(invoice.getClientTaxNumber())
                .amountExcludingVat(invoice.getSubtotalAmount())
                .vatRate(invoice.getVatRate())
                .vatAmount(invoice.getVatAmount())
                .totalAmount(invoice.getTotalAmount())
                .transactionType("VENTE")
                .isExempt(BigDecimal.ZERO.equals(invoice.getVatAmount()))
                .exemptionReason(BigDecimal.ZERO.equals(invoice.getVatAmount()) ? "Exemption de TVA" : null)
                .build();
    }

    private VATReport.VATReconciliation performVATReconciliation(List<Invoice> invoices) {
        // Implémentation simplifiée - à enrichir selon les besoins
        BigDecimal calculatedVatAmount = invoices.stream()
                .map(Invoice::getVatAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return VATReport.VATReconciliation.builder()
                .bookVatAmount(calculatedVatAmount)
                .calculatedVatAmount(calculatedVatAmount)
                .variance(BigDecimal.ZERO)
                .variancePercentage(BigDecimal.ZERO)
                .reconciliationItems(new ArrayList<>())
                .isReconciled(true)
                .reconciliationIssues(new String[0])
                .build();
    }

    private VATReport.DGICompliance assessDGICompliance(List<Invoice> invoices,
            VATReport.VATSummary summary) {
        // Évaluation de la conformité selon les règles DGI
        boolean isCompliant = true;
        List<VATReport.ComplianceIssue> issues = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        // Vérification des seuils
        if (summary.getTotalVatCollected().compareTo(new BigDecimal("10000000")) > 0) {
            recommendations.add("Déclaration mensuelle obligatoire pour ce niveau de TVA");
        }

        return VATReport.DGICompliance.builder()
                .isCompliant(isCompliant)
                .complianceLevel("CONFORME")
                .issues(issues)
                .recommendations(recommendations)
                .submissionDeadline(LocalDate.now().plusDays(15))
                .dgiFormReference("FORM_TVA_001")
                .requiresAudit(summary.getTotalVatCollected().compareTo(new BigDecimal("50000000")) > 0)
                .build();
    }

    // Méthodes utilitaires

    private String generateCalculationId() {
        return "VAT_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) +
                "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String generateReportId() {
        return "VATRPT_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    private LocalDate calculateDeclarationDueDate(LocalDate transactionDate) {
        // 15 du mois suivant pour les déclarations mensuelles
        return transactionDate.plusMonths(1).withDayOfMonth(15);
    }

    private boolean isSubjectToWithholdingTax(VATCalculationRequest request) {
        // Retenue à la source pour certains secteurs ou montants élevés
        return "XOF".equals(request.getCurrency()) &&
                request.getAmountExcludingVat().compareTo(new BigDecimal("5000000")) > 0;
    }

    private String formatReportingPeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate.getMonth() == endDate.getMonth() && startDate.getYear() == endDate.getYear()) {
            return startDate.format(DateTimeFormatter.ofPattern("MM/yyyy"));
        }
        return startDate.format(DateTimeFormatter.ofPattern("MM/yyyy")) + " - " +
                endDate.format(DateTimeFormatter.ofPattern("MM/yyyy"));
    }

    private String formatReportingPeriod(LocalDate transactionDate) {
        return transactionDate.format(DateTimeFormatter.ofPattern("MM/yyyy"));
    }

    private String getDGIReportingCode(VATCalculationRequest request) {
        if (Boolean.TRUE.equals(request.getIsExportTransaction())) {
            return "EXP001";
        }
        if (Boolean.TRUE.equals(request.getIsGovernmentTransaction())) {
            return "GOV001";
        }
        return "STD001";
    }
}