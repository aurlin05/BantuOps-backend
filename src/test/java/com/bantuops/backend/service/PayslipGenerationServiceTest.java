package com.bantuops.backend.service;

import com.bantuops.backend.dto.PayrollResult;
import com.bantuops.backend.dto.PayslipDocument;
import com.bantuops.backend.entity.Employee;
import com.bantuops.backend.exception.PayrollCalculationException;
import com.bantuops.backend.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour PayslipGenerationService
 * Conforme aux exigences 1.3, 2.3, 2.4
 */
@ExtendWith(MockitoExtension.class)
class PayslipGenerationServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private DigitalSignatureService digitalSignatureService;

    @Mock
    private PdfGenerationService pdfGenerationService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private PayslipGenerationService payslipGenerationService;

    private Employee testEmployee;
    private PayrollResult testPayrollResult;

    @BeforeEach
    void setUp() {
        // Configuration des propriétés
        ReflectionTestUtils.setField(payslipGenerationService, "companyName", "BantuOps Test");
        ReflectionTestUtils.setField(payslipGenerationService, "companyAddress", "Dakar, Sénégal");
        ReflectionTestUtils.setField(payslipGenerationService, "companyNinea", "123456789");
        ReflectionTestUtils.setField(payslipGenerationService, "companyRccm", "SN-DKR-2024-A-123");

        // Création d'un employé de test
        testEmployee = Employee.builder()
            .id(1L)
            .employeeNumber("EMP001")
            .firstName("Amadou")
            .lastName("Diallo")
            .email("amadou.diallo@bantuops.com")
            .position("Développeur Senior")
            .department("IT")
            .hireDate(LocalDate.of(2023, 1, 15))
            .contractType(Employee.ContractType.CDI)
            .baseSalary(new BigDecimal("500000"))
            .isActive(true)
            .build();

        // Création d'un résultat de paie de test
        testPayrollResult = PayrollResult.builder()
            .employeeId(1L)
            .period(YearMonth.of(2024, 1))
            .baseSalary(new BigDecimal("500000"))
            .regularHours(new BigDecimal("173.33"))
            .overtimeHours(new BigDecimal("10"))
            .regularSalary(new BigDecimal("500000"))
            .overtimeAmount(new BigDecimal("62500"))
            .grossSalary(new BigDecimal("562500"))
            .incomeTax(new BigDecimal("45000"))
            .ipresContribution(new BigDecimal("33750"))
            .cssContribution(new BigDecimal("39375"))
            .totalSocialContributions(new BigDecimal("73125"))
            .totalDeductions(new BigDecimal("0"))
            .netSalary(new BigDecimal("444375"))
            .build();
    }

    @Test
    void shouldGeneratePayslipSuccessfully() {
        // Given
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));
        when(pdfGenerationService.generatePdf(anyString())).thenReturn(new byte[]{1, 2, 3, 4, 5});
        when(digitalSignatureService.signDocument(any(byte[].class))).thenReturn("test-signature");

        // When
        PayslipDocument result = payslipGenerationService.generatePayslip(testPayrollResult);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getEmployeeId()).isEqualTo(1L);
        assertThat(result.getEmployeeName()).isEqualTo("Amadou Diallo");
        assertThat(result.getEmployeeNumber()).isEqualTo("EMP001");
        assertThat(result.getPeriod()).isEqualTo(YearMonth.of(2024, 1));
        assertThat(result.getStatus()).isEqualTo(PayslipDocument.PayslipStatus.FINALIZED);
        assertThat(result.getDigitalSignature()).isEqualTo("test-signature");
        assertThat(result.getPdfContent()).isNotNull();
        assertThat(result.getHtmlContent()).isNotNull();

        // Vérifications des appels
        verify(employeeRepository).findById(1L);
        verify(pdfGenerationService).generatePdf(anyString());
        verify(digitalSignatureService).signDocument(any(byte[].class));
        verify(auditService).logPayslipGeneration(1L, YearMonth.of(2024, 1));
    }

    @Test
    void shouldThrowExceptionWhenEmployeeNotFound() {
        // Given
        when(employeeRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> payslipGenerationService.generatePayslip(testPayrollResult))
            .isInstanceOf(PayrollCalculationException.class)
            .hasMessageContaining("Employé non trouvé");

        verify(employeeRepository).findById(1L);
        verifyNoInteractions(pdfGenerationService, digitalSignatureService);
    }

    @Test
    void shouldValidatePayrollResultBeforeGeneration() {
        // Given - PayrollResult invalide
        PayrollResult invalidResult = PayrollResult.builder()
            .employeeId(null) // ID manquant
            .period(YearMonth.of(2024, 1))
            .build();

        // When & Then
        assertThatThrownBy(() -> payslipGenerationService.generatePayslip(invalidResult))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("L'ID de l'employé est obligatoire");

        verifyNoInteractions(employeeRepository, pdfGenerationService, digitalSignatureService);
    }

    @Test
    void shouldGenerateSecurePdfSuccessfully() {
        // Given
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));
        when(pdfGenerationService.generateSecurePdfFromHtml(anyString(), anyString()))
            .thenReturn(new byte[]{1, 2, 3, 4, 5, 6, 7, 8});

        // When
        byte[] result = payslipGenerationService.generateSecurePdf(testPayrollResult);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(8);

        verify(employeeRepository).findById(1L);
        verify(pdfGenerationService).generateSecurePdfFromHtml(anyString(), anyString());
        verify(auditService).logSecurePdfGeneration(1L, YearMonth.of(2024, 1));
    }

    @Test
    void shouldGeneratePayslipWithTemplate() {
        // Given
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));
        when(pdfGenerationService.generatePdf(anyString())).thenReturn(new byte[]{1, 2, 3, 4, 5});
        when(digitalSignatureService.signDocument(any(byte[].class))).thenReturn("test-signature");
        when(digitalSignatureService.generateChecksum(any(byte[].class))).thenReturn("test-checksum");

        // When
        PayslipDocument result = payslipGenerationService.generatePayslipWithTemplate(
            testPayrollResult, "senegal_simplified");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getChecksum()).isEqualTo("test-checksum");

        verify(auditService).logPayslipGenerationWithTemplate(1L, YearMonth.of(2024, 1), "senegal_simplified");
    }

    @Test
    void shouldValidatePayslipSignature() {
        // Given
        PayslipDocument document = PayslipDocument.builder()
            .documentId("test-doc-123")
            .pdfContent(new byte[]{1, 2, 3, 4, 5})
            .digitalSignature("test-signature")
            .build();

        when(digitalSignatureService.validateSignature(any(byte[].class), anyString()))
            .thenReturn(true);

        // When
        boolean result = payslipGenerationService.validatePayslipSignature(document);

        // Then
        assertThat(result).isTrue();

        verify(digitalSignatureService).validateSignature(document.getPdfContent(), "test-signature");
        verify(auditService).logSignatureValidation("test-doc-123", true);
    }

    @Test
    void shouldFormatAmountCorrectly() {
        // Test de la méthode formatAmount via la génération HTML
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));

        // Utilisation de ReflectionTestUtils pour tester la méthode privée indirectement
        PayslipDocument result = payslipGenerationService.generatePayslip(testPayrollResult);

        // Vérification que le contenu HTML contient les montants formatés
        assertThat(result.getHtmlContent()).contains("500 000 FCFA"); // Salaire de base
        assertThat(result.getHtmlContent()).contains("444 375 FCFA"); // Salaire net
    }

    @Test
    void shouldConvertAmountToWordsInFrench() {
        // Test indirect de la conversion en lettres via le HTML généré
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));

        PayslipDocument result = payslipGenerationService.generatePayslip(testPayrollResult);

        // Vérification que le montant en lettres est présent
        assertThat(result.getHtmlContent()).contains("Montant en lettres:");
        assertThat(result.getHtmlContent()).contains("francs CFA");
    }
}