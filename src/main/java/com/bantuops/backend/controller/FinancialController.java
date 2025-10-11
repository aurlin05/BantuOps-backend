package com.bantuops.backend.controller;

import com.bantuops.backend.dto.*;
import com.bantuops.backend.entity.Invoice;
import com.bantuops.backend.entity.Transaction;
import com.bantuops.backend.service.FinancialService;
import com.bantuops.backend.service.TransactionService;
import com.bantuops.backend.service.VATCalculationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST pour la gestion financière
 * Conforme aux exigences 4.1, 4.2, 4.3, 4.4 pour les APIs financières sécurisées
 */
@RestController
@RequestMapping("/api/financial")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Gestion Financière", description = "API pour la gestion financière et facturation")
@SecurityRequirement(name = "bearerAuth")
public class FinancialController {

    private final FinancialService financialService;
    private final TransactionService transactionService;
    private final VATCalculationService vatCalculationService;

    /**
     * Crée une nouvelle facture
     */
    @PostMapping("/invoices")
    @Operation(summary = "Créer une facture", 
               description = "Crée une nouvelle facture avec validation fiscale sénégalaise")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Facture créée avec succès"),
        @ApiResponse(responseCode = "400", description = "Données de facture invalides"),
        @ApiResponse(responseCode = "403", description = "Accès non autorisé")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ResponseEntity<Invoice> createInvoice(
            @Valid @RequestBody InvoiceRequest request) {
        
        log.info("Création d'une nouvelle facture: {}", request.getInvoiceNumber());
        
        try {
            Invoice invoice = financialService.createInvoice(request);
            
            log.info("Facture créée avec succès: {}", invoice.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(invoice);
            
        } catch (Exception e) {
            log.error("Erreur lors de la création de facture: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Calcule les montants d'une facture
     */
    @PostMapping("/invoices/calculate")
    @Operation(summary = "Calculer les montants de facture", 
               description = "Calcule les montants HT, TVA et TTC d'une facture")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ResponseEntity<InvoiceCalculation> calculateInvoiceAmounts(
            @Valid @RequestBody InvoiceRequest request) {
        
        log.info("Calcul des montants pour la facture: {}", request.getInvoiceNumber());
        
        try {
            InvoiceCalculation calculation = financialService.calculateInvoiceAmounts(request);
            return ResponseEntity.ok(calculation);
            
        } catch (Exception e) {
            log.error("Erreur lors du calcul de facture: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Récupère la liste des factures avec pagination
     */
    @GetMapping("/invoices")
    @Operation(summary = "Lister les factures", 
               description = "Récupère la liste paginée des factures")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE', 'HR')")
    public ResponseEntity<Page<Invoice>> getInvoices(
            @PageableDefault(size = 20) Pageable pageable,
            @Parameter(description = "Filtrer par statut") 
            @RequestParam(required = false) String status,
            @Parameter(description = "Date de début") 
            @RequestParam(required = false) LocalDate startDate,
            @Parameter(description = "Date de fin") 
            @RequestParam(required = false) LocalDate endDate) {
        
        log.info("Récupération de la liste des factures");
        
        try {
            Page<Invoice> invoices = financialService.getInvoices(pageable, status, startDate, endDate);
            return ResponseEntity.ok(invoices);
            
        } catch (Exception e) {
            log.error("Erreur lors de la récupération des factures: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Récupère une facture par son ID
     */
    @GetMapping("/invoices/{invoiceId}")
    @Operation(summary = "Récupérer une facture", 
               description = "Récupère les détails d'une facture par son ID")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE', 'HR')")
    public ResponseEntity<Invoice> getInvoice(
            @Parameter(description = "ID de la facture") 
            @PathVariable Long invoiceId) {
        
        log.info("Récupération de la facture {}", invoiceId);
        
        try {
            Invoice invoice = financialService.getInvoiceById(invoiceId);
            return ResponseEntity.ok(invoice);
            
        } catch (Exception e) {
            log.error("Erreur lors de la récupération de la facture: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * Calcule la TVA
     */
    @PostMapping("/vat/calculate")
    @Operation(summary = "Calculer la TVA", 
               description = "Calcule la TVA selon les taux sénégalais")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ResponseEntity<VATCalculationResult> calculateVAT(
            @Valid @RequestBody VATCalculationRequest request) {
        
        log.info("Calcul de TVA demandé pour un montant de {}", request.getAmount());
        
        try {
            VATCalculationResult result = vatCalculationService.calculateVAT(request);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Erreur lors du calcul de TVA: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Génère un rapport financier
     */
    @PostMapping("/reports/generate")
    @Operation(summary = "Générer un rapport financier", 
               description = "Génère un rapport financier pour une période donnée")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ResponseEntity<FinancialReport> generateFinancialReport(
            @Valid @RequestBody FinancialReportRequest request) {
        
        log.info("Génération de rapport financier pour la période {} - {}", 
                request.getStartDate(), request.getEndDate());
        
        try {
            FinancialReport report = financialService.generateFinancialReport(request);
            
            log.info("Rapport financier généré avec succès");
            return ResponseEntity.ok(report);
            
        } catch (Exception e) {
            log.error("Erreur lors de la génération du rapport: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Enregistre une transaction financière
     */
    @PostMapping("/transactions")
    @Operation(summary = "Enregistrer une transaction", 
               description = "Enregistre une nouvelle transaction financière")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ResponseEntity<Transaction> recordTransaction(
            @Valid @RequestBody TransactionRequest request) {
        
        log.info("Enregistrement d'une nouvelle transaction de type {}", request.getType());
        
        try {
            Transaction transaction = transactionService.recordTransaction(request);
            
            log.info("Transaction enregistrée avec succès: {}", transaction.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(transaction);
            
        } catch (Exception e) {
            log.error("Erreur lors de l'enregistrement de transaction: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Récupère l'historique des transactions
     */
    @GetMapping("/transactions")
    @Operation(summary = "Historique des transactions", 
               description = "Récupère l'historique paginé des transactions")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ResponseEntity<Page<Transaction>> getTransactionHistory(
            @PageableDefault(size = 20) Pageable pageable,
            @Parameter(description = "Type de transaction") 
            @RequestParam(required = false) String type,
            @Parameter(description = "Date de début") 
            @RequestParam(required = false) LocalDate startDate,
            @Parameter(description = "Date de fin") 
            @RequestParam(required = false) LocalDate endDate) {
        
        log.info("Récupération de l'historique des transactions");
        
        try {
            TransactionFilter filter = TransactionFilter.builder()
                .type(type)
                .startDate(startDate)
                .endDate(endDate)
                .build();
                
            Page<Transaction> transactions = transactionService.getTransactionHistory(filter, pageable);
            return ResponseEntity.ok(transactions);
            
        } catch (Exception e) {
            log.error("Erreur lors de la récupération des transactions: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Génère un rapport de TVA
     */
    @GetMapping("/vat/report")
    @Operation(summary = "Rapport de TVA", 
               description = "Génère un rapport de TVA pour la DGI sénégalaise")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VATReport> generateVATReport(
            @Parameter(description = "Date de début") 
            @RequestParam LocalDate startDate,
            @Parameter(description = "Date de fin") 
            @RequestParam LocalDate endDate) {
        
        log.info("Génération de rapport de TVA pour la période {} - {}", startDate, endDate);
        
        try {
            VATReport report = vatCalculationService.generateVATReport(startDate, endDate);
            return ResponseEntity.ok(report);
            
        } catch (Exception e) {
            log.error("Erreur lors de la génération du rapport de TVA: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Exporte les données financières (accès restreint)
     */
    @PostMapping("/export")
    @Operation(summary = "Exporter les données financières", 
               description = "Exporte les données financières de manière sécurisée")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> exportFinancialData(
            @Valid @RequestBody FinancialReportRequest request) {
        
        log.info("Export de données financières demandé pour la période {} - {}", 
                request.getStartDate(), request.getEndDate());
        
        try {
            Map<String, Object> exportData = financialService.exportFinancialData(request);
            
            log.info("Export de données financières terminé avec succès");
            return ResponseEntity.ok(exportData);
            
        } catch (Exception e) {
            log.error("Erreur lors de l'export des données: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Récupère les métriques financières du tableau de bord
     */
    @GetMapping("/dashboard/metrics")
    @Operation(summary = "Métriques du tableau de bord", 
               description = "Récupère les métriques financières pour le tableau de bord")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ResponseEntity<Map<String, Object>> getDashboardMetrics() {
        
        log.info("Récupération des métriques du tableau de bord financier");
        
        try {
            Map<String, Object> metrics = financialService.getDashboardMetrics();
            return ResponseEntity.ok(metrics);
            
        } catch (Exception e) {
            log.error("Erreur lors de la récupération des métriques: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}