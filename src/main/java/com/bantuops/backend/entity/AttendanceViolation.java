package com.bantuops.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entité pour les violations d'assiduité
 * Conforme aux exigences 3.4, 3.5, 3.6 pour le suivi des infractions
 */
@Entity
@Table(name = "attendance_violations", indexes = {
    @Index(name = "idx_violation_employee", columnList = "employee_id"),
    @Index(name = "idx_violation_date", columnList = "violation_date"),
    @Index(name = "idx_violation_type", columnList = "violation_type"),
    @Index(name = "idx_violation_severity", columnList = "severity_level")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceViolation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendance_record_id")
    private AttendanceRecord attendanceRecord;

    @Column(name = "violation_date", nullable = false)
    private LocalDate violationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "violation_type", nullable = false)
    private ViolationType violationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity_level", nullable = false)
    private SeverityLevel severityLevel;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "penalty_applied", length = 500)
    private String penaltyApplied;

    @Column(name = "warning_issued")
    @Builder.Default
    private Boolean warningIssued = false;

    @Column(name = "disciplinary_action")
    @Builder.Default
    private Boolean disciplinaryAction = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ViolationStatus status = ViolationStatus.ACTIVE;

    @Column(name = "resolved_date")
    private LocalDate resolvedDate;

    @Column(name = "resolution_notes", length = 1000)
    private String resolutionNotes;

    @Column(name = "repeat_offense_count")
    @Builder.Default
    private Integer repeatOffenseCount = 1;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;

    // Méthodes utilitaires
    public boolean isResolved() {
        return status == ViolationStatus.RESOLVED;
    }

    public boolean requiresDisciplinaryAction() {
        return severityLevel == SeverityLevel.SEVERE || repeatOffenseCount >= 3;
    }

    public boolean isRecent() {
        return violationDate.isAfter(LocalDate.now().minusDays(30));
    }

    // Enums
    public enum ViolationType {
        EXCESSIVE_DELAY("Retard excessif"),
        UNAUTHORIZED_ABSENCE("Absence non autorisée"),
        EARLY_DEPARTURE("Départ anticipé"),
        MISSING_JUSTIFICATION("Justification manquante"),
        FALSE_DECLARATION("Fausse déclaration"),
        PATTERN_VIOLATION("Violation récurrente"),
        POLICY_BREACH("Violation de politique");

        private final String description;

        ViolationType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum SeverityLevel {
        MINOR("Mineur"),
        MODERATE("Modéré"),
        SEVERE("Grave"),
        CRITICAL("Critique");

        private final String description;

        SeverityLevel(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum ViolationStatus {
        ACTIVE("Actif"),
        UNDER_REVIEW("En cours d'examen"),
        RESOLVED("Résolu"),
        DISMISSED("Rejeté");

        private final String description;

        ViolationStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}