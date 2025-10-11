package com.bantuops.backend.repository;

import com.bantuops.backend.entity.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository pour Invoice avec pagination efficace
 * Conforme aux exigences 6.1, 6.2, 6.3, 6.4, 6.5 pour l'optimisation des requêtes financières
 */
@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {

    /**
     * Trouve une facture par son numéro
     */
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    /**
     * Trouve les factures avec leurs éléments (optimisé avec JOIN FETCH)
     */
    @Query("SELECT DISTINCT i FROM Invoice i " +
           "LEFT JOIN FETCH i.items " +
           "WHERE i.id = :invoiceId")
    Optional<Invoice> findByIdWithItems(@Param("invoiceId") Long invoiceId);

    /**
     * Trouve les factures par statut avec pagination
     */
    Page<Invoice> findByStatusOrderByInvoiceDateDesc(Invoice.InvoiceStatus status, Pageable pageable);

    /**
     * Trouve les factures par statut de paiement avec pagination
     */
    Page<Invoice> findByPaymentStatusOrderByDueDateAsc(Invoice.PaymentStatus paymentStatus, Pageable pageable);

    /**
     * Trouve les factures en retard de paiement
     */
    @Query("SELECT i FROM Invoice i " +
           "WHERE i.dueDate < CURRENT_DATE " +
           "AND i.paymentStatus IN ('UNPAID', 'PARTIALLY_PAID') " +
           "ORDER BY i.dueDate ASC")
    List<Invoice> findOverdueInvoices();

    /**
     * Trouve les factures en retard avec pagination
     */
    @Query("SELECT i FROM Invoice i " +
           "WHERE i.dueDate < CURRENT_DATE " +
           "AND i.paymentStatus IN ('UNPAID', 'PARTIALLY_PAID') " +
           "ORDER BY i.dueDate ASC")
    Page<Invoice> findOverdueInvoices(Pageable pageable);

    /**
     * Trouve les factures pour une période donnée
     */
    @Query("SELECT i FROM Invoice i " +
           "WHERE i.invoiceDate BETWEEN :startDate AND :endDate " +
           "ORDER BY i.invoiceDate DESC")
    List<Invoice> findByInvoiceDateBetween(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Trouve les factures pour une période avec pagination
     */
    Page<Invoice> findByInvoiceDateBetweenOrderByInvoiceDateDesc(
        LocalDate startDate, 
        LocalDate endDate, 
        Pageable pageable
    );

    /**
     * Trouve les factures pour une période et un statut avec pagination
     */
    Page<Invoice> findByInvoiceDateBetweenAndStatusOrderByInvoiceDateDesc(
        LocalDate startDate, 
        LocalDate endDate, 
        Invoice.InvoiceStatus status,
        Pageable pageable
    );

    /**
     * Recherche de factures par nom de client
     */
    @Query("SELECT i FROM Invoice i " +
           "WHERE LOWER(i.clientName) LIKE LOWER(CONCAT('%', :clientName, '%')) " +
           "ORDER BY i.invoiceDate DESC")
    Page<Invoice> findByClientNameContainingIgnoreCase(@Param("clientName") String clientName, Pageable pageable);

    /**
     * Trouve les factures d'un client par numéro fiscal
     */
    List<Invoice> findByClientTaxNumberOrderByInvoiceDateDesc(String clientTaxNumber);

    /**
     * Calcule le chiffre d'affaires total pour une période
     */
    @Query("SELECT SUM(i.totalAmount) FROM Invoice i " +
           "WHERE i.invoiceDate BETWEEN :startDate AND :endDate " +
           "AND i.status NOT IN ('CANCELLED', 'REJECTED')")
    BigDecimal calculateTotalRevenueForPeriod(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Calcule le montant total impayé
     */
    @Query("SELECT SUM(i.remainingAmount) FROM Invoice i " +
           "WHERE i.paymentStatus IN ('UNPAID', 'PARTIALLY_PAID')")
    BigDecimal calculateTotalOutstandingAmount();

    /**
     * Statistiques de facturation par mois
     */
    @Query("SELECT " +
           "YEAR(i.invoiceDate) as year, " +
           "MONTH(i.invoiceDate) as month, " +
           "COUNT(i) as invoiceCount, " +
           "SUM(i.totalAmount) as totalAmount, " +
           "SUM(i.vatAmount) as totalVat " +
           "FROM Invoice i " +
           "WHERE i.invoiceDate BETWEEN :startDate AND :endDate " +
           "AND i.status NOT IN ('CANCELLED', 'REJECTED') " +
           "GROUP BY YEAR(i.invoiceDate), MONTH(i.invoiceDate) " +
           "ORDER BY year DESC, month DESC")
    List<Object[]> getMonthlyInvoiceStats(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Top clients par chiffre d'affaires
     */
    @Query("SELECT i.clientName, SUM(i.totalAmount) as totalRevenue " +
           "FROM Invoice i " +
           "WHERE i.invoiceDate BETWEEN :startDate AND :endDate " +
           "AND i.status NOT IN ('CANCELLED', 'REJECTED') " +
           "GROUP BY i.clientName " +
           "ORDER BY totalRevenue DESC")
    List<Object[]> getTopClientsByRevenue(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        Pageable pageable
    );

    /**
     * Trouve les factures avec un montant supérieur à un seuil
     */
    @Query("SELECT i FROM Invoice i " +
           "WHERE i.totalAmount > :threshold " +
           "ORDER BY i.totalAmount DESC")
    List<Invoice> findInvoicesAboveAmount(@Param("threshold") BigDecimal threshold);

    /**
     * Compte les factures par statut
     */
    @Query("SELECT i.status, COUNT(i) FROM Invoice i GROUP BY i.status")
    List<Object[]> countInvoicesByStatus();

    /**
     * Compte les factures par statut de paiement
     */
    @Query("SELECT i.paymentStatus, COUNT(i) FROM Invoice i GROUP BY i.paymentStatus")
    List<Object[]> countInvoicesByPaymentStatus();

    /**
     * Trouve les factures échéant dans les prochains jours
     */
    @Query("SELECT i FROM Invoice i " +
           "WHERE i.dueDate BETWEEN CURRENT_DATE AND :futureDate " +
           "AND i.paymentStatus IN ('UNPAID', 'PARTIALLY_PAID') " +
           "ORDER BY i.dueDate ASC")
    List<Invoice> findInvoicesDueSoon(@Param("futureDate") LocalDate futureDate);

    /**
     * Calcule la TVA collectée pour une période (pour déclaration fiscale)
     */
    @Query("SELECT SUM(i.vatAmount) FROM Invoice i " +
           "WHERE i.invoiceDate BETWEEN :startDate AND :endDate " +
           "AND i.status NOT IN ('CANCELLED', 'REJECTED')")
    BigDecimal calculateVatCollectedForPeriod(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Trouve les factures récemment créées
     */
    @Query("SELECT i FROM Invoice i " +
           "WHERE i.createdAt >= :since " +
           "ORDER BY i.createdAt DESC")
    List<Invoice> findRecentlyCreatedInvoices(@Param("since") LocalDate since);

    /**
     * Trouve les factures modifiées récemment (pour audit)
     */
    @Query("SELECT i FROM Invoice i " +
           "WHERE i.updatedAt > i.createdAt " +
           "AND i.updatedAt >= :since " +
           "ORDER BY i.updatedAt DESC")
    List<Invoice> findRecentlyModifiedInvoices(@Param("since") LocalDate since);

    /**
     * Vérifie si un numéro de facture existe déjà
     */
    boolean existsByInvoiceNumber(String invoiceNumber);

    /**
     * Trouve les factures par devise
     */
    List<Invoice> findByCurrencyOrderByInvoiceDateDesc(String currency);

    /**
     * Requête native optimisée pour les rapports de performance
     */
    @Query(value = "SELECT " +
                   "DATE_TRUNC('month', invoice_date) as month, " +
                   "COUNT(*) as invoice_count, " +
                   "SUM(total_amount) as total_revenue, " +
                   "AVG(total_amount) as avg_invoice_amount " +
                   "FROM invoices " +
                   "WHERE invoice_date BETWEEN :startDate AND :endDate " +
                   "AND status NOT IN ('CANCELLED', 'REJECTED') " +
                   "GROUP BY DATE_TRUNC('month', invoice_date) " +
                   "ORDER BY month DESC", 
           nativeQuery = true)
    List<Object[]> getMonthlyRevenueStats(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Analyse des délais de paiement
     */
    @Query(value = "SELECT " +
                   "AVG(EXTRACT(DAY FROM (payment_date - due_date))) as avg_payment_delay, " +
                   "COUNT(CASE WHEN payment_date > due_date THEN 1 END) as late_payments, " +
                   "COUNT(*) as total_paid_invoices " +
                   "FROM invoices " +
                   "WHERE payment_date IS NOT NULL " +
                   "AND invoice_date BETWEEN :startDate AND :endDate", 
           nativeQuery = true)
    Object[] getPaymentDelayAnalysis(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Trouve les factures avec des montants suspects (pour détection de fraude)
     */
    @Query("SELECT i FROM Invoice i " +
           "WHERE (i.totalAmount > :highThreshold OR i.totalAmount < :lowThreshold) " +
           "AND i.createdAt >= :since " +
           "ORDER BY i.totalAmount DESC")
    List<Invoice> findSuspiciousAmountInvoices(
        @Param("highThreshold") BigDecimal highThreshold,
        @Param("lowThreshold") BigDecimal lowThreshold,
        @Param("since") LocalDate since
    );
}