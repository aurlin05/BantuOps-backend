package com.bantuops.backend.repository;

import com.bantuops.backend.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository pour Transaction avec requêtes optimisées
 * Conforme aux exigences 2.1, 2.2, 2.5, 7.4, 7.5 pour la gestion sécurisée des transactions
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    /**
     * Trouve une transaction par sa référence
     */
    Optional<Transaction> findByTransactionReference(String transactionReference);

    /**
     * Trouve les transactions par date avec pagination
     */
    Page<Transaction> findByTransactionDateBetweenOrderByTransactionDateDesc(
        LocalDate startDate, LocalDate endDate, Pageable pageable);

    /**
     * Trouve les transactions par type avec pagination
     */
    Page<Transaction> findByTransactionTypeOrderByTransactionDateDesc(
        Transaction.TransactionType transactionType, Pageable pageable);

    /**
     * Trouve les transactions par statut avec pagination
     */
    Page<Transaction> findByStatusOrderByTransactionDateDesc(
        Transaction.TransactionStatus status, Pageable pageable);

    /**
     * Trouve les transactions non réconciliées
     */
    @Query("SELECT t FROM Transaction t " +
           "WHERE t.isReconciled = false " +
           "AND t.status = 'COMPLETED' " +
           "ORDER BY t.transactionDate ASC")
    List<Transaction> findUnreconciledTransactions();

    /**
     * Trouve les transactions non réconciliées avec pagination
     */
    @Query("SELECT t FROM Transaction t " +
           "WHERE t.isReconciled = false " +
           "AND t.status = 'COMPLETED' " +
           "ORDER BY t.transactionDate ASC")
    Page<Transaction> findUnreconciledTransactions(Pageable pageable);

    /**
     * Trouve les transactions pour une facture
     */
    List<Transaction> findByInvoiceIdOrderByTransactionDateDesc(Long invoiceId);

    /**
     * Trouve les transactions par compte
     */
    @Query("SELECT t FROM Transaction t " +
           "WHERE t.accountNumber = :accountNumber " +
           "ORDER BY t.transactionDate DESC")
    List<Transaction> findByAccountNumber(@Param("accountNumber") String accountNumber);

    /**
     * Trouve les transactions par contrepartie
     */
    @Query("SELECT t FROM Transaction t " +
           "WHERE LOWER(t.counterpartyName) LIKE LOWER(CONCAT('%', :counterpartyName, '%')) " +
           "ORDER BY t.transactionDate DESC")
    List<Transaction> findByCounterpartyNameContainingIgnoreCase(@Param("counterpartyName") String counterpartyName);

    /**
     * Calcule le solde pour une période
     */
    @Query("SELECT " +
           "SUM(CASE WHEN t.transactionType IN ('CREDIT', 'PAYMENT_RECEIVED') THEN t.amount ELSE 0 END) - " +
           "SUM(CASE WHEN t.transactionType IN ('DEBIT', 'PAYMENT_SENT') THEN t.amount ELSE 0 END) " +
           "FROM Transaction t " +
           "WHERE t.transactionDate BETWEEN :startDate AND :endDate " +
           "AND t.status = 'COMPLETED'")
    BigDecimal calculateBalanceForPeriod(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Calcule le total des crédits pour une période
     */
    @Query("SELECT SUM(t.amount) FROM Transaction t " +
           "WHERE t.transactionDate BETWEEN :startDate AND :endDate " +
           "AND t.transactionType IN ('CREDIT', 'PAYMENT_RECEIVED') " +
           "AND t.status = 'COMPLETED'")
    BigDecimal calculateTotalCreditsForPeriod(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Calcule le total des débits pour une période
     */
    @Query("SELECT SUM(t.amount) FROM Transaction t " +
           "WHERE t.transactionDate BETWEEN :startDate AND :endDate " +
           "AND t.transactionType IN ('DEBIT', 'PAYMENT_SENT') " +
           "AND t.status = 'COMPLETED'")
    BigDecimal calculateTotalDebitsForPeriod(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Statistiques de transactions par mois
     */
    @Query("SELECT " +
           "YEAR(t.transactionDate) as year, " +
           "MONTH(t.transactionDate) as month, " +
           "COUNT(t) as transactionCount, " +
           "SUM(t.amount) as totalAmount " +
           "FROM Transaction t " +
           "WHERE t.transactionDate BETWEEN :startDate AND :endDate " +
           "AND t.status = 'COMPLETED' " +
           "GROUP BY YEAR(t.transactionDate), MONTH(t.transactionDate) " +
           "ORDER BY year DESC, month DESC")
    List<Object[]> getMonthlyTransactionStats(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Trouve les transactions suspectes (montants élevés ou patterns inhabituels)
     */
    @Query("SELECT t FROM Transaction t " +
           "WHERE (t.amount > :highThreshold OR " +
           "       (t.amount > :mediumThreshold AND t.createdAt > :recentTime)) " +
           "AND t.status = 'COMPLETED' " +
           "ORDER BY t.amount DESC")
    List<Transaction> findSuspiciousTransactions(
        @Param("highThreshold") BigDecimal highThreshold,
        @Param("mediumThreshold") BigDecimal mediumThreshold,
        @Param("recentTime") LocalDateTime recentTime
    );

    /**
     * Trouve les transactions en attente depuis longtemps
     */
    @Query("SELECT t FROM Transaction t " +
           "WHERE t.status = 'PENDING' " +
           "AND t.createdAt < :cutoffTime " +
           "ORDER BY t.createdAt ASC")
    List<Transaction> findStaleTransactions(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Trouve les transactions par méthode de paiement
     */
    List<Transaction> findByPaymentMethodOrderByTransactionDateDesc(String paymentMethod);

    /**
     * Trouve les transactions par canal de paiement
     */
    List<Transaction> findByPaymentChannelOrderByTransactionDateDesc(String paymentChannel);

    /**
     * Compte les transactions par statut
     */
    @Query("SELECT t.status, COUNT(t) FROM Transaction t GROUP BY t.status")
    List<Object[]> countTransactionsByStatus();

    /**
     * Compte les transactions par type
     */
    @Query("SELECT t.transactionType, COUNT(t) FROM Transaction t GROUP BY t.transactionType")
    List<Object[]> countTransactionsByType();

    /**
     * Trouve les transactions récentes pour audit
     */
    @Query("SELECT t FROM Transaction t " +
           "WHERE t.createdAt >= :since " +
           "ORDER BY t.createdAt DESC")
    List<Transaction> findRecentTransactions(@Param("since") LocalDateTime since);

    /**
     * Trouve les transactions modifiées récemment pour audit
     */
    @Query("SELECT t FROM Transaction t " +
           "WHERE t.updatedAt > t.createdAt " +
           "AND t.updatedAt >= :since " +
           "ORDER BY t.updatedAt DESC")
    List<Transaction> findRecentlyModifiedTransactions(@Param("since") LocalDateTime since);

    /**
     * Vérifie si une référence de transaction existe
     */
    boolean existsByTransactionReference(String transactionReference);

    /**
     * Trouve les transactions par devise
     */
    List<Transaction> findByCurrencyOrderByTransactionDateDesc(String currency);

    /**
     * Analyse des flux de trésorerie
     */
    @Query(value = "SELECT " +
                   "DATE_TRUNC('day', transaction_date) as day, " +
                   "SUM(CASE WHEN transaction_type IN ('CREDIT', 'PAYMENT_RECEIVED') THEN amount ELSE 0 END) as inflow, " +
                   "SUM(CASE WHEN transaction_type IN ('DEBIT', 'PAYMENT_SENT') THEN amount ELSE 0 END) as outflow, " +
                   "SUM(CASE WHEN transaction_type IN ('CREDIT', 'PAYMENT_RECEIVED') THEN amount ELSE -amount END) as net_flow " +
                   "FROM transactions " +
                   "WHERE transaction_date BETWEEN :startDate AND :endDate " +
                   "AND status = 'COMPLETED' " +
                   "GROUP BY DATE_TRUNC('day', transaction_date) " +
                   "ORDER BY day DESC", 
           nativeQuery = true)
    List<Object[]> getCashFlowAnalysis(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Analyse des délais de traitement
     */
    @Query(value = "SELECT " +
                   "AVG(EXTRACT(EPOCH FROM (updated_at - created_at))/3600) as avg_processing_hours, " +
                   "MIN(EXTRACT(EPOCH FROM (updated_at - created_at))/3600) as min_processing_hours, " +
                   "MAX(EXTRACT(EPOCH FROM (updated_at - created_at))/3600) as max_processing_hours, " +
                   "COUNT(*) as total_transactions " +
                   "FROM transactions " +
                   "WHERE status = 'COMPLETED' " +
                   "AND transaction_date BETWEEN :startDate AND :endDate", 
           nativeQuery = true)
    Object[] getProcessingTimeAnalysis(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Recherche de transactions par texte (référence, description, contrepartie)
     */
    @Query("SELECT t FROM Transaction t " +
           "WHERE LOWER(t.transactionReference) LIKE LOWER(CONCAT('%', :searchText, '%')) " +
           "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :searchText, '%')) " +
           "OR LOWER(t.counterpartyName) LIKE LOWER(CONCAT('%', :searchText, '%')) " +
           "OR LOWER(t.internalReference) LIKE LOWER(CONCAT('%', :searchText, '%')) " +
           "OR LOWER(t.externalReference) LIKE LOWER(CONCAT('%', :searchText, '%')) " +
           "ORDER BY t.transactionDate DESC")
    Page<Transaction> searchTransactions(@Param("searchText") String searchText, Pageable pageable);

    /**
     * Trouve les transactions pour réconciliation bancaire
     */
    @Query("SELECT t FROM Transaction t " +
           "WHERE t.accountNumber = :accountNumber " +
           "AND t.transactionDate BETWEEN :startDate AND :endDate " +
           "AND t.status = 'COMPLETED' " +
           "ORDER BY t.transactionDate ASC, t.amount ASC")
    List<Transaction> findTransactionsForReconciliation(
        @Param("accountNumber") String accountNumber,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
}