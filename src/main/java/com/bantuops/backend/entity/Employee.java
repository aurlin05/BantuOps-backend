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
 * Entité Employee avec informations personnelles et professionnelles chiffrées
 * Conforme aux exigences 2.1, 2.2, 3.1, 3.2 pour la sécurisation des données RH
 */
@Entity
@Table(name = "employees", indexes = {
    @Index(name = "idx_employee_number", columnList = "employeeNumber", unique = true),
    @Index(name = "idx_employee_active", columnList = "isActive"),
    @Index(name = "idx_employee_department", columnList = "department")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 20)
    private String employeeNumber;

    // Informations personnelles chiffrées
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "email", unique = true)
    private String email;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "phone_number")
    private String phoneNumber;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "national_id", unique = true)
    private String nationalId;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "address")
    private String address;

    // Informations professionnelles
    @Column(name = "position", nullable = false)
    private String position;

    @Column(name = "department", nullable = false)
    private String department;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", nullable = false)
    private ContractType contractType;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "base_salary", nullable = false, precision = 15, scale = 2)
    private BigDecimal baseSalary;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // Horaires de travail
    @Column(name = "work_start_time")
    private String workStartTime; // Format HH:mm

    @Column(name = "work_end_time")
    private String workEndTime; // Format HH:mm

    @Column(name = "work_days")
    private String workDays; // Jours de travail séparés par des virgules

    // Relations
    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<AttendanceRecord> attendanceRecords = new ArrayList<>();

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PayrollRecord> payrollRecords = new ArrayList<>();

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
    public String getFullName() {
        return firstName + " " + lastName;
    }

    public boolean isCurrentlyEmployed() {
        return isActive != null && isActive;
    }

    // Enum pour les types de contrat
    public enum ContractType {
        CDI("Contrat à Durée Indéterminée"),
        CDD("Contrat à Durée Déterminée"),
        STAGE("Stage"),
        CONSULTANT("Consultant"),
        FREELANCE("Freelance");

        private final String description;

        ContractType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}