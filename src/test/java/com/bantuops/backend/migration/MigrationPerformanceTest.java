package com.bantuops.backend.migration;

import com.bantuops.backend.entity.Employee;
import com.bantuops.backend.entity.PayrollRecord;
import com.bantuops.backend.entity.Invoice;
import com.bantuops.backend.repository.EmployeeRepository;
import com.bantuops.backend.repository.PayrollRepository;
import com.bantuops.backend.repository.InvoiceRepository;
import com.bantuops.backend.service.migration.DataMigrationService;
import com.bantuops.backend.service.PayrollCalculationService;
import com.bantuops.backend.service.FinancialService;
import com.bantuops.backend.dto.migration.MigrationResult;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StopWatch;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests de performance pour la migration BantuOps
 * Évalue les performances sous différentes charges et conditions
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.jdbc.batch_size=50",
    "spring.jpa.properties.hibernate.order_inserts=true",
    "spring.jpa.properties.hibernate.order_updates=true",
    "spring.jpa.properties.hibernate.jdbc.batch_versioned_data=true",
    "bantuops.encryption.validate-on-startup=false",
    "logging.level.com.bantuops=INFO"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MigrationPerformanceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("bantuops_perf_test")
            .withUsername("test")
            .withPassword("test")
            .withCommand("postgres", "-c", "max_connections=200", "-c", "shared_buffers=256MB");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withCommand("redis-server", "--maxmemory", "512mb", "--maxmemory-policy", "allkeys-lru");

    @Autowired
    private DataMigrationService dataMigrationService;

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

    private static final int SMALL_DATASET = 100;
    private static final int MEDIUM_DATASET = 1000;
    private static final int LARGE_DATASET = 5000;
    private static final int XLARGE_DATASET = 10000;

    @BeforeEach
    void setUp() {
        // Nettoyer les données existantes
        payrollRepository.deleteAll();
        invoiceRepository.deleteAll();
        employeeRepository.deleteAll();
    }

    @Test
    @Order(1)
    @DisplayName("Test de performance - Migration de petit dataset (100 entités)")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testSmallDatasetMigrationPerformance() {
        // Given
        createTestDataset(SMALL_DATASET);
        StopWatch stopWatch = new StopWatch("Small Dataset Migration");

        // When
        stopWatch.start();
        MigrationResult result = dataMigrationService.migrateAllData();
        stopWatch.stop();

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMigratedEntitiesCount()).isEqualTo(SMALL_DATASET * 3); // Employee + Payroll + Invoice

        long executionTime = stopWatch.getTotalTimeMillis();
        double throughput = (double) result.getMigratedEntitiesCount() / (executionTime / 1000.0);

        System.out.printf("Small Dataset - Temps: %dms, Débit: %.2f entités/sec%n", 
                         executionTime, throughput);

        // Assertions de performance
        assertThat(executionTime).isLessThan(10000); // Moins de 10 secondes
        assertThat(throughput).isGreaterThan(30.0); // Au moins 30 entités par seconde
    }

    @Test
    @Order(2)
    @DisplayName("Test de performance - Migration de dataset moyen (1000 entités)")
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testMediumDatasetMigrationPerformance() {
        // Given
        createTestDataset(MEDIUM_DATASET);
        StopWatch stopWatch = new StopWatch("Medium Dataset Migration");

        // When
        stopWatch.start();
        MigrationResult result = dataMigrationService.migrateAllData();
        stopWatch.stop();

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMigratedEntitiesCount()).isEqualTo(MEDIUM_DATASET * 3);

        long executionTime = stopWatch.getTotalTimeMillis();
        double throughput = (double) result.getMigratedEntitiesCount() / (executionTime / 1000.0);

        System.out.printf("Medium Dataset - Temps: %dms, Débit: %.2f entités/sec%n", 
                         executionTime, throughput);

        // Assertions de performance
        assertThat(executionTime).isLessThan(30000); // Moins de 30 secondes
        assertThat(throughput).isGreaterThan(50.0); // Au moins 50 entités par seconde
    }

    @Test
    @Order(3)
    @DisplayName("Test de performance - Migration de gros dataset (5000 entités)")
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void testLargeDatasetMigrationPerformance() {
        // Given
        createTestDataset(LARGE_DATASET);
        StopWatch stopWatch = new StopWatch("Large Dataset Migration");

        // When
        stopWatch.start();
        MigrationResult result = dataMigrationService.migrateAllData();
        stopWatch.stop();

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMigratedEntitiesCount()).isEqualTo(LARGE_DATASET * 3);

        long executionTime = stopWatch.getTotalTimeMillis();
        double throughput = (double) result.getMigratedEntitiesCount() / (executionTime / 1000.0);

        System.out.printf("Large Dataset - Temps: %dms, Débit: %.2f entités/sec%n", 
                         executionTime, throughput);

        // Assertions de performance
        assertThat(executionTime).isLessThan(90000); // Moins de 90 secondes
        assertThat(throughput).isGreaterThan(100.0); // Au moins 100 entités par seconde
    }

    @Test
    @Order(4)
    @DisplayName("Test de performance - Migration parallèle avec threads multiples")
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void testParallelMigrationPerformance() throws Exception {
        // Given
        int threadCount = 4;
        int entitiesPerThread = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        List<CompletableFuture<Long>> futures = new ArrayList<>();
        StopWatch stopWatch = new StopWatch("Parallel Migration");

        // When
        stopWatch.start();
        
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            CompletableFuture<Long> future = CompletableFuture.supplyAsync(() -> {
                StopWatch threadStopWatch = new StopWatch("Thread " + threadIndex);
                threadStopWatch.start();
                
                createTestDatasetForThread(threadIndex, entitiesPerThread);
                MigrationResult result = dataMigrationService.migrateAllData();
                
                threadStopWatch.stop();
                System.out.printf("Thread %d - Temps: %dms, Entités: %d%n", 
                                 threadIndex, threadStopWatch.getTotalTimeMillis(), 
                                 result.getMigratedEntitiesCount());
                
                return threadStopWatch.getTotalTimeMillis();
            }, executor);
            
            futures.add(future);
        }

        // Attendre que tous les threads se terminent
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
        stopWatch.stop();

        // Then
        long totalExecutionTime = stopWatch.getTotalTimeMillis();
        long totalEntities = employeeRepository.count() + payrollRepository.count() + invoiceRepository.count();
        double overallThroughput = (double) totalEntities / (totalExecutionTime / 1000.0);

        System.out.printf("Migration Parallèle - Temps total: %dms, Entités totales: %d, Débit: %.2f entités/sec%n", 
                         totalExecutionTime, totalEntities, overallThroughput);

        // Assertions de performance
        assertThat(totalExecutionTime).isLessThan(120000); // Moins de 2 minutes
        assertThat(overallThroughput).isGreaterThan(50.0); // Au moins 50 entités par seconde
        assertThat(totalEntities).isEqualTo(threadCount * entitiesPerThread * 3);

        executor.shutdown();
    }

    @Test
    @Order(5)
    @DisplayName("Test de performance - Calculs métier après migration")
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testBusinessLogicPerformanceAfterMigration() {
        // Given - Dataset migré
        createTestDataset(MEDIUM_DATASET);
        MigrationResult migrationResult = dataMigrationService.migrateAllData();
        assertThat(migrationResult.isSuccess()).isTrue();

        List<Employee> employees = employeeRepository.findAll();
        assertThat(employees).hasSize(MEDIUM_DATASET);

        StopWatch stopWatch = new StopWatch("Business Logic Performance");

        // When - Calculs de paie en masse
        stopWatch.start();
        
        List<CompletableFuture<Void>> payrollFutures = employees.stream()
            .limit(100) // Limiter à 100 pour le test de performance
            .map(employee -> CompletableFuture.runAsync(() -> {
                try {
                    payrollCalculationService.calculatePayroll(employee.getId(), YearMonth.now());
                } catch (Exception e) {
                    // Log l'erreur mais ne fait pas échouer le test
                    System.err.println("Erreur calcul paie pour employé " + employee.getId() + ": " + e.getMessage());
                }
            }))
            .toList();

        CompletableFuture.allOf(payrollFutures.toArray(new CompletableFuture[0])).join();
        stopWatch.stop();

        // Then
        long executionTime = stopWatch.getTotalTimeMillis();
        double calculationsPerSecond = 100.0 / (executionTime / 1000.0);

        System.out.printf("Calculs métier - Temps: %dms, Débit: %.2f calculs/sec%n", 
                         executionTime, calculationsPerSecond);

        // Assertions de performance
        assertThat(executionTime).isLessThan(30000); // Moins de 30 secondes pour 100 calculs
        assertThat(calculationsPerSecond).isGreaterThan(3.0); // Au moins 3 calculs par seconde
    }

    @Test
    @Order(6)
    @DisplayName("Test de performance - Utilisation mémoire pendant la migration")
    void testMemoryUsageDuringMigration() {
        // Given
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        createTestDataset(LARGE_DATASET);

        // When
        long beforeMigrationMemory = runtime.totalMemory() - runtime.freeMemory();
        MigrationResult result = dataMigrationService.migrateAllData();
        long afterMigrationMemory = runtime.totalMemory() - runtime.freeMemory();

        // Force garbage collection
        System.gc();
        Thread.yield();
        long afterGCMemory = runtime.totalMemory() - runtime.freeMemory();

        // Then
        assertThat(result.isSuccess()).isTrue();

        long migrationMemoryUsage = afterMigrationMemory - beforeMigrationMemory;
        long memoryLeakage = afterGCMemory - initialMemory;

        System.out.printf("Utilisation mémoire - Initial: %d MB, Avant migration: %d MB, Après migration: %d MB, Après GC: %d MB%n",
                         initialMemory / (1024 * 1024),
                         beforeMigrationMemory / (1024 * 1024),
                         afterMigrationMemory / (1024 * 1024),
                         afterGCMemory / (1024 * 1024));

        // Assertions de mémoire
        assertThat(migrationMemoryUsage).isLessThan(500 * 1024 * 1024); // Moins de 500 MB d'utilisation
        assertThat(memoryLeakage).isLessThan(100 * 1024 * 1024); // Moins de 100 MB de fuite mémoire
    }

    @Test
    @Order(7)
    @DisplayName("Test de performance - Stress test avec très gros dataset")
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void testStressTestWithVeryLargeDataset() {
        // Given - Très gros dataset (attention aux ressources)
        createTestDataset(XLARGE_DATASET);
        StopWatch stopWatch = new StopWatch("Stress Test Migration");

        // When
        stopWatch.start();
        MigrationResult result = dataMigrationService.migrateAllData();
        stopWatch.stop();

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMigratedEntitiesCount()).isEqualTo(XLARGE_DATASET * 3);

        long executionTime = stopWatch.getTotalTimeMillis();
        double throughput = (double) result.getMigratedEntitiesCount() / (executionTime / 1000.0);

        System.out.printf("Stress Test - Temps: %dms, Débit: %.2f entités/sec%n", 
                         executionTime, throughput);

        // Assertions de performance pour stress test
        assertThat(executionTime).isLessThan(240000); // Moins de 4 minutes
        assertThat(throughput).isGreaterThan(125.0); // Au moins 125 entités par seconde
        
        // Vérifier qu'il n'y a pas d'erreurs même avec un gros volume
        assertThat(result.getErrors()).isEmpty();
    }

    // Méthodes utilitaires

    private void createTestDataset(int size) {
        System.out.printf("Création du dataset de test avec %d entités...%n", size);
        
        List<Employee> employees = new ArrayList<>();
        List<PayrollRecord> payrolls = new ArrayList<>();
        List<Invoice> invoices = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            Employee employee = createTestEmployee(i);
            employees.add(employee);

            PayrollRecord payroll = createTestPayrollRecord(employee, i);
            payrolls.add(payroll);

            Invoice invoice = createTestInvoice(i);
            invoices.add(invoice);

            // Sauvegarder par batch pour améliorer les performances
            if (i % 100 == 0 && i > 0) {
                employeeRepository.saveAll(employees);
                payrollRepository.saveAll(payrolls);
                invoiceRepository.saveAll(invoices);
                
                employees.clear();
                payrolls.clear();
                invoices.clear();
                
                System.out.printf("Créé %d/%d entités...%n", i, size);
            }
        }

        // Sauvegarder le reste
        if (!employees.isEmpty()) {
            employeeRepository.saveAll(employees);
            payrollRepository.saveAll(payrolls);
            invoiceRepository.saveAll(invoices);
        }

        System.out.printf("Dataset de %d entités créé avec succès%n", size);
    }

    private void createTestDatasetForThread(int threadIndex, int size) {
        for (int i = 0; i < size; i++) {
            Employee employee = createTestEmployee(threadIndex * 10000 + i);
            employee.setEmployeeNumber("T" + threadIndex + "_EMP" + String.format("%06d", i));
            employee = employeeRepository.save(employee);

            PayrollRecord payroll = createTestPayrollRecord(employee, i);
            payrollRepository.save(payroll);

            Invoice invoice = createTestInvoice(threadIndex * 10000 + i);
            invoice.setInvoiceNumber("T" + threadIndex + "_INV" + String.format("%06d", i));
            invoiceRepository.save(invoice);
        }
    }

    private Employee createTestEmployee(int index) {
        Employee employee = new Employee();
        employee.setEmployeeNumber("EMP" + String.format("%06d", index));
        
        var personalInfo = new Employee.PersonalInfo();
        personalInfo.setFirstName("Employee" + index);
        personalInfo.setLastName("Test" + index);
        personalInfo.setEmail("employee" + index + "@bantuops.com");
        personalInfo.setPhoneNumber("+22177" + String.format("%07d", index % 10000000));
        personalInfo.setNationalId(String.format("%013d", index));
        personalInfo.setDateOfBirth(LocalDate.of(1980 + (index % 40), 1 + (index % 12), 1 + (index % 28)));
        employee.setPersonalInfo(personalInfo);

        var employmentInfo = new Employee.EmploymentInfo();
        employmentInfo.setPosition("Position" + (index % 10));
        employmentInfo.setDepartment("Dept" + (index % 5));
        employmentInfo.setHireDate(LocalDate.of(2020 + (index % 4), 1 + (index % 12), 1 + (index % 28)));
        employmentInfo.setBaseSalary(new BigDecimal(300000 + (index % 500000)));
        employmentInfo.setIsActive(true);
        employee.setEmploymentInfo(employmentInfo);

        return employee;
    }

    private PayrollRecord createTestPayrollRecord(Employee employee, int index) {
        PayrollRecord payroll = new PayrollRecord();
        payroll.setEmployee(employee);
        payroll.setPeriod(YearMonth.of(2024, 1 + (index % 12)));
        
        BigDecimal baseSalary = employee.getEmploymentInfo().getBaseSalary();
        payroll.setGrossSalary(baseSalary);
        payroll.setNetSalary(baseSalary.multiply(new BigDecimal("0.8")));
        payroll.setIncomeTax(baseSalary.multiply(new BigDecimal("0.1")));
        payroll.setSocialContributions(baseSalary.multiply(new BigDecimal("0.1")));
        
        return payroll;
    }

    private Invoice createTestInvoice(int index) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("INV" + String.format("%06d", index));
        
        BigDecimal baseAmount = new BigDecimal(100000 + (index % 900000));
        BigDecimal vatAmount = baseAmount.multiply(new BigDecimal("0.18"));
        
        invoice.setTotalAmount(baseAmount.add(vatAmount));
        invoice.setVatAmount(vatAmount);
        invoice.setStatus(Invoice.InvoiceStatus.values()[index % Invoice.InvoiceStatus.values().length]);
        
        return invoice;
    }
}