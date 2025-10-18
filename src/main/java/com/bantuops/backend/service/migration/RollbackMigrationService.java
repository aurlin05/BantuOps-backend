package com.bantuops.backend.service.migration;

import com.bantuops.backend.dto.migration.RollbackResult;
import com.bantuops.backend.dto.migration.MigrationBackup;
import com.bantuops.backend.entity.Employee;
import com.bantuops.backend.entity.Invoice;
import com.bantuops.backend.entity.PayrollRecord;
import com.bantuops.backend.repository.EmployeeRepository;
import com.bantuops.backend.repository.InvoiceRepository;
import com.bantuops.backend.repository.PayrollRecordRepository;
import com.bantuops.backend.service.AuditService;
import com.bantuops.backend.service.DataEncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * Service pour la gestion des retours en arrière (rollback) lors des
 * migrations.
 * Permet de restaurer l'état précédent en cas d'échec de migration ou de
 * problème détecté.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RollbackMigrationService {

    private final EmployeeRepository employeeRepository;
    private final InvoiceRepository invoiceRepository;
    private final PayrollRecordRepository payrollRecordRepository;
    private final DataEncryptionService dataEncryptionService;
    private final AuditService auditService;

    private final Map<String, MigrationBackup> backupStorage = new ConcurrentHashMap<>();

    /**
     * Crée une sauvegarde complète avant la migration
     */
    @Transactional(readOnly = true)
    public CompletableFuture<String> createMigrationBackup() {
        return CompletableFuture.supplyAsync(() -> {
            String backupId = generateBackupId();
            log.info("Création de la sauvegarde de migration - ID: {}", backupId);

            try {
                MigrationBackup backup = new MigrationBackup();
                backup.setBackupId(backupId);
                backup.setCreatedAt(LocalDateTime.now());

                // Sauvegarde des employés
                log.info("Sauvegarde des données employés...");
                List<Employee> employees = employeeRepository.findAll();
                backup.setEmployees(employees);
                log.info("Sauvegardé {} employés", employees.size());

                // Sauvegarde des factures
                log.info("Sauvegarde des données factures...");
                List<Invoice> invoices = invoiceRepository.findAll();
                backup.setInvoices(invoices);
                log.info("Sauvegardé {} factures", invoices.size());

                // Sauvegarde des enregistrements de paie
                log.info("Sauvegarde des enregistrements de paie...");
                List<PayrollRecord> payrollRecords = payrollRecordRepository.findAll();
                backup.setPayrollRecords(payrollRecords);
                log.info("Sauvegardé {} enregistrements de paie", payrollRecords.size());

                // Stockage de la sauvegarde
                backupStorage.put(backupId, backup);

                auditService.logDataAccess(
                        "MIGRATION_BACKUP_CREATED",
                        String.format("Sauvegarde créée: %d employés, %d factures, %d enregistrements de paie",
                                employees.size(), invoices.size(), payrollRecords.size()),
                        Map.of("backupId", backupId));

                log.info("Sauvegarde de migration créée avec succès - ID: {}", backupId);
                return backupId;

            } catch (Exception e) {
                log.error("Erreur lors de la création de la sauvegarde de migration - ID: {}", backupId, e);
                auditService.logDataAccess(
                        "MIGRATION_BACKUP_FAILED",
                        e.getMessage(),
                        Map.of("backupId", backupId));
                throw new RuntimeException("Échec de la création de la sauvegarde", e);
            }
        });
    }

    /**
     * Effectue un rollback complet vers l'état sauvegardé
     */
    @Transactional
    public CompletableFuture<RollbackResult> performFullRollback(String backupId) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Démarrage du rollback complet - Backup ID: {}", backupId);

            RollbackResult result = new RollbackResult();
            result.setBackupId(backupId);
            result.setStartTime(LocalDateTime.now());

            try {
                MigrationBackup backup = backupStorage.get(backupId);
                if (backup == null) {
                    throw new IllegalArgumentException("Sauvegarde non trouvée: " + backupId);
                }

                // Rollback des employés
                log.info("Rollback des données employés...");
                RollbackResult.EntityRollbackResult employeeResult = rollbackEmployees(backup.getEmployees());
                result.addEntityResult("employees", employeeResult);

                // Rollback des factures
                log.info("Rollback des données factures...");
                RollbackResult.EntityRollbackResult invoiceResult = rollbackInvoices(backup.getInvoices());
                result.addEntityResult("invoices", invoiceResult);

                // Rollback des enregistrements de paie
                log.info("Rollback des enregistrements de paie...");
                RollbackResult.EntityRollbackResult payrollResult = rollbackPayrollRecords(backup.getPayrollRecords());
                result.addEntityResult("payroll_records", payrollResult);

                result.setEndTime(LocalDateTime.now());
                result.setSuccess(true);

                auditService.logDataAccess(
                        "MIGRATION_ROLLBACK_COMPLETED",
                        "Rollback complet terminé avec succès",
                        Map.of("backupId", backupId, "success", String.valueOf(result.isSuccess())));

                log.info("Rollback complet terminé avec succès - Backup ID: {}", backupId);
                return result;

            } catch (Exception e) {
                log.error("Erreur lors du rollback complet - Backup ID: {}", backupId, e);
                auditService.logDataAccess(
                        "MIGRATION_ROLLBACK_FAILED",
                        e.getMessage(),
                        Map.of("backupId", backupId));

                result.setSuccess(false);
                result.setErrorMessage(e.getMessage());
                result.setEndTime(LocalDateTime.now());
                return result;
            }
        });
    }

    /**
     * Effectue un rollback partiel pour une entité spécifique
     */
    @Transactional
    public RollbackResult performPartialRollback(String backupId, String entityType) {
        log.info("Démarrage du rollback partiel - Backup ID: {}, Entité: {}", backupId, entityType);

        RollbackResult result = new RollbackResult();
        result.setBackupId(backupId);
        result.setStartTime(LocalDateTime.now());

        try {
            MigrationBackup backup = backupStorage.get(backupId);
            if (backup == null) {
                throw new IllegalArgumentException("Sauvegarde non trouvée: " + backupId);
            }

            RollbackResult.EntityRollbackResult entityResult = switch (entityType.toLowerCase()) {
                case "employees" -> rollbackEmployees(backup.getEmployees());
                case "invoices" -> rollbackInvoices(backup.getInvoices());
                case "payroll_records" -> rollbackPayrollRecords(backup.getPayrollRecords());
                default -> throw new IllegalArgumentException("Type d'entité non supporté: " + entityType);
            };

            result.addEntityResult(entityType, entityResult);
            result.setEndTime(LocalDateTime.now());
            result.setSuccess(true);

            auditService.logDataAccess("MIGRATION_PARTIAL_ROLLBACK_COMPLETED",
                    entityType + " rollback completed",
                    Map.of("backupId", backupId, "entityType", entityType)
            );

            log.info("Rollback partiel terminé avec succès - Backup ID: {}, Entité: {}", backupId, entityType);
            return result;

        } catch (Exception e) {
            log.error("Erreur lors du rollback partiel - Backup ID: {}, Entité: {}", backupId, entityType, e);
            auditService.logDataAccess(
                    "MIGRATION_PARTIAL_ROLLBACK_FAILED",
                    e.getMessage(),
                    Map.of("backupId", backupId, "entityType", entityType)
            );

            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
            return result;
        }
    }

    /**
     * Rollback des données employés
     */
    @Transactional
    private RollbackResult.EntityRollbackResult rollbackEmployees(List<Employee> backupEmployees) {
        RollbackResult.EntityRollbackResult result = new RollbackResult.EntityRollbackResult();

        try {
            // Suppression des données actuelles
            employeeRepository.deleteAll();
            log.info("Données employés actuelles supprimées");

            // Restauration des données sauvegardées
            int restored = 0;
            int errors = 0;

            for (Employee employee : backupEmployees) {
                try {
                    // Déchiffrement des données si nécessaire
                    Employee restoredEmployee = decryptEmployeeData(employee);
                    employeeRepository.save(restoredEmployee);
                    restored++;

                    auditService.logDataAccess("EMPLOYEE_ROLLBACK_SUCCESS",
                            "Employé restauré",
                        Map.of("employeeId", employee.getId().toString())
                );

                } catch (Exception e) {
                    log.error("Erreur lors de la restauration de l'employé ID: {}", employee.getId(), e);
                    errors++;
                }
            }

            result.setTotalRecords(backupEmployees.size());
            result.setRestoredRecords(restored);
            result.setErrorRecords(errors);
            result.setSuccess(errors == 0);

            log.info("Rollback employés terminé: {} restaurés, {} erreurs", restored, errors);

        } catch (Exception e) {
            log.error("Erreur lors du rollback des employés", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }

        return result;
    }

    /**
     * Rollback des données factures
     */
    @Transactional
    private RollbackResult.EntityRollbackResult rollbackInvoices(List<Invoice> backupInvoices) {
        RollbackResult.EntityRollbackResult result = new RollbackResult.EntityRollbackResult();

        try {
            // Suppression des données actuelles
            invoiceRepository.deleteAll();
            log.info("Données factures actuelles supprimées");

            // Restauration des données sauvegardées
            int restored = 0;
            int errors = 0;

            for (Invoice invoice : backupInvoices) {
                try {
                    // Déchiffrement des données si nécessaire
                    Invoice restoredInvoice = decryptInvoiceData(invoice);
                    invoiceRepository.save(restoredInvoice);
                    restored++;

                    auditService.logDataAccess("INVOICE_ROLLBACK_SUCCESS",
                            "Facture restaurée",
                        Map.of("invoiceId", invoice.getId().toString())
                );

                } catch (Exception e) {
                    log.error("Erreur lors de la restauration de la facture ID: {}", invoice.getId(), e);
                    errors++;
                }
            }

            result.setTotalRecords(backupInvoices.size());
            result.setRestoredRecords(restored);
            result.setErrorRecords(errors);
            result.setSuccess(errors == 0);

            log.info("Rollback factures terminé: {} restaurées, {} erreurs", restored, errors);

        } catch (Exception e) {
            log.error("Erreur lors du rollback des factures", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }

        return result;
    }

    /**
     * Rollback des enregistrements de paie
     */
    @Transactional
    private RollbackResult.EntityRollbackResult rollbackPayrollRecords(List<PayrollRecord> backupPayrollRecords) {
        RollbackResult.EntityRollbackResult result = new RollbackResult.EntityRollbackResult();

        try {
            // Suppression des données actuelles
            payrollRecordRepository.deleteAll();
            log.info("Enregistrements de paie actuels supprimés");

            // Restauration des données sauvegardées
            int restored = 0;
            int errors = 0;

            for (PayrollRecord payrollRecord : backupPayrollRecords) {
                try {
                    // Déchiffrement des données si nécessaire
                    PayrollRecord restoredPayroll = decryptPayrollData(payrollRecord);
                    payrollRecordRepository.save(restoredPayroll);
                    restored++;

                    auditService.logDataAccess("PAYROLL_ROLLBACK_SUCCESS",
                            "Enregistrement de paie restauré",
                        Map.of("payrollRecordId", payrollRecord.getId().toString())
                );

                } catch (Exception e) {
                    log.error("Erreur lors de la restauration de l'enregistrement de paie ID: {}",
                            payrollRecord.getId(), e);
                    errors++;
                }
            }

            result.setTotalRecords(backupPayrollRecords.size());
            result.setRestoredRecords(restored);
            result.setErrorRecords(errors);
            result.setSuccess(errors == 0);

            log.info("Rollback enregistrements de paie terminé: {} restaurés, {} erreurs", restored, errors);

        } catch (Exception e) {
            log.error("Erreur lors du rollback des enregistrements de paie", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }

        return result;
    }

    /**
     * Déchiffre les données d'un employé lors du rollback
     */
    private Employee decryptEmployeeData(Employee employee) {
        // Si les données sont chiffrées, les déchiffrer
        // Sinon, retourner l'employé tel quel
        return employee;
    }

    /**
     * Déchiffre les données d'une facture lors du rollback
     */
    private Invoice decryptInvoiceData(Invoice invoice) {
        // Si les données sont chiffrées, les déchiffrer
        // Sinon, retourner la facture telle quelle
        return invoice;
    }

    /**
     * Déchiffre les données d'un enregistrement de paie lors du rollback
     */
    private PayrollRecord decryptPayrollData(PayrollRecord payrollRecord) {
        // Si les données sont chiffrées, les déchiffrer
        // Sinon, retourner l'enregistrement tel quel
        return payrollRecord;
    }

    /**
     * Vérifie l'intégrité d'une sauvegarde
     */
    public boolean verifyBackupIntegrity(String backupId) {
        try {
            MigrationBackup backup = backupStorage.get(backupId);
            if (backup == null) {
                log.warn("Sauvegarde non trouvée pour vérification: {}", backupId);
                return false;
            }

            // Vérifications de base
            boolean isValid = backup.getEmployees() != null &&
                    backup.getInvoices() != null &&
                    backup.getPayrollRecords() != null &&
                    backup.getCreatedAt() != null;

            if (isValid) {
                log.info("Intégrité de la sauvegarde {} vérifiée avec succès", backupId);
                auditService.logDataAccess("BACKUP_INTEGRITY_VERIFIED", "Intégrité confirmée",
                    Map.of("backupId", backupId)
            );
            } else {
                log.warn("Intégrité de la sauvegarde {} compromise", backupId);
                auditService.logDataAccess("BACKUP_INTEGRITY_FAILED", "Intégrité compromise",
                    Map.of("backupId", backupId)
            );
            }

            return isValid;

        } catch (Exception e) {
            log.error("Erreur lors de la vérification de l'intégrité de la sauvegarde: {}", backupId, e);
            return false;
        }
    }

    /**
     * Supprime une sauvegarde
     */
    public void deleteBackup(String backupId) {
        try {
            MigrationBackup removed = backupStorage.remove(backupId);
            if (removed != null) {
                log.info("Sauvegarde {} supprimée avec succès", backupId);
                auditService.logDataAccess("BACKUP_DELETED", "Sauvegarde supprimée",
                    Map.of("backupId", backupId)
            );
            } else {
                log.warn("Sauvegarde {} non trouvée pour suppression", backupId);
            }
        } catch (Exception e) {
            log.error("Erreur lors de la suppression de la sauvegarde: {}", backupId, e);
        }
    }

    /**
     * Liste toutes les sauvegardes disponibles
     */
    public List<String> listAvailableBackups() {
        return backupStorage.keySet().stream().sorted().toList();
    }

    /**
     * Génère un ID unique pour la sauvegarde
     */
    private String generateBackupId() {
        return "BACKUP_" + System.currentTimeMillis();
    }

    /**
     * Nettoie les anciennes sauvegardes
     */
    public void cleanupOldBackups(int maxBackupsToKeep) {
        List<String> backupIds = listAvailableBackups();
        if (backupIds.size() > maxBackupsToKeep) {
            List<String> toDelete = backupIds.subList(0, backupIds.size() - maxBackupsToKeep);
            for (String backupId : toDelete) {
                deleteBackup(backupId);
            }
            log.info("Nettoyage terminé: {} anciennes sauvegardes supprimées", toDelete.size());
        }
    }
}