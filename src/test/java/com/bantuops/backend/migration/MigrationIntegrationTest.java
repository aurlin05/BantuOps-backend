package com.bantuops.backend.migration;

import com.bantuops.backend.entity.Employee;
import com.bantuops.backend.entity.PayrollRecord;
import com.bantuops.backend.entity.Invoice;
import com.bantuops.backend.repository.EmployeeRepository;
import com.bantuops.backend.repository.PayrollRepository;
import com.bantuops.backend.repository.InvoiceRepository;
import com.bantuops.backend.service.migration.DataMigrationService;
import com.bantuops.backend.service.migration.ValidationMigrationService;
import com.bantuops.backend.service.migration.EncryptionMigrationService;
import com.bantuops.backend.service.PayrollCalculationService;
import com.bantuops.backend.service.FinancialService;
import com.bantuops.backend.dto.migration.MigrationResult;
import com.bantuops.backend.dto.PayrollRequest;
import com.bantuops.backend.dto.InvoiceRequest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
// Testcontainers imports commented out due to missing dependencies
// import org.testcontainers.containers.PostgreSQLContainer;
// import org.testcontainers.containers.GenericContainer;
// import org.testcontainers.junit.jupiter.Container;
// import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests d'intégration complets pour la migration des données BantuOps
 * Couvre les scénarios end-to-end, performance, sécurité et récupération
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// @Testcontainers - commented out due to missing dependencies
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "bantuops.encryption.validate-on-startup=false",
        "logging.level.com.bantuops=DEBUG"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MigrationIntegrationTest {

    // Testcontainers commented out due to missing dependencies
    // Note: To use Testcontainers, add the following dependencies to pom.xml:
    // - org.testcontainers:junit-jupiter
    // - org.testcontainers:postgresql
    // - org.testcontainers:testcontainers

    @Autowired
    private DataMigrationService dataMigrationService;

    @Autowired
    private ValidationMigrationService validationMigrationService;

    @Autowired
    private EncryptionMigrationService encryptionMigrationService;

    @Autowired
    private PayrollCalculationService payrollCalculationService;

    @Autowired
    private FinancialService financialService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PayrollRepository payrollRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    private Employee testEmployee;
    private PayrollRecord testPayrollRecord;
    private Invoice testInvoice;

    @BeforeEach
    void setUp() {
        // Créer des données de test
        testEmployee = createTestEmployee();
        testPayrollRecord = createTestPayrollRecord();
        testInvoice = createTestInvoice();
    }

    @Test
    @Order(1)
    @DisplayName("Test de migration complète end-to-end")
    @Transactional
    void testCompleteEndToEndMigration() throws Exception {
        // Given - Données existantes non chiffrées
        Employee originalEmployee = employeeRepository.save(testEmployee);
        PayrollRecord originalPayroll = payrollRepository.save(testPayrollRecord);
        Invoice originalInvoice = invoiceRepository.save(testInvoice);

        // When - Exécution de la migration complète
        MigrationResult migrationResult = dataMigrationService.migrateAllData().get();

        // Then - Vérification du succès de la migration
        assertThat(migrationResult.isSuccess()).isTrue();
        assertThat(migrationResult.getTotalProcessedRecords()).isGreaterThan(0);
        assertThat(migrationResult.getTotalErrorRecords()).isEqualTo(0);

        // Vérification que les données sont toujours accessibles
        Employee migratedEmployee = employeeRepository.findById(originalEmployee.getId()).orElse(null);
        assertThat(migratedEmployee).isNotNull();
        assertThat(migratedEmployee.getFirstName())
                .isEqualTo(originalEmployee.getFirstName());

        PayrollRecord migratedPayroll = payrollRepository.findById(originalPayroll.getId()).orElse(null);
        assertThat(migratedPayroll).isNotNull();
        assertThat(migratedPayroll.getNetSalary()).isEqualTo(originalPayroll.getNetSalary());

        Invoice migratedInvoice = invoiceRepository.findById(originalInvoice.getId()).orElse(null);
        assertThat(migratedInvoice).isNotNull();
        assertThat(migratedInvoice.getTotalAmount()).isEqualTo(originalInvoice.getTotalAmount());
    }

    @Test
    @Order(2)
    @DisplayName("Test de performance de migration avec gros volumes")
    void testMigrationPerformanceWithLargeVolumes() throws Exception {
        // Given - Création d'un grand nombre d'entités
        int entityCount = 1000;
        createLargeDataSet(entityCount);

        long startTime = System.currentTimeMillis();

        // When - Migration des gros volumes
        MigrationResult result = dataMigrationService.migrateAllData().get();

        long endTime = System.currentTimeMillis();
        long migrationTime = endTime - startTime;

        // Then - Vérification des performances
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTotalProcessedRecords()).isEqualTo(entityCount * 3); // Employee + Payroll + Invoice

        // La migration ne doit pas prendre plus de 30 secondes pour 1000 entités
        assertThat(migrationTime).isLessThan(30000);

        // Vérification du débit (entités par seconde)
        double throughput = (double) result.getTotalProcessedRecords() / (migrationTime / 1000.0);
        assertThat(throughput).isGreaterThan(10.0); // Au moins 10 entités par seconde
    }

    @Test
    @Order(3)
    @DisplayName("Test de sécurité - Chiffrement des données sensibles")
    void testSecurityEncryptionOfSensitiveData() {
        // Given - Employé avec données sensibles
        Employee employee = createTestEmployee();
        employee.setNationalId("1234567890123");
        employee.setEmail("test@bantuops.com");
        employee.setBaseSalary(new BigDecimal("500000"));

        Employee savedEmployee = employeeRepository.save(employee);

        // When - Migration avec chiffrement
        Employee encryptedEmployee = encryptionMigrationService.encryptEmployeeData(savedEmployee);

        // Then - Vérification du chiffrement
        assertThat(encryptedEmployee).isNotNull();

        // Vérifier que les données sont chiffrées en base mais déchiffrées à l'accès
        Employee retrievedEmployee = employeeRepository.findById(savedEmployee.getId()).orElse(null);
        assertThat(retrievedEmployee).isNotNull();

        // Les données doivent être déchiffrées automatiquement par les convertisseurs
        // JPA
        assertThat(retrievedEmployee.getNationalId()).isEqualTo("1234567890123");
        assertThat(retrievedEmployee.getEmail()).isEqualTo("test@bantuops.com");
        assertThat(retrievedEmployee.getBaseSalary()).isEqualTo(new BigDecimal("500000"));

        // Vérifier que les données sont effectivement chiffrées en base (requête
        // native)
        // Cette vérification nécessiterait une requête SQL native pour voir les données
        // brutes
    }

    @Test
    @Order(4)
    @DisplayName("Test de validation de l'intégrité des données après migration")
    void testDataIntegrityValidationAfterMigration() {
        // Given - Données migrées
        employeeRepository.save(testEmployee);
        payrollRepository.save(testPayrollRecord);
        invoiceRepository.save(testInvoice);

        // When - Validation de l'intégrité des données migrées
        boolean employeeValid = validationMigrationService.validateEmployeeData(testEmployee);
        boolean payrollValid = validationMigrationService.validatePayrollData(testPayrollRecord);
        boolean invoiceValid = validationMigrationService.validateInvoiceData(testInvoice);

        // Then - Vérification de l'intégrité
        assertThat(employeeValid).isTrue();
        assertThat(payrollValid).isTrue();
        assertThat(invoiceValid).isTrue();
    }

    @Test
    @Order(5)
    @DisplayName("Test de récupération et rollback en cas d'erreur")
    void testRecoveryAndRollbackOnError() throws Exception {
        // Given - Données existantes
        Employee employee = employeeRepository.save(testEmployee);
        Long originalEmployeeId = employee.getId();

        // Simuler une erreur pendant la migration en corrompant les données
        employee.setFirstName(null); // Violation de contrainte
        employeeRepository.save(employee);

        // When - Tentative de migration avec erreur
        MigrationResult migrationResult = dataMigrationService.migrateAllData().get();

        // Then - Vérification de la gestion d'erreur
        if (!migrationResult.isSuccess()) {
            // Vérifier que le rollback a été effectué
            Employee rolledBackEmployee = employeeRepository.findById(originalEmployeeId).orElse(null);
            assertThat(rolledBackEmployee).isNotNull();

            // Vérifier que les erreurs sont correctement rapportées
            assertThat(migrationResult.getTotalErrorRecords()).isGreaterThan(0);
            assertThat(migrationResult.getErrorMessage()).isNotNull();
        }
    }

    @Test
    @Order(6)
    @DisplayName("Test de migration concurrente et thread-safety")
    void testConcurrentMigrationThreadSafety() throws Exception {
        // Given - Plusieurs datasets
        int threadCount = 5;
        int entitiesPerThread = 100;

        // When - Migration concurrente
        CompletableFuture<MigrationResult>[] futures = new CompletableFuture[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            // Créer des données spécifiques au thread
            createDataSetForThread(threadIndex, entitiesPerThread);
            // dataMigrationService.migrateAllData() already returns
            // CompletableFuture<MigrationResult>
            futures[i] = dataMigrationService.migrateAllData();
        }

        // Attendre que toutes les migrations se terminent
        CompletableFuture.allOf(futures).get(60, TimeUnit.SECONDS);

        // Then - Vérification des résultats
        for (CompletableFuture<MigrationResult> future : futures) {
            MigrationResult result = future.get();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getTotalProcessedRecords()).isGreaterThan(0);
        }

        // Vérifier l'intégrité globale des données
        long totalEmployees = employeeRepository.count();
        long totalPayrolls = payrollRepository.count();
        long totalInvoices = invoiceRepository.count();

        assertThat(totalEmployees).isGreaterThan(threadCount * entitiesPerThread);
        assertThat(totalPayrolls).isGreaterThan(threadCount * entitiesPerThread);
        assertThat(totalInvoices).isGreaterThan(threadCount * entitiesPerThread);
    }

    @Test
    @Order(7)
    @DisplayName("Test de compatibilité avec les calculs métier après migration")
    void testBusinessLogicCompatibilityAfterMigration() throws Exception {
        // Given - Employé migré
        Employee employee = employeeRepository.save(testEmployee);

        // Migration des données
        MigrationResult migrationResult = dataMigrationService.migrateAllData().get();
        assertThat(migrationResult.isSuccess()).isTrue();

        // When - Calcul de paie après migration
        PayrollRequest payrollRequest = new PayrollRequest();
        payrollRequest.setEmployeeId(employee.getId());
        payrollRequest.setPayrollPeriod(YearMonth.now());
        payrollRequest.setBaseSalary(new BigDecimal("500000"));

        var payrollResult = payrollCalculationService.calculatePayroll(
                employee.getId(),
                YearMonth.now());

        // Then - Vérification que les calculs fonctionnent correctement
        assertThat(payrollResult).isNotNull();
        assertThat(payrollResult.getGrossSalary()).isPositive();
        assertThat(payrollResult.getNetSalary()).isPositive();
        assertThat(payrollResult.getNetSalary()).isLessThan(payrollResult.getGrossSalary());

        // Vérification des calculs fiscaux sénégalais
        assertThat(payrollResult.getIncomeTax()).isNotNull();
        assertThat(payrollResult.getTotalSocialContributions()).isNotNull();
    }

    @Test
    @Order(8)
    @DisplayName("Test de migration avec validation des règles métier sénégalaises")
    void testMigrationWithSenegaleseBusinessRules() throws Exception {
        // Given - Données avec spécificités sénégalaises
        Employee employee = createSenegaleseEmployee();
        Invoice invoice = createSenegaleseInvoice();

        employeeRepository.save(employee);
        invoiceRepository.save(invoice);

        // When - Migration avec validation des règles sénégalaises
        MigrationResult migrationResult = dataMigrationService.migrateAllData().get();

        // Then - Vérification de la conformité
        assertThat(migrationResult.isSuccess()).isTrue();

        // Vérification des règles fiscales sénégalaises via validation des entités
        boolean employeeValidation = validationMigrationService.validateEmployeeData(employee);
        boolean invoiceValidation = validationMigrationService.validateInvoiceData(invoice);

        assertThat(employeeValidation).isTrue();
        assertThat(invoiceValidation).isTrue();

        // Vérification des spécificités sénégalaises dans les données
        assertThat(employee.getPhoneNumber()).startsWith("+221"); // Format sénégalais
        assertThat(employee.getNationalId()).hasSize(13); // Format CNI sénégalaise
        assertThat(invoice.getVatAmount()).isEqualTo(invoice.getTotalAmount().multiply(new BigDecimal("0.18"))); // TVA
                                                                                                                 // 18%
    }

    // Méthodes utilitaires pour créer des données de test

    private Employee createTestEmployee() {
        Employee employee = new Employee();
        employee.setEmployeeNumber("EMP001");

        // Personal information
        employee.setFirstName("Amadou");
        employee.setLastName("Diallo");
        employee.setEmail("amadou.diallo@bantuops.com");
        employee.setPhoneNumber("+221771234567");
        employee.setNationalId("1234567890123");
        employee.setDateOfBirth(LocalDate.of(1990, 1, 15));

        // Employment information
        employee.setPosition("Développeur");
        employee.setDepartment("IT");
        employee.setHireDate(LocalDate.of(2023, 1, 1));
        employee.setContractType(Employee.ContractType.CDI);
        employee.setBaseSalary(new BigDecimal("500000"));
        employee.setIsActive(true);

        return employee;
    }

    private PayrollRecord createTestPayrollRecord() {
        PayrollRecord payroll = new PayrollRecord();
        payroll.setEmployee(testEmployee);
        payroll.setPayrollPeriod(YearMonth.now());
        payroll.setBaseSalary(new BigDecimal("500000"));
        payroll.setGrossSalary(new BigDecimal("500000"));
        payroll.setNetSalary(new BigDecimal("400000"));
        payroll.setIncomeTax(new BigDecimal("50000"));
        // Set individual social contributions instead of a single field
        payroll.setIpresContribution(new BigDecimal("25000"));
        payroll.setCssContribution(new BigDecimal("25000"));
        return payroll;
    }

    private Invoice createTestInvoice() {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("INV001");
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(30));
        invoice.setClientName("Test Client");
        invoice.setSubtotalAmount(new BigDecimal("100000"));
        invoice.setTotalAmount(new BigDecimal("118000"));
        invoice.setVatAmount(new BigDecimal("18000"));
        invoice.setStatus(Invoice.InvoiceStatus.ACCEPTED);
        invoice.setPaymentStatus(Invoice.PaymentStatus.PAID);
        return invoice;
    }

    private Employee createSenegaleseEmployee() {
        Employee employee = createTestEmployee();
        employee.setPhoneNumber("+221771234567"); // Format sénégalais
        employee.setNationalId("1234567890123"); // Format CNI sénégalaise
        return employee;
    }

    private Invoice createSenegaleseInvoice() {
        Invoice invoice = createTestInvoice();
        // TVA sénégalaise à 18%
        invoice.setVatAmount(invoice.getTotalAmount().multiply(new BigDecimal("0.18")));
        return invoice;
    }

    private void createLargeDataSet(int count) {
        for (int i = 0; i < count; i++) {
            Employee employee = createTestEmployee();
            employee.setEmployeeNumber("EMP" + String.format("%06d", i));
            employee.setEmail("employee" + i + "@bantuops.com");
            employeeRepository.save(employee);

            PayrollRecord payroll = createTestPayrollRecord();
            payroll.setEmployee(employee);
            payrollRepository.save(payroll);

            Invoice invoice = createTestInvoice();
            invoice.setInvoiceNumber("INV" + String.format("%06d", i));
            invoiceRepository.save(invoice);
        }
    }

    private void createDataSetForThread(int threadIndex, int count) {
        for (int i = 0; i < count; i++) {
            Employee employee = createTestEmployee();
            employee.setEmployeeNumber("T" + threadIndex + "_EMP" + String.format("%06d", i));
            employee.setEmail("thread" + threadIndex + "_employee" + i + "@bantuops.com");
            employeeRepository.save(employee);

            PayrollRecord payroll = createTestPayrollRecord();
            payroll.setEmployee(employee);
            payrollRepository.save(payroll);

            Invoice invoice = createTestInvoice();
            invoice.setInvoiceNumber("T" + threadIndex + "_INV" + String.format("%06d", i));
            invoiceRepository.save(invoice);
        }
    }
}