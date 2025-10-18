package com.bantuops.backend.service.migration;

import com.bantuops.backend.dto.migration.MigrationResult;
import com.bantuops.backend.dto.migration.MigrationStatus;
import com.bantuops.backend.entity.Employee;
import com.bantuops.backend.entity.Invoice;
import com.bantuops.backend.entity.PayrollRecord;
import com.bantuops.backend.repository.EmployeeRepository;
import com.bantuops.backend.repository.InvoiceRepository;
import com.bantuops.backend.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service pour la migration des données existantes vers le nouveau système backend sécurisé.
 * Gère la migration des employés, factures, et enregistrements de paie avec validation complète.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataMigrationService {

    private final EmployeeRepository employeeRepository;
    private final InvoiceRepository invoiceRepository;
    private final EncryptionMigrationService encryptionMigrationService;
    private final ValidationMigrationService validationMigrationService;
    private final AuditService auditService;
    
    private final Map<String, MigrationStatus> migrationStatusMap = new ConcurrentHashMap<>();

    /**
     * Lance la migration complète des données avec validation et chiffrement
     */
    @Transactional
    public CompletableFuture<MigrationResult> migrateAllData() {
        String migrationId = generateMigrationId();
        log.info("Démarrage de la migration complète des données - ID: {}", migrationId);
        
        updateMigrationStatus(migrationId, MigrationStatus.IN_PROGRESS);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                MigrationResult result = new MigrationResult();
                result.setMigrationId(migrationId);
                result.setStartTime(LocalDateTime.now());
                
                // Migration des employés
                log.info("Migration des données employés...");
                MigrationResult.EntityMigrationResult employeeResult = migrateEmployees();
                result.addEntityResult("employees", employeeResult);
                
                // Migration des factures
                log.info("Migration des données factures...");
                MigrationResult.EntityMigrationResult invoiceResult = migrateInvoices();
                result.addEntityResult("invoices", invoiceResult);
                
                // Migration des enregistrements de paie
                log.info("Migration des enregistrements de paie...");
                MigrationResult.EntityMigrationResult payrollResult = migratePayrollRecords();
                result.addEntityResult("payroll_records", payrollResult);
                
                result.setEndTime(LocalDateTime.now());
                result.setSuccess(true);
                
                updateMigrationStatus(migrationId, MigrationStatus.COMPLETED);
                auditService.logMigrationEvent("DATA_MIGRATION_COMPLETED", migrationId, result);
                
                log.info("Migration complète terminée avec succès - ID: {}", migrationId);
                return result;
                
            } catch (Exception e) {
                log.error("Erreur lors de la migration des données - ID: {}", migrationId, e);
                updateMigrationStatus(migrationId, MigrationStatus.FAILED);
                auditService.logMigrationEvent("DATA_MIGRATION_FAILED", migrationId, e.getMessage());
                
                MigrationResult errorResult = new MigrationResult();
                errorResult.setMigrationId(migrationId);
                errorResult.setSuccess(false);
                errorResult.setErrorMessage(e.getMessage());
                return errorResult;
            }
        });
    }

    /**
     * Migre les données des employés avec chiffrement des informations sensibles
     */
    @Transactional
    public MigrationResult.EntityMigrationResult migrateEmployees() {
        MigrationResult.EntityMigrationResult result = new MigrationResult.EntityMigrationResult();
        
        try {
            List<Employee> employees = employeeRepository.findAll();
            log.info("Migration de {} employés", employees.size());
            
            int processed = 0;
            int errors = 0;
            
            for (Employee employee : employees) {
                try {
                    // Validation des données avant migration
                    if (validationMigrationService.validateEmployeeData(employee)) {
                        // Chiffrement des données sensibles
                        Employee encryptedEmployee = encryptionMigrationService.encryptEmployeeData(employee);
                        
                        // Sauvegarde de l'employé avec données chiffrées
                        employeeRepository.save(encryptedEmployee);
                        processed++;
                        
                        auditService.logMigrationEvent("EMPLOYEE_MIGRATED", employee.getId().toString(), 
                            "Employé migré avec succès");
                    } else {
                        log.warn("Données invalides pour l'employé ID: {}", employee.getId());
                        errors++;
                    }
                } catch (Exception e) {
                    log.error("Erreur lors de la migration de l'employé ID: {}", employee.getId(), e);
                    errors++;
                }
            }
            
            result.setTotalRecords(employees.size());
            result.setProcessedRecords(processed);
            result.setErrorRecords(errors);
            result.setSuccess(errors == 0);
            
        } catch (Exception e) {
            log.error("Erreur lors de la migration des employés", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }
        
        return result;
    }

    /**
     * Migre les données des factures avec chiffrement des montants
     */
    @Transactional
    public MigrationResult.EntityMigrationResult migrateInvoices() {
        MigrationResult.EntityMigrationResult result = new MigrationResult.EntityMigrationResult();
        
        try {
            List<Invoice> invoices = invoiceRepository.findAll();
            log.info("Migration de {} factures", invoices.size());
            
            int processed = 0;
            int errors = 0;
            
            for (Invoice invoice : invoices) {
                try {
                    // Validation des données de facture
                    if (validationMigrationService.validateInvoiceData(invoice)) {
                        // Chiffrement des montants sensibles
                        Invoice encryptedInvoice = encryptionMigrationService.encryptInvoiceData(invoice);
                        
                        // Sauvegarde avec données chiffrées
                        invoiceRepository.save(encryptedInvoice);
                        processed++;
                        
                        auditService.logMigrationEvent("INVOICE_MIGRATED", invoice.getId().toString(),
                            "Facture migrée avec succès");
                    } else {
                        log.warn("Données invalides pour la facture ID: {}", invoice.getId());
                        errors++;
                    }
                } catch (Exception e) {
                    log.error("Erreur lors de la migration de la facture ID: {}", invoice.getId(), e);
                    errors++;
                }
            }
            
            result.setTotalRecords(invoices.size());
            result.setProcessedRecords(processed);
            result.setErrorRecords(errors);
            result.setSuccess(errors == 0);
            
        } catch (Exception e) {
            log.error("Erreur lors de la migration des factures", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }
        
        return result;
    }

    /**
     * Migre les enregistrements de paie avec chiffrement des données salariales
     */
    @Transactional
    public MigrationResult.EntityMigrationResult migratePayrollRecords() {
        MigrationResult.EntityMigrationResult result = new MigrationResult.EntityMigrationResult();
        
        try {
            List<PayrollRecord> payrollRecords = payrollRecordRepository.findAll();
            log.info("Migration de {} enregistrements de paie", payrollRecords.size());
            
            int processed = 0;
            int errors = 0;
            
            for (PayrollRecord payrollRecord : payrollRecords) {
                try {
                    // Validation des données de paie
                    if (validationMigrationService.validatePayrollData(payrollRecord)) {
                        // Chiffrement des données salariales sensibles
                        PayrollRecord encryptedPayroll = encryptionMigrationService.encryptPayrollData(payrollRecord);
                        
                        // Sauvegarde avec données chiffrées
                        payrollRecordRepository.save(encryptedPayroll);
                        processed++;
                        
                        auditService.logMigrationEvent("PAYROLL_MIGRATED", payrollRecord.getId().toString(),
                            "Enregistrement de paie migré avec succès");
                    } else {
                        log.warn("Données invalides pour l'enregistrement de paie ID: {}", payrollRecord.getId());
                        errors++;
                    }
                } catch (Exception e) {
                    log.error("Erreur lors de la migration de l'enregistrement de paie ID: {}", 
                        payrollRecord.getId(), e);
                    errors++;
                }
            }
            
            result.setTotalRecords(payrollRecords.size());
            result.setProcessedRecords(processed);
            result.setErrorRecords(errors);
            result.setSuccess(errors == 0);
            
        } catch (Exception e) {
            log.error("Erreur lors de la migration des enregistrements de paie", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }
        
        return result;
    }

    /**
     * Récupère le statut d'une migration
     */
    public MigrationStatus getMigrationStatus(String migrationId) {
        return migrationStatusMap.getOrDefault(migrationId, MigrationStatus.NOT_FOUND);
    }

    /**
     * Met à jour le statut d'une migration
     */
    private void updateMigrationStatus(String migrationId, MigrationStatus status) {
        migrationStatusMap.put(migrationId, status);
        log.info("Statut de migration mis à jour - ID: {}, Statut: {}", migrationId, status);
    }

    /**
     * Génère un ID unique pour la migration
     */
    private String generateMigrationId() {
        return "MIGRATION_" + System.currentTimeMillis();
    }

    /**
     * Nettoie les statuts de migration anciens
     */
    public void cleanupOldMigrationStatuses() {
        // Implémentation pour nettoyer les anciens statuts
        log.info("Nettoyage des anciens statuts de migration");
    }
}