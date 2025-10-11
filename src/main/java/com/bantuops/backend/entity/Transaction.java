package com.bantuops.backend.entity;

import com.bantuops.backend.converter.EncryptedBigDecimalConverter;
import com.bantuops.backend.converter.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entité Transaction avec chiffrement des montants
 * Conforme aux exigences 2.1, 2.2, 2.5, 7.4, 7.5 pour la gestion sécurisée des transactions
 */
@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_transaction_reference", columnList = "transaction_reference", unique = true),
    @Index(name = "idx_transaction_date", columnList = "transaction_date"),
    @Index(name = "idx_transaction_type", columnList = "transaction_type"),
    @Index(name = "idx_transaction_status", columnList = "status"),
    @Index(name = "idx_invoice_id", columnList = "invoice_id"),
    @Index(name = "idx_account_number", columnList = "account_number")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_reference", unique = true, nullable = false, length = 100)
    private String transactionReference;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "value_date")
    private LocalDate valueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    // Montants chiffrés
    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "fees", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal fees = BigDecimal.ZERO;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "net_amount", precision = 15, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "XOF";

    // Informations bancaires chiffrées
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "account_number")
    private String accountNumber;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "bank_code")
    private String bankCode;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "branch_code")
    private String branchCode;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "iban")
    private String iban;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "bic_swift")
    private String bicSwift;

    // Informations de contrepartie chiffrées
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "counterparty_name")
    private String counterpartyName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "counterparty_account")
    private String counterpartyAccount;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "counterparty_bank")
    private String counterpartyBank;

    // Description et références
    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "internal_reference", length = 100)
    private String internalReference;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "payment_channel", length = 50)
    private String paymentChannel;

    // Relations
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    // Informations de réconciliation
    @Column(name = "is_reconciled")
    @Builder.Default
    private Boolean isReconciled = false;

    @Column(name = "reconciliation_date")
    private LocalDateTime reconciliationDate;

    @Column(name = "reconciliation_reference")
    private String reconciliationReference;

    // Informations de validation
    @Column(name = "is_validated")
    @Builder.Default
    private Boolean isValidated = false;

    @Column(name = "validation_date")
    private LocalDateTime validationDate;

    @Column(name = "validated_by")
    private String validatedBy;

    // Audit fields
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "last_modified_by")
    private String lastModifiedBy;

    // Méthodes utilitaires
    public boolean isPending() {
        return status == TransactionStatus.PENDING;
    }

    public boolean isCompleted() {
        return status == TransactionStatus.COMPLETED;
    }

    public boolean isFailed() {
        return status == TransactionStatus.FAILED;
    }

    public boolean isCredit() {
        return transactionType == TransactionType.CREDIT || 
               transactionType == TransactionType.PAYMENT_RECEIVED;
    }

    public boolean isDebit() {
        return transactionType == TransactionType.DEBIT || 
               transactionType == TransactionType.PAYMENT_SENT;
    }

    public BigDecimal getEffectiveAmount() {
        return isCredit() ? amount : amount.negate();
    }

    // Enums
    public enum TransactionType {
        CREDIT("Crédit"),
        DEBIT("Débit"),
        PAYMENT_RECEIVED("Paiement reçu"),
        PAYMENT_SENT("Paiement envoyé"),
        BANK_TRANSFER("Virement bancaire"),
        MOBILE_MONEY("Mobile Money"),
        CASH("Espèces"),
        CHECK("Chèque"),
        CARD_PAYMENT("Paiement par carte"),
        WIRE_TRANSFER("Virement international"),
        FEE("Frais"),
        INTEREST("Intérêts"),
        ADJUSTMENT("Ajustement"),
        REFUND("Remboursement");

        private final String description;

        TransactionType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum TransactionStatus {
        PENDING("En attente"),
        PROCESSING("En cours de traitement"),
        COMPLETED("Terminée"),
        FAILED("Échouée"),
        CANCELLED("Annulée"),
        REJECTED("Rejetée"),
        REVERSED("Annulée/Contrepassée");

        private final String description;

        TransactionStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}