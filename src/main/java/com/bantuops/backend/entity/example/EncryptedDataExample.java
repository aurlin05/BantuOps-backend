package com.bantuops.backend.entity.example;

import com.bantuops.backend.converter.EncryptedBigDecimalConverter;
import com.bantuops.backend.converter.EncryptedLongConverter;
import com.bantuops.backend.converter.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Exemple d'entité démontrant l'utilisation des convertisseurs de chiffrement
 * Cette classe montre comment protéger les données sensibles automatiquement
 */
@Entity
@Table(name = "encrypted_data_examples")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EncryptedDataExample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Données personnelles chiffrées
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "encrypted_name", columnDefinition = "TEXT")
    private String personalName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "encrypted_email", columnDefinition = "TEXT")
    private String email;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "encrypted_phone", columnDefinition = "TEXT")
    private String phoneNumber;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "encrypted_national_id", columnDefinition = "TEXT")
    private String nationalId;

    // Données financières chiffrées
    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "encrypted_salary", columnDefinition = "TEXT")
    private BigDecimal salary;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "encrypted_bonus", columnDefinition = "TEXT")
    private BigDecimal bonus;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "encrypted_total_amount", columnDefinition = "TEXT")
    private BigDecimal totalAmount;

    // Identifiants sensibles chiffrés
    @Convert(converter = EncryptedLongConverter.class)
    @Column(name = "encrypted_employee_number", columnDefinition = "TEXT")
    private Long employeeNumber;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "encrypted_bank_account", columnDefinition = "TEXT")
    private String bankAccountNumber;

    // Données non chiffrées (métadonnées)
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Constructeur pour les données sensibles
     */
    public EncryptedDataExample(String personalName, String email, String phoneNumber, 
                               String nationalId, BigDecimal salary, Long employeeNumber, 
                               String bankAccountNumber) {
        this.personalName = personalName;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.nationalId = nationalId;
        this.salary = salary;
        this.employeeNumber = employeeNumber;
        this.bankAccountNumber = bankAccountNumber;
        this.isActive = true;
    }

    /**
     * Méthode utilitaire pour calculer le montant total
     */
    public void calculateTotalAmount() {
        BigDecimal salaryAmount = salary != null ? salary : BigDecimal.ZERO;
        BigDecimal bonusAmount = bonus != null ? bonus : BigDecimal.ZERO;
        this.totalAmount = salaryAmount.add(bonusAmount);
    }

    /**
     * Méthode pour masquer les données sensibles dans les logs
     */
    @Override
    public String toString() {
        return "EncryptedDataExample{" +
                "id=" + id +
                ", personalName='***'" +
                ", email='***'" +
                ", phoneNumber='***'" +
                ", nationalId='***'" +
                ", salary=***" +
                ", bonus=***" +
                ", totalAmount=***" +
                ", employeeNumber=***" +
                ", bankAccountNumber='***'" +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", isActive=" + isActive +
                '}';
    }
}