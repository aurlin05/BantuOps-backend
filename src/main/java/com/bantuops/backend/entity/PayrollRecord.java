package com.bantuops.backend.entity;

import com.bantuops.backend.converter.EncryptedBigDecimalConverter;
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
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Entité PayrollRecord avec calculs sécurisés selon la législation sénégalaise
 * Conforme aux exigences 2.1, 2.2, 3.1, 3.2 pour les calculs de paie
 */
@Entity
@Table(name = "payroll_records", indexes = {
    @Index(name = "idx_payroll_employee_period", columnList = "employee_id, payroll_period", unique = true),
    @Index(name = "idx_payroll_period", columnList = "payroll_period"),
    @Index(name = "idx_payroll_status", columnList = "status"),
    @Index(name = "idx_payroll_processed_date", columnList = "processed_date")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "payroll_period", nullable = false)
    private YearMonth payrollPeriod;

    // Salaire de base et éléments variables
    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "base_salary", nullable = false, precision = 15, scale = 2)
    private BigDecimal baseSalary;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "gross_salary", nullable = false, precision = 15, scale = 2)
    private BigDecimal grossSalary;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "net_salary", nullable = false, precision = 15, scale = 2)
    private BigDecimal netSalary;

    // Heures travaillées
    @Column(name = "regular_hours", precision = 8, scale = 2)
    private BigDecimal regularHours;

    @Column(name = "overtime_hours", precision = 8, scale = 2)
    @Builder.Default
    private BigDecimal overtimeHours = BigDecimal.ZERO;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "overtime_amount", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal overtimeAmount = BigDecimal.ZERO;

    // Primes et indemnités
    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "performance_bonus", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal performanceBonus = BigDecimal.ZERO;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "transport_allowance", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal transportAllowance = BigDecimal.ZERO;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "meal_allowance", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal mealAllowance = BigDecimal.ZERO;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "housing_allowance", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal housingAllowance = BigDecimal.ZERO;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "other_allowances", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal otherAllowances = BigDecimal.ZERO;

    // Déductions fiscales et sociales (Sénégal)
    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "income_tax", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal incomeTax = BigDecimal.ZERO;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "ipres_contribution", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal ipresContribution = BigDecimal.ZERO; // Institution de Prévoyance Retraite du Sénégal

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "css_contribution", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal cssContribution = BigDecimal.ZERO; // Caisse de Sécurité Sociale

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "family_allowance_contribution", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal familyAllowanceContribution = BigDecimal.ZERO;

    // Autres déductions
    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "advance_deduction", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal advanceDeduction = BigDecimal.ZERO;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "loan_deduction", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal loanDeduction = BigDecimal.ZERO;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "absence_deduction", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal absenceDeduction = BigDecimal.ZERO;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "delay_penalty", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal delayPenalty = BigDecimal.ZERO;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "other_deductions", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal otherDeductions = BigDecimal.ZERO;

    // Totaux calculés
    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "total_allowances", precision = 15, scale = 2)
    private BigDecimal totalAllowances;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "total_deductions", precision = 15, scale = 2)
    private BigDecimal totalDeductions;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "total_social_contributions", precision = 15, scale = 2)
    private BigDecimal totalSocialContributions;

    // Statut et traitement
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private PayrollStatus status = PayrollStatus.DRAFT;

    @Column(name = "processed_date")
    private LocalDateTime processedDate;

    @Column(name = "processed_by")
    private String processedBy;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_date")
    private LocalDateTime approvedDate;

    @Column(name = "payment_date")
    private LocalDateTime paymentDate;

    @Column(name = "payment_reference")
    private String paymentReference;

    @Column(name = "notes", length = 1000)
    private String notes;

    // Relations
    @OneToMany(mappedBy = "payrollRecord", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PayrollAdjustment> adjustments = new ArrayList<>();

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
    public BigDecimal calculateTotalAllowances() {
        return performanceBonus
            .add(transportAllowance)
            .add(mealAllowance)
            .add(housingAllowance)
            .add(otherAllowances)
            .add(overtimeAmount);
    }

    public BigDecimal calculateTotalDeductions() {
        return incomeTax
            .add(advanceDeduction)
            .add(loanDeduction)
            .add(absenceDeduction)
            .add(delayPenalty)
            .add(otherDeductions);
    }

    public BigDecimal calculateTotalSocialContributions() {
        return ipresContribution
            .add(cssContribution)
            .add(familyAllowanceContribution);
    }

    public boolean isProcessed() {
        return status == PayrollStatus.PROCESSED || status == PayrollStatus.PAID;
    }

    public boolean isPaid() {
        return status == PayrollStatus.PAID;
    }

    // Enum pour le statut de la paie
    public enum PayrollStatus {
        DRAFT("Brouillon"),
        CALCULATED("Calculée"),
        REVIEWED("Révisée"),
        APPROVED("Approuvée"),
        PROCESSED("Traitée"),
        PAID("Payée"),
        CANCELLED("Annulée");

        private final String description;

        PayrollStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}