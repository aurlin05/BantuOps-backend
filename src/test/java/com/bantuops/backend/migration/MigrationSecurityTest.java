package com.bantuops.backend.migration;

import com.bantuops.backend.entity.Employee;
import com.bantuops.backend.entity.PayrollRecord;
import com.bantuops.backend.entity.Invoice;
import com.bantuops.backend.entity.AuditLog;
import com.bantuops.backend.repository.EmployeeRepository;
import com.bantuops.backend.repository.PayrollRepository;
import com.bantuops.backend.repository.InvoiceRepository;
import com.bantuops.backend.repository.AuditLogRepository;
import com.bantuops.backend.service.migration.DataMigrationService;
import com.bantuops.backend.service.migration.EncryptionMigrationService;
import com.bantuops.backend.service.migration.ValidationMigrationService;
import com.bantuops.backend.service.DataEncryptionService;
import com.bantuops.backend.service.AuditService;
import com.bantuops.backend.dto.migration.MigrationResult;

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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests de sécurité pour la migration BantuOps
 * Vérifie le chiffrement, l'audit, les permissions et la conformité
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "bantuops.encryption.key=test-encryption-key-32-chars-",
    "bantuops.encryption.validate-on-startup=true",
    "bantuops.audit.enabled=true",
    "bantuops.security.alerts.enabled=true",
    "logging.level.com.bantuops.backend.security=DEBUG"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MigrationSecurityTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("bantuops_security_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Autowired
    private DataMigrationService dataMigrationService;

    @Autowired
    private EncryptionMigrationService encryptionMigrationService;

    @Autowired
    private ValidationMigrationService validationMigrationService;

    @Autowired
    private DataEncryptionService dataEncryptionService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PayrollRepository payrollRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        // Nettoyer les données de test
        auditLogRepository.deleteAll();
        payrollRepository.deleteAll();
        invoiceRepository.deleteAll();
        employeeRepository.deleteAll();
    }

    @Test
    @Order(1)
    @DisplayName("Test de chiffrement des données sensibles pendant la migration")
    @WithMockUser(roles = "ADMIN")
    @Transactional
    void testSensitiveDataEncryptionDuringMigration() {
        // Given - Employé avec données sensibles
        Employee employee = createEmployeeWithSensitiveData();
        Employee savedEmployee = employeeRepository.save(employee);

        // When - Migration avec chiffrement
        MigrationResult migrationResult = encryptionMigrationService.encryptSensitiveData();

        // Then - Vérification du chiffrement
        assertThat(migrationResult.isSuccess()).isTrue();

        // Vérifier que les données sont chiffrées en base de données
        String rawFirstName = getRawDatabaseValue("employees", "first_name", savedEmployee.getId());
        String rawEmail = getRawDatabaseValue("employees", "email", savedEmployee.getId());
        String rawNationalId = getRawDatabaseValue("employees", "national_id", savedEmployee.getId());

        // Les données brutes ne doivent pas être en clair
        assertThat(rawFirstName).isNotEqualTo("Amadou");
        assertThat(rawEmail).isNotEqualTo("amadou.diallo@bantuops.com");
        assertThat(rawNationalId).isNotEqualTo("1234567890123");

        // Mais les données déchiffrées doivent être correctes
        Employee decryptedEmployee = employeeRepository.findById(savedEmployee.getId()).orElse(null);
        assertThat(decryptedEmployee).isNotNull();
        assertThat(decryptedEmployee.getPersonalInfo().getFirstName()).isEqualTo("Amadou");
        assertThat(decryptedEmployee.getPersonalInfo().getEmail()).isEqualTo("amadou.diallo@bantuops.com");
        assertThat(decryptedEmployee.getPersonalInfo().getNationalId()).isEqualTo("1234567890123");
    }

    @Test
    @Order(2)
    @DisplayName("Test de chiffrement des montants financiers")
    @WithMockUser(roles = "ADMIN")
    @Transactional
    void testFinancialAmountsEncryption() {
        // Given - Données financières sensibles
        PayrollRecord payroll = createPayrollWithSensitiveAmounts();
        Invoice invoice = createInvoiceWithSensitiveAmounts();

        PayrollRecord savedPayroll = payrollRepository.save(payroll);
        Invoice savedInvoice = invoiceRepository.save(invoice);

        // When - Migration avec chiffrement
        MigrationResult migrationResult = encryptionMigrationService.encryptSensitiveData();

        // Then - Vérification du chiffrement des montants
        assertThat(migrationResult.isSuccess()).isTrue();

        // Vérifier que les montants sont chiffrés en base
        String rawGrossSalary = getRawDatabaseValue("payroll_records", "gross_salary", savedPayroll.getId());
        String rawNetSalary = getRawDatabaseValue("payroll_records", "net_salary", savedPayroll.getId());
        String rawTotalAmount = getRawDatabaseValue("invoices", "total_amount", savedInvoice.getId());

        // Les montants bruts ne doivent pas être en clair
        assertThat(rawGrossSalary).doesNotContain("500000");
        assertThat(rawNetSalary).doesNotContain("400000");
        assertThat(rawTotalAmount).doesNotContain("118000");

        // Mais les montants déchiffrés doivent être corrects
        PayrollRecord decryptedPayroll = payrollRepository.findById(savedPayroll.getId()).orElse(null);
        Invoice decryptedInvoice = invoiceRepository.findById(savedInvoice.getId()).orElse(null);

        assertThat(decryptedPayroll).isNotNull();
        assertThat(decryptedPayroll.getGrossSalary()).isEqualTo(new BigDecimal("500000"));
        assertThat(decryptedPayroll.getNetSalary()).isEqualTo(new BigDecimal("400000"));

        assertThat(decryptedInvoice).isNotNull();
        assertThat(decryptedInvoice.getTotalAmount()).isEqualTo(new BigDecimal("118000"));
    }

    @Test
    @Order(3)
    @DisplayName("Test d'audit complet pendant la migration")
    @WithMockUser(username = "admin", roles = "ADMIN")
    @Transactional
    void testComprehensiveAuditDuringMigration() {
        // Given - Données à migrer
        Employee employee = createEmployeeWithSensitiveData();
        employeeRepository.save(employee);

        long initialAuditCount = auditLogRepository.count();

        // When - Migration avec audit activé
        MigrationResult migrationResult = dataMigrationService.migrateAllData();

        // Then - Vérification de l'audit
        assertThat(migrationResult.isSuccess()).isTrue();

        // Vérifier que des logs d'audit ont été créés
        long finalAuditCount = auditLogRepository.count();
        assertThat(finalAuditCount).isGreaterThan(initialAuditCount);

        // Vérifier les détails des logs d'audit
        List<AuditLog> auditLogs = auditLogRepository.findAll();
        
        boolean hasMigrationAudit = auditLogs.stream()
            .anyMatch(log -> log.getAction().contains("MIGRATION") || 
                           log.getAction().contains("ENCRYPT"));

        assertThat(hasMigrationAudit).isTrue();

        // Vérifier que l'utilisateur est correctement enregistré
        boolean hasUserInfo = auditLogs.stream()
            .anyMatch(log -> "admin".equals(log.getUserId()));

        assertThat(hasUserInfo).isTrue();

        // Vérifier que les données sensibles ne sont pas loggées en clair
        auditLogs.forEach(log -> {
            if (log.getOldValues() != null) {
                assertThat(log.getOldValues()).doesNotContain("amadou.diallo@bantuops.com");
                assertThat(log.getOldValues()).doesNotContain("1234567890123");
            }
            if (log.getNewValues() != null) {
                assertThat(log.getNewValues()).doesNotContain("amadou.diallo@bantuops.com");
                assertThat(log.getNewValues()).doesNotContain("1234567890123");
            }
        });
    }

    @Test
    @Order(4)
    @DisplayName("Test de validation des permissions pendant la migration")
    @WithMockUser(username = "user", roles = "USER")
    void testPermissionValidationDuringMigration() {
        // Given - Utilisateur avec permissions limitées
        Employee employee = createEmployeeWithSensitiveData();
        employeeRepository.save(employee);

        // When/Then - La migration doit échouer avec des permissions insuffisantes
        assertThatThrownBy(() -> {
            dataMigrationService.migrateAllData();
        }).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        // Vérifier qu'un log de sécurité a été créé
        List<AuditLog> securityLogs = auditLogRepository.findAll().stream()
            .filter(log -> log.getAction().contains("SECURITY_VIOLATION") || 
                          log.getAction().contains("ACCESS_DENIED"))
            .toList();

        assertThat(securityLogs).isNotEmpty();
    }

    @Test
    @Order(5)
    @DisplayName("Test de résistance aux injections SQL pendant la migration")
    @WithMockUser(roles = "ADMIN")
    @Transactional
    void testSQLInjectionResistance() {
        // Given - Données avec tentatives d'injection SQL
        Employee employee = createEmployeeWithSensitiveData();
        
        // Tentatives d'injection dans les champs
        employee.getPersonalInfo().setFirstName("'; DROP TABLE employees; --");
        employee.getPersonalInfo().setEmail("test@test.com'; DELETE FROM payroll_records; --");
        employee.setEmployeeNumber("EMP001' OR '1'='1");

        Employee savedEmployee = employeeRepository.save(employee);

        // When - Migration des données avec tentatives d'injection
        MigrationResult migrationResult = dataMigrationService.migrateAllData();

        // Then - Vérification que l'injection a été neutralisée
        assertThat(migrationResult.isSuccess()).isTrue();

        // Vérifier que les tables existent toujours
        long employeeCount = employeeRepository.count();
        assertThat(employeeCount).isGreaterThan(0);

        // Vérifier que les données malveillantes ont été échappées
        Employee retrievedEmployee = employeeRepository.findById(savedEmployee.getId()).orElse(null);
        assertThat(retrievedEmployee).isNotNull();
        assertThat(retrievedEmployee.getPersonalInfo().getFirstName()).contains("DROP TABLE");
        assertThat(retrievedEmployee.getPersonalInfo().getEmail()).contains("DELETE FROM");
    }

    @Test
    @Order(6)
    @DisplayName("Test de validation des règles de conformité RGPD")
    @WithMockUser(roles = "ADMIN")
    @Transactional
    void testGDPRComplianceValidation() {
        // Given - Données personnelles sensibles
        Employee employee = createEmployeeWithSensitiveData();
        employeeRepository.save(employee);

        // When - Validation de la conformité RGPD
        var validationResult = validationMigrationService.validateGDPRCompliance();

        // Then - Vérification de la conformité
        assertThat(validationResult.isCompliant()).isTrue();

        // Vérifier que les données sensibles sont chiffrées
        assertThat(validationResult.getEncryptedFieldsCount()).isGreaterThan(0);

        // Vérifier que l'audit est activé
        assertThat(validationResult.isAuditEnabled()).isTrue();

        // Vérifier les droits d'accès
        assertThat(validationResult.getAccessControlViolations()).isEmpty();

        // Vérifier la rétention des données
        assertThat(validationResult.getDataRetentionViolations()).isEmpty();
    }

    @Test
    @Order(7)
    @DisplayName("Test de détection des anomalies de sécurité")
    @WithMockUser(roles = "ADMIN")
    @Transactional
    void testSecurityAnomalyDetection() {
        // Given - Données avec anomalies potentielles
        Employee employee1 = createEmployeeWithSensitiveData();
        Employee employee2 = createEmployeeWithSensitiveData();
        
        // Créer des anomalies
        employee2.getPersonalInfo().setNationalId(employee1.getPersonalInfo().getNationalId()); // Doublon
        employee2.getPersonalInfo().setEmail("admin@system.local"); // Email suspect
        employee2.getEmploymentInfo().setBaseSalary(new BigDecimal("999999999")); // Salaire anormalement élevé

        employeeRepository.save(employee1);
        employeeRepository.save(employee2);

        // When - Détection des anomalies
        var securityResult = validationMigrationService.detectSecurityAnomalies();

        // Then - Vérification de la détection
        assertThat(securityResult.hasAnomalies()).isTrue();

        // Vérifier les types d'anomalies détectées
        assertThat(securityResult.getDuplicateIdentifiers()).isNotEmpty();
        assertThat(securityResult.getSuspiciousEmails()).isNotEmpty();
        assertThat(securityResult.getAbnormalSalaries()).isNotEmpty();

        // Vérifier qu'une alerte de sécurité a été générée
        List<AuditLog> securityAlerts = auditLogRepository.findAll().stream()
            .filter(log -> log.getAction().contains("SECURITY_ANOMALY"))
            .toList();

        assertThat(securityAlerts).isNotEmpty();
    }

    @Test
    @Order(8)
    @DisplayName("Test de chiffrement avec rotation des clés")
    @WithMockUser(roles = "ADMIN")
    @Transactional
    void testEncryptionKeyRotation() {
        // Given - Données chiffrées avec ancienne clé
        Employee employee = createEmployeeWithSensitiveData();
        Employee savedEmployee = employeeRepository.save(employee);

        // Première migration avec chiffrement
        MigrationResult firstMigration = encryptionMigrationService.encryptSensitiveData();
        assertThat(firstMigration.isSuccess()).isTrue();

        String firstEncryptedValue = getRawDatabaseValue("employees", "first_name", savedEmployee.getId());

        // When - Rotation de clé et re-chiffrement
        MigrationResult keyRotationResult = encryptionMigrationService.rotateEncryptionKeys();

        // Then - Vérification de la rotation
        assertThat(keyRotationResult.isSuccess()).isTrue();

        String secondEncryptedValue = getRawDatabaseValue("employees", "first_name", savedEmployee.getId());

        // Les valeurs chiffrées doivent être différentes
        assertThat(secondEncryptedValue).isNotEqualTo(firstEncryptedValue);

        // Mais les données déchiffrées doivent être identiques
        Employee decryptedEmployee = employeeRepository.findById(savedEmployee.getId()).orElse(null);
        assertThat(decryptedEmployee).isNotNull();
        assertThat(decryptedEmployee.getPersonalInfo().getFirstName()).isEqualTo("Amadou");
    }

    // Méthodes utilitaires

    private Employee createEmployeeWithSensitiveData() {
        Employee employee = new Employee();
        employee.setEmployeeNumber("EMP001");
        
        var personalInfo = new Employee.PersonalInfo();
        personalInfo.setFirstName("Amadou");
        personalInfo.setLastName("Diallo");
        personalInfo.setEmail("amadou.diallo@bantuops.com");
        personalInfo.setPhoneNumber("+221771234567");
        personalInfo.setNationalId("1234567890123");
        personalInfo.setDateOfBirth(LocalDate.of(1990, 1, 15));
        employee.setPersonalInfo(personalInfo);

        var employmentInfo = new Employee.EmploymentInfo();
        employmentInfo.setPosition("Développeur");
        employmentInfo.setDepartment("IT");
        employmentInfo.setHireDate(LocalDate.of(2023, 1, 1));
        employmentInfo.setBaseSalary(new BigDecimal("500000"));
        employmentInfo.setIsActive(true);
        employee.setEmploymentInfo(employmentInfo);

        return employee;
    }

    private PayrollRecord createPayrollWithSensitiveAmounts() {
        PayrollRecord payroll = new PayrollRecord();
        payroll.setPeriod(YearMonth.now());
        payroll.setGrossSalary(new BigDecimal("500000"));
        payroll.setNetSalary(new BigDecimal("400000"));
        payroll.setIncomeTax(new BigDecimal("50000"));
        payroll.setSocialContributions(new BigDecimal("50000"));
        return payroll;
    }

    private Invoice createInvoiceWithSensitiveAmounts() {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("INV001");
        invoice.setTotalAmount(new BigDecimal("118000"));
        invoice.setVatAmount(new BigDecimal("18000"));
        invoice.setStatus(Invoice.InvoiceStatus.PAID);
        return invoice;
    }

    private String getRawDatabaseValue(String tableName, String columnName, Long entityId) {
        Query query = entityManager.createNativeQuery(
            "SELECT " + columnName + " FROM " + tableName + " WHERE id = ?1"
        );
        query.setParameter(1, entityId);
        
        Object result = query.getSingleResult();
        return result != null ? result.toString() : null;
    }
}