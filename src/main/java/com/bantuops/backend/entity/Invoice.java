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
import java.util.ArrayList;
import java.util.List;

/**
 * Entité Invoice avec montants chiffrés et validation fiscale sénégalaise
 * Conforme aux exigences 2.1, 2.2, 3.1, 3.2 pour la gestion financière sécurisée
 */
@Entity
@Table(name = "invoices", indexes = {
    @Index(name = "idx_invoice_number", columnList = "invoice_number", unique = true),
    @Index(name = "idx_invoice_status", columnList = "status"),
    @Index(name = "idx_invoice_date", columnList = "invoice_date"),
    @Index(name = "idx_invoice_due_date", columnList = "due_date"),
    @Index(name = "idx_invoice_client", columnList = "client_name")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", unique = true, nullable = false, length = 50)
    private String invoiceNumber;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    // Informations client chiffrées
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "client_name", nullable = false)
    private String clientName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "client_address")
    private String clientAddress;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "client_tax_number")
    private String clientTaxNumber;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "client_email")
    private String clientEmail;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "client_phone")
    private String clientPhone;

    // Montants chiffrés
    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "subtotal_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotalAmount;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "vat_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal vatAmount;

    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal vatRate = new BigDecimal("0.1800"); // 18% TVA sénégalaise

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "discount_amount", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "paid_amount", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "remaining_amount", precision = 15, scale = 2)
    private BigDecimal remainingAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "XOF"; // Franc CFA

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "terms_and_conditions", length = 2000)
    private String termsAndConditions;

    // Informations de paiement
    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "payment_reference")
    private String paymentReference;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    // Relations
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<InvoiceItem> items = new ArrayList<>();

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
    public boolean isOverdue() {
        return dueDate.isBefore(LocalDate.now()) && paymentStatus != PaymentStatus.PAID;
    }

    public boolean isFullyPaid() {
        return paymentStatus == PaymentStatus.PAID;
    }

    public boolean isPartiallyPaid() {
        return paymentStatus == PaymentStatus.PARTIALLY_PAID;
    }

    public BigDecimal calculateRemainingAmount() {
        return totalAmount.subtract(paidAmount);
    }

    // Enums
    public enum InvoiceStatus {
        DRAFT("Brouillon"),
        SENT("Envoyée"),
        VIEWED("Vue"),
        ACCEPTED("Acceptée"),
        REJECTED("Rejetée"),
        CANCELLED("Annulée");

        private final String description;

        InvoiceStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum PaymentStatus {
        UNPAID("Non payée"),
        PARTIALLY_PAID("Partiellement payée"),
        PAID("Payée"),
        OVERDUE("En retard"),
        CANCELLED("Annulée");

        private final String description;

        PaymentStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}