package com.bantuops.backend.dto;

import com.bantuops.backend.entity.Transaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO pour les filtres de recherche de transactions
 * Conforme aux exigences 2.1, 2.2, 2.5, 7.4, 7.5 pour les filtres sécurisés
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionFilter {

    private LocalDate startDate;
    private LocalDate endDate;
    private List<Transaction.TransactionType> transactionTypes;
    private List<Transaction.TransactionStatus> statuses;
    private String currency;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private String accountNumber;
    private String counterpartyName;
    private String paymentMethod;
    private String paymentChannel;
    private Boolean isReconciled;
    private Boolean isValidated;
    private Long invoiceId;
    private String searchText;
    private String createdBy;

    // Filtres de sécurité
    private Boolean includeEncryptedFields;
    private List<String> allowedFields;
    private String userRole;
    private List<String> accessibleAccounts;

    public boolean hasDateRange() {
        return startDate != null && endDate != null;
    }

    public boolean hasAmountRange() {
        return minAmount != null && maxAmount != null;
    }

    public boolean hasTextSearch() {
        return searchText != null && !searchText.trim().isEmpty();
    }

    public boolean isSecurityFilterEnabled() {
        return userRole != null && !userRole.trim().isEmpty();
    }
}