package com.bantuops.backend.service.migration;

import com.bantuops.backend.entity.Employee;
import com.bantuops.backend.entity.Invoice;
import com.bantuops.backend.entity.PayrollRecord;
import com.bantuops.backend.service.security.DataEncryptionService;
import com.bantuops.backend.service.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service spécialisé pour le chiffrement des données lors de la migration.
 * Applique le chiffrement AES-256 aux données sensibles selon les exigences de sécurité.
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
            if (employee.getPersonalInfo() != null) {
                var personalInfo = employee.getPersonalInfo();
                
                // Chiffrement des données personnelles
                if (personalInfo.getFirstName() != null && !isAlreadyEncrypted(personalInfo.getFirstName())) {
                    personalInfo.setFirstName(dataEncryptionService.encrypt(personalInfo.getFirstName()));
                }
                
                if (personalInfo.getLastName() != null && !isAlreadyEncrypted(personalInfo.getLastName())) {
                    personalInfo.setLastName(dataEncryptionService.encrypt(personalInfo.getLastName()));
                }
                
                if (personalInfo.getEmail() != null && !isAlreadyEncrypted(personalInfo.getEmail())) {
                    personalInfo.setEmail(dataEncryptionService.encrypt(personalInfo.getEmail()));
                }
                
                if (personalInfo.getPhoneNumber() != null && !isAlreadyEncrypted(personalInfo.getPhoneNumber())) {
                    personalInfo.setPhoneNumber(dataEncryptionService.encrypt(personalInfo.getPhoneNumber()));
                }
                
                if (personalInfo.getNationalId() != null && !isAlreadyEncrypted(personalInfo.getNationalId())) {
                    personalInfo.setNationalId(dataEncryptionService.encrypt(personalInfo.getNationalId()));
                }
            }
            
            // Chiffrement des informations d'emploi sensibles
            if (employee.getEmploymentInfo() != null) {
                var employmentInfo = employee.getEmploymentInfo();
                
                // Chiffrement du salaire de base
                if (employmentInfo.getBaseSalary() != null && !isAlreadyEncrypted(employmentInfo.getBaseSalary().toString())) {
                    String encryptedSalary = dataEncryptionService.encrypt(employmentInfo.getBaseSalary().toString());
                    // Note: Le convertisseur JPA gérera automatiquement le chiffrement/déchiffrement
                }
            }
            
            auditService.logEncryptionEvent("EMPLOYEE_DATA_ENCRYPTED", employee.getId().toString(),
                "Données employé chiffrées lors de la migration");
            
            log.debug("Chiffrement terminé pour l'employé ID: {}", employee.getId());
            return employee;
            
        } catch (Exception e) {
            log.error("Erreur lors du chiffrement des données de l'employé ID: {}", employee.getId(), e);
            auditService.logEncryptionEvent("EMPLOYEE_ENCRYPTION_FAILED", employee.getId().toString(),
                "Échec du chiffrement: " + e.getMessage());
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
            if (invoice.getClientInfo() != null && !isAlreadyEncrypted(invoice.getClientInfo())) {
                invoice.setClientInfo(dataEncryptionService.encrypt(invoice.getClientInfo()));
            }
            
            auditService.logEncryptionEvent("INVOICE_DATA_ENCRYPTED", invoice.getId().toString(),
                "Données facture chiffrées lors de la migration");
            
            log.debug("Chiffrement terminé pour la facture ID: {}", invoice.getId());
            return invoice;
            
        } catch (Exception e) {
            log.error("Erreur lors du chiffrement des données de la facture ID: {}", invoice.getId(), e);
            auditService.logEncryptionEvent("INVOICE_ENCRYPTION_FAILED", invoice.getId().toString(),
                "Échec du chiffrement: " + e.getMessage());
            throw new RuntimeException("Échec du chiffrement des données facture", e);
        }
    }

    /**
     * Chiffre les données sensibles d'un enregistrement de paie lors de la migration
     */
    @Transactional
    public PayrollRecord encryptPayrollData(PayrollRecord payrollRecord) {
        log.debug("Chiffrement des données de paie ID: {}", payrollRecord.getId());
        
        try {
            // Chiffrement des montants salariaux
            if (payrollRecord.getGrossSalary() != null && !isAlreadyEncrypted(payrollRecord.getGrossSalary().toString())) {
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
            
            if (payrollRecord.getSocialContributions() != null && !isAlreadyEncrypted(payrollRecord.getSocialContributions().toString())) {
                // Le convertisseur JPA gérera automatiquement le chiffrement
                log.debug("Cotisations sociales marquées pour chiffrement");
            }
            
            if (payrollRecord.getOvertimeAmount() != null && !isAlreadyEncrypted(payrollRecord.getOvertimeAmount().toString())) {
                // Le convertisseur JPA gérera automatiquement le chiffrement
                log.debug("Montant heures supplémentaires marqué pour chiffrement");
            }
            
            // Chiffrement des détails de paie sensibles
            if (payrollRecord.getPayrollDetails() != null && !isAlreadyEncrypted(payrollRecord.getPayrollDetails())) {
                payrollRecord.setPayrollDetails(dataEncryptionService.encrypt(payrollRecord.getPayrollDetails()));
            }
            
            auditService.logEncryptionEvent("PAYROLL_DATA_ENCRYPTED", payrollRecord.getId().toString(),
                "Données de paie chiffrées lors de la migration");
            
            log.debug("Chiffrement terminé pour l'enregistrement de paie ID: {}", payrollRecord.getId());
            return payrollRecord;
            
        } catch (Exception e) {
            log.error("Erreur lors du chiffrement des données de paie ID: {}", payrollRecord.getId(), e);
            auditService.logEncryptionEvent("PAYROLL_ENCRYPTION_FAILED", payrollRecord.getId().toString(),
                "Échec du chiffrement: " + e.getMessage());
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
        // (par exemple, si elle commence par un préfixe spécifique ou a une longueur caractéristique)
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
            totalRecords > 0 ? (encryptedRecords * 100.0 / totalRecords) : 0.0
        );
    }
}