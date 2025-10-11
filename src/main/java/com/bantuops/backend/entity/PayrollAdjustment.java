package com.bantuops.backend.entity;

import com.bantuops.backend.converter.EncryptedBigDecimalConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entité PayrollAdjustment pour les ajustements de paie
 * Support pour les corrections et ajustements manuels
 */
@Entity
@Table(name = "payroll_adjustments", indexes = {
    @Index(name = "idx_adjustment_payroll", columnList = "payroll_record_id"),
    @Index(name = "idx_adjustment_type", columnList = "adjustment_type")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_record_id", nullable = false)
    private PayrollRecord payrollRecord;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false)
    private AdjustmentType adjustmentType;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_date")
    private LocalDateTime approvedDate;

    // Audit fields
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;

    // Enum pour les types d'ajustement
    public enum AdjustmentType {
        BONUS("Prime"),
        DEDUCTION("Déduction"),
        CORRECTION("Correction"),
        RETROACTIVE_PAY("Rappel de salaire"),
        ADVANCE_RECOVERY("Récupération d'avance"),
        OVERTIME_ADJUSTMENT("Ajustement heures supplémentaires"),
        ATTENDANCE_PENALTY("Pénalité d'assiduité"),
        OTHER("Autre");

        private final String description;

        AdjustmentType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}