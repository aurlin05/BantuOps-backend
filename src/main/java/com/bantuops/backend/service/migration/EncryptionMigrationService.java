package com.bantuops.backend.service.migration;

import com.bantuops.backend.entity.Employee;
import com.bantuops.backend.entity.Invoice;
import com.bantuops.backend.entity.PayrollRecord;
import com.bantuops.backend.service.AuditService;
import com.bantuops.backend.service.DataEncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Service spécialisé pour le chiffrement des données lors de la migration.
 * Applique le chiffrement AES-256 aux données sensibles selon les exigences de
 * sécurité.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EncryptionMigrationService {

    private final DataEncryptionService dataEncryptionService;
    private final AuditService auditService;

    /**
     * Chiffre les données sensibles d'un employé lors de la migration
     */
    @Transactional
    public Employee encryptEmployeeData(Employee employee) {
        log.debug("Chiffrement des données de l'employé ID: {}", employee.getId());

        try {
            // Chiffrement des informations personnelles sensibles
            // Chiffrement des données personnelles
            if (employee.getFirstName() != null && !isAlreadyEncrypted(employee.getFirstName())) {
                employee.setFirstName(dataEncryptionService.encrypt(employee.getFirstName()));
            }

            if (employee.getLastName() != null && !isAlreadyEncrypted(employee.getLastName())) {
                employee.setLastName(dataEncryptionService.encrypt(employee.getLastName()));
            }

            if (employee.getEmail() != null && !isAlreadyEncrypted(employee.getEmail())) {
                employee.setEmail(dataEncryptionService.encrypt(employee.getEmail()));
            }

            if (employee.getPhoneNumber() != null && !isAlreadyEncrypted(employee.getPhoneNumber())) {
                employee.setPhoneNumber(dataEncryptionService.encrypt(employee.getPhoneNumber()));
            }

            if (employee.getNationalId() != null && !isAlreadyEncrypted(employee.getNationalId())) {
                employee.setNationalId(dataEncryptionService.encrypt(employee.getNationalId()));
            }

            // Chiffrement des informations d'emploi sensibles
            // Chiffrement du salaire de base
            if (employee.getBaseSalary() != null
                    && !isAlreadyEncrypted(employee.getBaseSalary().toString())) {
                String encryptedSalary = dataEncryptionService.encrypt(employee.getBaseSalary().toString());
                // Note: Le convertisseur JPA gérera automatiquement le
                // chiffrement/déchiffrement
            }

            auditService.logDataAccess(
                    "EMPLOYEE_DATA_ENCRYPTED",
                    "Données employé chiffrées lors de la migration",
                    Map.of("employeeId", employee.getId().toString()));

            log.debug("Chiffrement terminé pour l'employé ID: {}", employee.getId());
            return employee;

        } catch (Exception e) {
            log.error("Erreur lors du chiffrement des données de l'employé ID: {}", employee.getId(), e);
            auditService.logDataAccess(
                    "EMPLOYEE_ENCRYPTION_FAILED",
                    "Échec du chiffrement: " + e.getMessage(),
                    Map.of("employeeId", employee.getId().toString()));
            throw new RuntimeException("Échec du chiffrement des données employé", e);
        }
    }

    /**
     * Chiffre les données sensibles d'une facture lors de la migration
     */
    @Transactional
    public Invoice encryptInvoiceData(Invoice invoice) {
        log.debug("Chiffrement des données de la facture ID: {}", invoice.getId());

        try {
            // Chiffrement des montants financiers
            if (invoice.getTotalAmount() != null && !isAlreadyEncrypted(invoice.getTotalAmount().toString())) {
                // Le convertisseur JPA gérera automatiquement le chiffrement
                log.debug("Montant total marqué pour chiffrement: {}", invoice.getTotalAmount());
            }

            if (invoice.getVatAmount() != null && !isAlreadyEncrypted(invoice.getVatAmount().toString())) {
                // Le convertisseur JPA gérera automatiquement le chiffrement
                log.debug("Montant TVA marqué pour chiffrement: {}", invoice.getVatAmount());
            }

            if (invoice.getSubtotalAmount() != null && !isAlreadyEncrypted(invoice.getSubtotalAmount().toString())) {
                // Le convertisseur JPA gérera automatiquement le chiffrement
                log.debug("Sous-total marqué pour chiffrement: {}", invoice.getSubtotalAmount());
            }

            // Chiffrement des informations client sensibles si présentes
            if (invoice.getClientName() != null && !isAlreadyEncrypted(invoice.getClientName())) {
                invoice.setClientName(dataEncryptionService.encrypt(invoice.getClientName()));
            }

            if (invoice.getClientAddress() != null && !isAlreadyEncrypted(invoice.getClientAddress())) {
                invoice.setClientAddress(dataEncryptionService.encrypt(invoice.getClientAddress()));
            }

            if (invoice.getClientEmail() != null && !isAlreadyEncrypted(invoice.getClientEmail())) {
                invoice.setClientEmail(dataEncryptionService.encrypt(invoice.getClientEmail()));
            }

            auditService.logDataAccess(
                    "INVOICE_DATA_ENCRYPTED",
                    "Données facture chiffrées lors de la migration",
                    Map.of("invoiceId", invoice.getId().toString()));

            log.debug("Chiffrement terminé pour la facture ID: {}", invoice.getId());
            return invoice;

        } catch (Exception e) {
            log.error("Erreur lors du chiffrement des données de la facture ID: {}", invoice.getId(), e);
            auditService.logDataAccess(
                    "INVOICE_ENCRYPTION_FAILED",
                    "Échec du chiffrement: " + e.getMessage(),
                    Map.of("invoiceId", invoice.getId().toString()));
            throw new RuntimeException("Échec du chiffrement des données facture", e);
        }
    }

    /**
     * Chiffre les données sensibles d'un enregistrement de paie lors de la
     * migration
     */
    @Transactional
    public PayrollRecord encryptPayrollData(PayrollRecord payrollRecord) {
        log.debug("Chiffrement des données de paie ID: {}", payrollRecord.getId());

        try {
            // Chiffrement des montants salariaux
            if (payrollRecord.getGrossSalary() != null
                    && !isAlreadyEncrypted(payrollRecord.getGrossSalary().toString())) {
                // Le convertisseur JPA gérera automatiquement le chiffrement
                log.debug("Salaire brut marqué pour chiffrement");
            }

            if (payrollRecord.getNetSalary() != null && !isAlreadyEncrypted(payrollRecord.getNetSalary().toString())) {
                // Le convertisseur JPA gérera automatiquement le chiffrement
                log.debug("Salaire net marqué pour chiffrement");
            }

            if (payrollRecord.getIncomeTax() != null && !isAlreadyEncrypted(payrollRecord.getIncomeTax().toString())) {
                // Le convertisseur JPA gérera automatiquement le chiffrement
                log.debug("Impôt sur le revenu marqué pour chiffrement");
            }

            // Chiffrement des cotisations sociales individuelles
            if (payrollRecord.getIpresContribution() != null
                    && !isAlreadyEncrypted(payrollRecord.getIpresContribution().toString())) {
                // Le convertisseur JPA gérera automatiquement le chiffrement
                log.debug("Cotisation IPRES marquée pour chiffrement");
            }

            if (payrollRecord.getCssContribution() != null
                    && !isAlreadyEncrypted(payrollRecord.getCssContribution().toString())) {
                // Le convertisseur JPA gérera automatiquement le chiffrement
                log.debug("Cotisation CSS marquée pour chiffrement");
            }

            if (payrollRecord.getOvertimeAmount() != null
                    && !isAlreadyEncrypted(payrollRecord.getOvertimeAmount().toString())) {
                // Le convertisseur JPA gérera automatiquement le chiffrement
                log.debug("Montant heures supplémentaires marqué pour chiffrement");
            }

            auditService.logDataAccess(
                    "PAYROLL_DATA_ENCRYPTED",
                    "Données de paie chiffrées lors de la migration",
                    Map.of("payrollRecordId", payrollRecord.getId().toString()));

            log.debug("Chiffrement terminé pour l'enregistrement de paie ID: {}", payrollRecord.getId());
            return payrollRecord;

        } catch (Exception e) {
            log.error("Erreur lors du chiffrement des données de paie ID: {}", payrollRecord.getId(), e);
            auditService.logDataAccess(
                    "PAYROLL_ENCRYPTION_FAILED",
                    "Échec du chiffrement: " + e.getMessage(),
                    Map.of("payrollRecordId", payrollRecord.getId().toString()));
            throw new RuntimeException("Échec du chiffrement des données de paie", e);
        }
    }

    /**
     * Chiffre une liste d'employés en lot
     */
    @Transactional
    public List<Employee> encryptEmployeeDataBatch(List<Employee> employees) {
        log.info("Chiffrement en lot de {} employés", employees.size());

        return employees.stream()
                .map(this::encryptEmployeeData)
                .toList();
    }

    /**
     * Chiffre une liste de factures en lot
     */
    @Transactional
    public List<Invoice> encryptInvoiceDataBatch(List<Invoice> invoices) {
        log.info("Chiffrement en lot de {} factures", invoices.size());

        return invoices.stream()
                .map(this::encryptInvoiceData)
                .toList();
    }

    /**
     * Chiffre une liste d'enregistrements de paie en lot
     */
    @Transactional
    public List<PayrollRecord> encryptPayrollDataBatch(List<PayrollRecord> payrollRecords) {
        log.info("Chiffrement en lot de {} enregistrements de paie", payrollRecords.size());

        return payrollRecords.stream()
                .map(this::encryptPayrollData)
                .toList();
    }

    /**
     * Vérifie si une donnée est déjà chiffrée
     */
    private boolean isAlreadyEncrypted(String data) {
        if (data == null || data.trim().isEmpty()) {
            return false;
        }

        // Vérifie si la donnée a le format d'une donnée chiffrée
        // (par exemple, si elle commence par un préfixe spécifique ou a une longueur
        // caractéristique)
        try {
            // Tentative de déchiffrement pour vérifier si c'est déjà chiffré
            dataEncryptionService.decrypt(data);
            return true; // Si le déchiffrement réussit, c'est déjà chiffré
        } catch (Exception e) {
            return false; // Si le déchiffrement échoue, ce n'est pas chiffré
        }
    }

    /**
     * Valide l'intégrité du chiffrement après migration
     */
    public boolean validateEncryptionIntegrity(String originalData, String encryptedData) {
        try {
            String decryptedData = dataEncryptionService.decrypt(encryptedData);
            return originalData.equals(decryptedData);
        } catch (Exception e) {
            log.error("Erreur lors de la validation de l'intégrité du chiffrement", e);
            return false;
        }
    }

    /**
     * Génère un rapport de chiffrement pour la migration
     */
    public String generateEncryptionReport(int totalRecords, int encryptedRecords, int failedRecords) {
        return String.format(
                "Rapport de chiffrement de migration:\n" +
                        "- Total des enregistrements: %d\n" +
                        "- Enregistrements chiffrés avec succès: %d\n" +
                        "- Échecs de chiffrement: %d\n" +
                        "- Taux de réussite: %.2f%%",
                totalRecords, encryptedRecords, failedRecords,
                totalRecords > 0 ? (encryptedRecords * 100.0 / totalRecords) : 0.0);
    }
}