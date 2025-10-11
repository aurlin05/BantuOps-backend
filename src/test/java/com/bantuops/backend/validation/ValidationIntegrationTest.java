package com.bantuops.backend.validation;

import com.bantuops.backend.dto.*;
import com.bantuops.backend.service.ValidationService;
import com.bantuops.backend.service.BusinessRuleValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests d'intégration pour la validation et gestion d'erreurs
 * Conforme aux exigences 4.2, 4.3, 4.4, 3.1, 3.2, 3.3 pour la validation complète
 */
@SpringBootTest
@ActiveProfiles("test")
class ValidationIntegrationTest {

    @Autowired
    private ValidationService validationService;

    @Autowired
    private BusinessRuleValidator businessRuleValidator;

    @Test
    void shouldValidatePayrollRequestWithSenegaleseRules() {
        // Given - Requête de paie valide
        PayrollRequest validRequest = PayrollRequest.builder()
            .employeeId(1L)
            .payrollPeriod(YearMonth.of(2024, 1))
            .baseSalary(new BigDecimal("150000")) // Au-dessus du SMIG
            .regularHours(new BigDecimal("160"))
            .overtimeHours(new BigDecimal("3")) // Dans la limite légale
            .build();

        // When
        ValidationResult result = validationService.validatePayrollRequest(validRequest);

        // Then
        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void shouldRejectPayrollRequestBelowSMIG() {
        // Given - Salaire en dessous du SMIG
        PayrollRequest invalidRequest = PayrollRequest.builder()
            .employeeId(1L)
            .payrollPeriod(YearMonth.of(2024, 1))
            .baseSalary(new BigDecimal("50000")) // En dessous du SMIG (60000)
            .regularHours(new BigDecimal("160"))
            .build();

        // When
        ValidationResult result = validationService.validatePayrollRequest(invalidRequest);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors().get(0)).contains("SMIG");
    }

    @Test
    void shouldRejectExcessiveOvertimeHours() {
        // Given - Heures supplémentaires excessives
        PayrollRequest invalidRequest = PayrollRequest.builder()
            .employeeId(1L)
            .payrollPeriod(YearMonth.of(2024, 1))
            .baseSalary(new BigDecimal("150000"))
            .regularHours(new BigDecimal("160"))
            .overtimeHours(new BigDecimal("5")) // Dépasse la limite de 4h/jour
            .build();

        // When
        ValidationResult result = validationService.validatePayrollRequest(invalidRequest);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).isNotEmpty();
    }

    @Test
    void shouldValidateEmployeeRequestWithSenegaleseRules() {
        // Given - Employé valide
        EmployeeRequest validRequest = EmployeeRequest.builder()
            .employeeNumber("EMP-001")
            .firstName("Amadou")
            .lastName("Diallo")
            .email("amadou.diallo@example.com")
            .phoneNumber("+221701234567") // Numéro sénégalais valide
            .nationalId("1234567890123")
            .dateOfBirth(LocalDate.of(1990, 1, 1)) // Âge valide
            .position("Développeur")
            .department("IT")
            .hireDate(LocalDate.of(2020, 1, 1))
            .contractType("CDI")
            .baseSalary(new BigDecimal("200000")) // Au-dessus du SMIG
            .workStartTime("08:00")
            .workEndTime("17:00")
            .workDays("LUNDI,MARDI,MERCREDI,JEUDI,VENDREDI")
            .isActive(true)
            .build();

        // When
        ValidationResult result = validationService.validateEmployeeRequest(validRequest);

        // Then
        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void shouldRejectEmployeeBelowMinimumAge() {
        // Given - Employé trop jeune
        EmployeeRequest invalidRequest = EmployeeRequest.builder()
            .employeeNumber("EMP-002")
            .firstName("Jeune")
            .lastName("Personne")
            .email("jeune@example.com")
            .phoneNumber("+221701234567")
            .nationalId("1234567890123")
            .dateOfBirth(LocalDate.of(2010, 1, 1)) // Trop jeune (14 ans)
            .position("Stagiaire")
            .department("IT")
            .hireDate(LocalDate.of(2024, 1, 1))
            .contractType("STAGE")
            .baseSalary(new BigDecimal("100000"))
            .isActive(true)
            .build();

        // When
        ValidationResult result = validationService.validateEmployeeRequest(invalidRequest);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).isNotEmpty();
    }

    @Test
    void shouldValidateInvoiceRequestWithSenegaleseRules() {
        // Given - Facture valide
        InvoiceRequest validRequest = InvoiceRequest.builder()
            .invoiceNumber("INV-2024-001")
            .invoiceDate(LocalDate.now().minusDays(1))
            .dueDate(LocalDate.now().plusDays(30))
            .clientName("Entreprise Sénégalaise SARL")
            .clientAddress("Dakar, Sénégal")
            .clientEmail("contact@entreprise.sn")
            .clientPhone("+221338123456") // Numéro fixe sénégalais
            .subtotalAmount(new BigDecimal("500000"))
            .vatRate(new BigDecimal("0.18")) // TVA sénégalaise 18%
            .currency("XOF")
            .description("Services de développement")
            .items(java.util.List.of(
                InvoiceRequest.InvoiceItemRequest.builder()
                    .description("Développement application")
                    .quantity(new BigDecimal("1"))
                    .unitPrice(new BigDecimal("500000"))
                    .unit("service")
                    .itemOrder(1)
                    .build()
            ))
            .build();

        // When
        ValidationResult result = validationService.validateInvoiceRequest(validRequest);

        // Then
        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void shouldRequireTaxNumberForLargeInvoices() {
        // Given - Facture importante sans numéro fiscal
        InvoiceRequest invalidRequest = InvoiceRequest.builder()
            .invoiceNumber("INV-2024-002")
            .invoiceDate(LocalDate.now().minusDays(1))
            .dueDate(LocalDate.now().plusDays(30))
            .clientName("Grande Entreprise")
            .subtotalAmount(new BigDecimal("2000000")) // > 1M XOF
            .vatRate(new BigDecimal("0.18"))
            .currency("XOF")
            .description("Gros contrat")
            .items(java.util.List.of(
                InvoiceRequest.InvoiceItemRequest.builder()
                    .description("Service important")
                    .quantity(new BigDecimal("1"))
                    .unitPrice(new BigDecimal("2000000"))
                    .itemOrder(1)
                    .build()
            ))
            // clientTaxNumber manquant
            .build();

        // When
        ValidationResult result = validationService.validateInvoiceRequest(invalidRequest);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors().get(0)).contains("numéro fiscal");
    }

    @Test
    void shouldValidateSenegalesePhoneNumbers() {
        // Test des numéros valides
        assertThat(businessRuleValidator.validateSenegalPhoneNumber("+221701234567")).isTrue(); // Mobile
        assertThat(businessRuleValidator.validateSenegalPhoneNumber("00221338123456")).isTrue(); // Fixe
        assertThat(businessRuleValidator.validateSenegalPhoneNumber("771234567")).isTrue(); // Mobile sans préfixe

        // Test des numéros invalides
        assertThat(businessRuleValidator.validateSenegalPhoneNumber("+33123456789")).isFalse(); // France
        assertThat(businessRuleValidator.validateSenegalPhoneNumber("123456")).isFalse(); // Trop court
        assertThat(businessRuleValidator.validateSenegalPhoneNumber("+221691234567")).isFalse(); // Préfixe invalide
    }

    @Test
    void shouldValidateSenegaleseTaxNumbers() {
        // Test d'un numéro fiscal valide (format simplifié)
        assertThat(businessRuleValidator.validateSenegalTaxNumber("1234567890123")).isTrue();

        // Test des numéros invalides
        assertThat(businessRuleValidator.validateSenegalTaxNumber("123456789")).isFalse(); // Trop court
        assertThat(businessRuleValidator.validateSenegalTaxNumber("12345678901234")).isFalse(); // Trop long
        assertThat(businessRuleValidator.validateSenegalTaxNumber("123456789012A")).isFalse(); // Contient des lettres
    }

    @Test
    void shouldValidateSenegaleseBankAccounts() {
        // Test des comptes valides
        assertThat(businessRuleValidator.validateSenegalBankAccount("12345678901234")).isTrue();
        assertThat(businessRuleValidator.validateSenegalBankAccount("1234567890")).isTrue(); // Format court

        // Test des comptes invalides
        assertThat(businessRuleValidator.validateSenegalBankAccount("123456789")).isFalse(); // Trop court
        assertThat(businessRuleValidator.validateSenegalBankAccount("12345678901234567890")).isFalse(); // Trop long
        assertThat(businessRuleValidator.validateSenegalBankAccount("0234567890123")).isFalse(); // Commence par 0
    }

    @Test
    void shouldValidateAttendanceRequestWithSenegaleseRules() {
        // Given - Assiduité valide
        AttendanceRequest validRequest = AttendanceRequest.builder()
            .employeeId(1L)
            .workDate(LocalDate.now().minusDays(1)) // Pas un dimanche
            .scheduledStartTime(java.time.LocalTime.of(8, 0))
            .scheduledEndTime(java.time.LocalTime.of(17, 0))
            .actualStartTime(java.time.LocalTime.of(8, 15))
            .actualEndTime(java.time.LocalTime.of(17, 0))
            .attendanceType("LATE")
            .delayMinutes(15)
            .totalHoursWorked(8.0) // Durée légale
            .overtimeHours(0.0)
            .build();

        // When
        ValidationResult result = validationService.validateAttendanceRequest(validRequest);

        // Then
        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void shouldRejectExcessiveDailyWorkHours() {
        // Given - Heures de travail excessives
        AttendanceRequest invalidRequest = AttendanceRequest.builder()
            .employeeId(1L)
            .workDate(LocalDate.now().minusDays(1))
            .scheduledStartTime(java.time.LocalTime.of(8, 0))
            .scheduledEndTime(java.time.LocalTime.of(17, 0))
            .actualStartTime(java.time.LocalTime.of(8, 0))
            .actualEndTime(java.time.LocalTime.of(21, 0)) // 13 heures
            .attendanceType("PRESENT")
            .totalHoursWorked(13.0) // Dépasse la limite légale
            .overtimeHours(5.0)
            .build();

        // When
        ValidationResult result = validationService.validateAttendanceRequest(invalidRequest);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).isNotEmpty();
    }
}