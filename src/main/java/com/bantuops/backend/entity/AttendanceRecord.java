package com.bantuops.backend.entity;

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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Entité AttendanceRecord pour la gestion des retards et absences
 * Conforme aux exigences 2.1, 2.2, 3.1, 3.2 pour le suivi de l'assiduité
 */
@Entity
@Table(name = "attendance_records", indexes = {
    @Index(name = "idx_attendance_employee_date", columnList = "employee_id, work_date", unique = true),
    @Index(name = "idx_attendance_date", columnList = "work_date"),
    @Index(name = "idx_attendance_type", columnList = "attendance_type"),
    @Index(name = "idx_attendance_status", columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    // Horaires prévus
    @Column(name = "scheduled_start_time")
    private LocalTime scheduledStartTime;

    @Column(name = "scheduled_end_time")
    private LocalTime scheduledEndTime;

    // Horaires réels
    @Column(name = "actual_start_time")
    private LocalTime actualStartTime;

    @Column(name = "actual_end_time")
    private LocalTime actualEndTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_type", nullable = false)
    private AttendanceType attendanceType;

    @Column(name = "delay_minutes")
    private Integer delayMinutes;

    @Column(name = "early_departure_minutes")
    private Integer earlyDepartureMinutes;

    @Column(name = "total_hours_worked", precision = 4, scale = 2)
    private Double totalHoursWorked;

    @Column(name = "overtime_hours", precision = 4, scale = 2)
    private Double overtimeHours;

    @Column(name = "justification", length = 500)
    private String justification;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private AttendanceStatus status = AttendanceStatus.PENDING;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    // Champs pour les absences
    @Enumerated(EnumType.STRING)
    @Column(name = "absence_type")
    private AbsenceType absenceType;

    @Column(name = "is_paid_absence")
    @Builder.Default
    private Boolean isPaidAbsence = false;

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
    public boolean isLate() {
        return delayMinutes != null && delayMinutes > 0;
    }

    public boolean hasEarlyDeparture() {
        return earlyDepartureMinutes != null && earlyDepartureMinutes > 0;
    }

    public boolean hasOvertime() {
        return overtimeHours != null && overtimeHours > 0;
    }

    public boolean isApproved() {
        return status == AttendanceStatus.APPROVED;
    }

    // Enums
    public enum AttendanceType {
        PRESENT("Présent"),
        LATE("Retard"),
        ABSENT("Absent"),
        HALF_DAY("Demi-journée"),
        SICK_LEAVE("Congé maladie"),
        VACATION("Congé"),
        AUTHORIZED_ABSENCE("Absence autorisée"),
        UNAUTHORIZED_ABSENCE("Absence non autorisée");

        private final String description;

        AttendanceType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum AttendanceStatus {
        PENDING("En attente"),
        APPROVED("Approuvé"),
        REJECTED("Rejeté"),
        UNDER_REVIEW("En cours d'examen");

        private final String description;

        AttendanceStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum AbsenceType {
        SICK_LEAVE("Congé maladie"),
        ANNUAL_LEAVE("Congé annuel"),
        MATERNITY_LEAVE("Congé maternité"),
        PATERNITY_LEAVE("Congé paternité"),
        BEREAVEMENT_LEAVE("Congé de deuil"),
        PERSONAL_LEAVE("Congé personnel"),
        TRAINING("Formation"),
        MISSION("Mission"),
        OTHER("Autre");

        private final String description;

        AbsenceType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}