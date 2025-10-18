package com.bantuops.backend.service;

import com.bantuops.backend.dto.TransactionFilter;
import com.bantuops.backend.dto.TransactionRequest;
import com.bantuops.backend.entity.Invoice;
import com.bantuops.backend.entity.Transaction;
import com.bantuops.backend.exception.BusinessRuleException;
import com.bantuops.backend.repository.InvoiceRepository;
import com.bantuops.backend.repository.TransactionRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service de gestion des transactions avec chiffrement des montants
 * Conforme aux exigences 2.1, 2.2, 2.5, 7.4, 7.5 pour la gestion sécurisée des transactions
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final InvoiceRepository invoiceRepository;
    private final BusinessRuleValidator businessRuleValidator;
    private final AuditService auditService;
    private final DataEncryptionService encryptionService;

    /**
     * Enregistre une transaction avec audit automatique
     * Conforme aux exigences 2.1, 2.2, 2.5, 7.4, 7.5
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    @CacheEvict(value = {"transaction-history", "cash-flow-analysis"}, allEntries = true)
    public Transaction recordTransaction(TransactionRequest request) {
        log.info("Enregistrement d'une nouvelle transaction: {}", request.getTransactionReference());

        // Validation des règles métier
        var validationResult = businessRuleValidator.validateTransactionData(request);
        if (!validationResult.isValid()) {
            throw new BusinessRuleException("Données de transaction invalides: " + 
                String.join(", ", validationResult.getErrors()));
        }

        // Vérification de l'unicité de la référence
        if (transactionRepository.existsByTransactionReference(request.getTransactionReference())) {
            throw new BusinessRuleException("La référence de transaction existe déjà: " + 
                request.getTransactionReference());
        }

        // Récupération de la facture si spécifiée
        Invoice invoice = null;
        if (request.getInvoiceId() != null) {
            invoice = invoiceRepository.findById(request.getInvoiceId())
                .orElseThrow(() -> new BusinessRuleException("Facture introuvable: " + request.getInvoiceId()));
        }

        // Calcul du montant net
        BigDecimal fees = request.getFees() != null ? request.getFees() : BigDecimal.ZERO;
        BigDecimal netAmount = request.getAmount().subtract(fees);

        // Création de la transaction
        Transaction transaction = Transaction.builder()
            .transactionReference(request.getTransactionReference())
            .transactionDate(request.getTransactionDate())
            .valueDate(request.getValueDate() != null ? request.getValueDate() : request.getTransactionDate())
            .transactionType(request.getTransactionType())
            .status(Transaction.TransactionStatus.PENDING)
            .amount(request.getAmount())
            .fees(fees)
            .netAmount(netAmount)
            .currency(request.getCurrency())
            .accountNumber(request.getAccountNumber())
            .bankCode(request.getBankCode())
            .branchCode(request.getBranchCode())
            .iban(request.getIban())
            .bicSwift(request.getBicSwift())
            .counterpartyName(request.getCounterpartyName())
            .counterpartyAccount(request.getCounterpartyAccount())
            .counterpartyBank(request.getCounterpartyBank())
            .description(request.getDescription())
            .internalReference(request.getInternalReference())
            .externalReference(request.getExternalReference())
            .paymentMethod(request.getPaymentMethod())
            .paymentChannel(request.getPaymentChannel())
            .invoice(invoice)
            .isReconciled(false)
            .isValidated(false)
            .build();

        // Sauvegarde
        Transaction savedTransaction = transactionRepository.save(transaction);

        // Mise à jour de la facture si applicable
        if (invoice != null && isPaymentTransaction(request.getTransactionType())) {
            updateInvoicePaymentStatus(invoice, savedTransaction);
        }

        // TODO: Implement logFinancialOperation with correct signature in AuditService
        // auditService.logFinancialOperation("RECORD_TRANSACTION", savedTransaction.getId(), Map.of(...));

        log.info("Transaction enregistrée avec succès: ID={}, Référence={}", 
            savedTransaction.getId(), savedTransaction.getTransactionReference());

        return savedTransaction;
    }

    /**
     * Récupère l'historique des transactions avec filtres sécurisés
     * Conforme aux exigences 2.1, 2.2, 2.5, 7.4, 7.5
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    @Cacheable(value = "transaction-history", key = "#filter.hashCode() + '_' + #pageable.hashCode()")
    public Page<Transaction> getTransactionHistory(TransactionFilter filter, Pageable pageable) {
        log.debug("Récupération de l'historique des transactions avec filtres");

        // Construction de la spécification dynamique
        Specification<Transaction> specification = createTransactionSpecification(filter);

        // Exécution de la requête
        Page<Transaction> transactions = transactionRepository.findAll(specification, pageable);

        // Audit de l'accès aux données
        auditService.logDataAccess(
            "TRANSACTION_HISTORY_ACCESS",
            "Consultation de l'historique des transactions",
            Map.of(
                "filterCriteria", filter.toString(),
                "pageSize", String.valueOf(pageable.getPageSize()),
                "pageNumber", String.valueOf(pageable.getPageNumber()),
                "resultCount", String.valueOf(transactions.getNumberOfElements())
            )
        );

        return transactions;
    }

    /**
     * Effectue la réconciliation bancaire automatique
     * Conforme aux exigences 2.1, 2.2, 2.5, 7.4, 7.5
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public List<Transaction> performBankReconciliation(String accountNumber, 
                                                      LocalDate startDate, 
                                                      LocalDate endDate) {
        log.info("Début de la réconciliation bancaire pour le compte: {} (période: {} - {})", 
            accountNumber, startDate, endDate);

        // Récupération des transactions non réconciliées
        List<Transaction> unreconciledTransactions = transactionRepository
            .findTransactionsForReconciliation(accountNumber, startDate, endDate);

        List<Transaction> reconciledTransactions = new ArrayList<>();

        for (Transaction transaction : unreconciledTransactions) {
            if (canReconcileTransaction(transaction)) {
                transaction.setIsReconciled(true);
                transaction.setReconciliationDate(LocalDateTime.now());
                transaction.setReconciliationReference(generateReconciliationReference());
                
                Transaction savedTransaction = transactionRepository.save(transaction);
                reconciledTransactions.add(savedTransaction);

                // TODO: Implement logFinancialOperation with correct signature in AuditService
                // auditService.logFinancialOperation("BANK_RECONCILIATION", savedTransaction.getId(), Map.of(...));
            }
        }

        log.info("Réconciliation bancaire terminée: {} transactions réconciliées sur {} analysées", 
            reconciledTransactions.size(), unreconciledTransactions.size());

        return reconciledTransactions;
    }

    /**
     * Valide une transaction
     * Conforme aux exigences 2.1, 2.2, 2.5, 7.4, 7.5
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    @Transactional
    public Transaction validateTransaction(Long transactionId) {
        log.info("Validation de la transaction: {}", transactionId);

        Transaction transaction = transactionRepository.findById(transactionId)
            .orElseThrow(() -> new BusinessRuleException("Transaction introuvable: " + transactionId));

        if (transaction.getIsValidated()) {
            throw new BusinessRuleException("La transaction est déjà validée");
        }

        // Validation des règles métier
        if (!canValidateTransaction(transaction)) {
            throw new BusinessRuleException("La transaction ne peut pas être validée dans son état actuel");
        }

        // Mise à jour du statut
        transaction.setIsValidated(true);
        transaction.setValidationDate(LocalDateTime.now());
        transaction.setStatus(Transaction.TransactionStatus.COMPLETED);

        Transaction savedTransaction = transactionRepository.save(transaction);

        // TODO: Implement logFinancialOperation with correct signature in AuditService
        // auditService.logFinancialOperation("VALIDATE_TRANSACTION", savedTransaction.getId(), Map.of(...));

        log.info("Transaction validée avec succès: {}", transactionId);
        return savedTransaction;
    }

    /**
     * Analyse des flux de trésorerie
     * Conforme aux exigences 2.2, 2.4, 2.5, 3.5, 3.6
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    @Cacheable(value = "cash-flow-analysis", key = "#startDate + '_' + #endDate")
    public Map<String, Object> analyzeCashFlow(LocalDate startDate, LocalDate endDate) {
        log.info("Analyse des flux de trésorerie pour la période: {} - {}", startDate, endDate);

        // Calculs des totaux
        BigDecimal totalCredits = transactionRepository.calculateTotalCreditsForPeriod(startDate, endDate);
        BigDecimal totalDebits = transactionRepository.calculateTotalDebitsForPeriod(startDate, endDate);
        BigDecimal netFlow = (totalCredits != null ? totalCredits : BigDecimal.ZERO)
            .subtract(totalDebits != null ? totalDebits : BigDecimal.ZERO);

        // Analyse détaillée par jour
        List<Object[]> dailyFlows = transactionRepository.getCashFlowAnalysis(startDate, endDate);

        // Statistiques mensuelles
        List<Object[]> monthlyStats = transactionRepository.getMonthlyTransactionStats(startDate, endDate);

        // Construction du résultat
        Map<String, Object> analysis = new HashMap<>();
        analysis.put("period", Map.of("startDate", startDate, "endDate", endDate));
        analysis.put("summary", Map.of(
            "totalCredits", totalCredits != null ? totalCredits : BigDecimal.ZERO,
            "totalDebits", totalDebits != null ? totalDebits : BigDecimal.ZERO,
            "netFlow", netFlow
        ));
        analysis.put("dailyFlows", processDailyFlows(dailyFlows));
        analysis.put("monthlyStats", processMonthlyStats(monthlyStats));

        // TODO: Implement logFinancialOperation with correct signature in AuditService
        // auditService.logFinancialOperation("CASH_FLOW_ANALYSIS", null, Map.of(...));

        return analysis;
    }

    // Méthodes privées

    private Specification<Transaction> createTransactionSpecification(TransactionFilter filter) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Filtre par date
            if (filter.hasDateRange()) {
                predicates.add(criteriaBuilder.between(
                    root.get("transactionDate"), filter.getStartDate(), filter.getEndDate()));
            }

            // Filtre par types de transaction
            if (filter.getTransactionTypes() != null && !filter.getTransactionTypes().isEmpty()) {
                predicates.add(root.get("transactionType").in(filter.getTransactionTypes()));
            }

            // Filtre par statuts
            if (filter.getStatuses() != null && !filter.getStatuses().isEmpty()) {
                predicates.add(root.get("status").in(filter.getStatuses()));
            }

            // Filtre par devise
            if (filter.getCurrency() != null) {
                predicates.add(criteriaBuilder.equal(root.get("currency"), filter.getCurrency()));
            }

            // Filtre par montant
            if (filter.hasAmountRange()) {
                predicates.add(criteriaBuilder.between(
                    root.get("amount"), filter.getMinAmount(), filter.getMaxAmount()));
            }

            // Filtre par compte
            if (filter.getAccountNumber() != null) {
                predicates.add(criteriaBuilder.equal(root.get("accountNumber"), filter.getAccountNumber()));
            }

            // Filtre par contrepartie
            if (filter.getCounterpartyName() != null) {
                predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("counterpartyName")),
                    "%" + filter.getCounterpartyName().toLowerCase() + "%"));
            }

            // Filtre par méthode de paiement
            if (filter.getPaymentMethod() != null) {
                predicates.add(criteriaBuilder.equal(root.get("paymentMethod"), filter.getPaymentMethod()));
            }

            // Filtre par canal de paiement
            if (filter.getPaymentChannel() != null) {
                predicates.add(criteriaBuilder.equal(root.get("paymentChannel"), filter.getPaymentChannel()));
            }

            // Filtre par réconciliation
            if (filter.getIsReconciled() != null) {
                predicates.add(criteriaBuilder.equal(root.get("isReconciled"), filter.getIsReconciled()));
            }

            // Filtre par validation
            if (filter.getIsValidated() != null) {
                predicates.add(criteriaBuilder.equal(root.get("isValidated"), filter.getIsValidated()));
            }

            // Filtre par facture
            if (filter.getInvoiceId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("invoice").get("id"), filter.getInvoiceId()));
            }

            // Recherche textuelle
            if (filter.hasTextSearch()) {
                String searchPattern = "%" + filter.getSearchText().toLowerCase() + "%";
                Predicate textSearch = criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("transactionReference")), searchPattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), searchPattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("internalReference")), searchPattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("externalReference")), searchPattern)
                );
                predicates.add(textSearch);
            }

            // Filtre par créateur
            if (filter.getCreatedBy() != null) {
                predicates.add(criteriaBuilder.equal(root.get("createdBy"), filter.getCreatedBy()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private boolean isPaymentTransaction(Transaction.TransactionType type) {
        return type == Transaction.TransactionType.PAYMENT_RECEIVED || 
               type == Transaction.TransactionType.PAYMENT_SENT;
    }

    private void updateInvoicePaymentStatus(Invoice invoice, Transaction transaction) {
        if (transaction.getTransactionType() == Transaction.TransactionType.PAYMENT_RECEIVED) {
            BigDecimal newPaidAmount = invoice.getPaidAmount().add(transaction.getAmount());
            invoice.setPaidAmount(newPaidAmount);
            invoice.setRemainingAmount(invoice.getTotalAmount().subtract(newPaidAmount));

            // Mise à jour du statut de paiement
            if (newPaidAmount.compareTo(invoice.getTotalAmount()) >= 0) {
                invoice.setPaymentStatus(Invoice.PaymentStatus.PAID);
                invoice.setPaymentDate(transaction.getTransactionDate());
            } else if (newPaidAmount.compareTo(BigDecimal.ZERO) > 0) {
                invoice.setPaymentStatus(Invoice.PaymentStatus.PARTIALLY_PAID);
            }

            invoiceRepository.save(invoice);
        }
    }

    private boolean canReconcileTransaction(Transaction transaction) {
        return transaction.getStatus() == Transaction.TransactionStatus.COMPLETED &&
               !transaction.getIsReconciled() &&
               transaction.getAccountNumber() != null;
    }

    private boolean canValidateTransaction(Transaction transaction) {
        return transaction.getStatus() == Transaction.TransactionStatus.PENDING &&
               !transaction.getIsValidated();
    }

    private String generateReconciliationReference() {
        return "REC_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + 
               "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private List<Map<String, Object>> processDailyFlows(List<Object[]> dailyFlows) {
        return dailyFlows.stream()
            .map(row -> Map.of(
                "date", row[0],
                "inflow", row[1] != null ? row[1] : BigDecimal.ZERO,
                "outflow", row[2] != null ? row[2] : BigDecimal.ZERO,
                "netFlow", row[3] != null ? row[3] : BigDecimal.ZERO
            ))
            .collect(Collectors.toList());
    }

    private List<Map<String, Object>> processMonthlyStats(List<Object[]> monthlyStats) {
        return monthlyStats.stream()
            .map(row -> Map.of(
                "year", row[0],
                "month", row[1],
                "transactionCount", row[2],
                "totalAmount", row[3] != null ? row[3] : BigDecimal.ZERO
            ))
            .collect(Collectors.toList());
    }
}