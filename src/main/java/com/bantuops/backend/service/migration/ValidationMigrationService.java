package com.bantuops.backend.service.migration;

import com.bantuops.backend.entity.Employee;
import com.bantuops.backend.entity.Invoice;
import com.bantuops.backend.entity.PayrollRecord;
import com.bantuops.backend.service.AuditService;
import com.bantuops.backend.service.BusinessRuleValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Service de validation des données lors de la migration.
 * Vérifie l'intégrité et la conformité des données avant leur migration vers le
 * système sécurisé.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationMigrationService {

    private final BusinessRuleValidator businessRuleValidator;
    private final AuditService auditService;

    // Patterns de validation pour les données sénégalaises
    private static final Pattern SENEGAL_PHONE_PATTERN = Pattern.compile("^(\\+221|221)?[0-9]{8,9}$");
    private static final Pattern SENEGAL_TAX_NUMBER_PATTERN = Pattern.compile("^[0-9]{13}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    /**
     * Valide les données d'un employé avant migration
     */
    public boolean validateEmployeeData(Employee employee) {
        log.debug("Validation des données de l'employé ID: {}", employee.getId());

        List<String> validationErrors = new ArrayList<>();

        try {
            // Validation des informations de base
            if (employee.getEmployeeNumber() == null || employee.getEmployeeNumber().trim().isEmpty()) {
                validationErrors.add("Numéro d'employé manquant");
            }

            // Validation des informations personnelles
            validatePersonalInfo(employee, validationErrors);

            // Validation des informations d'emploi
            validateEmploymentInfo(employee, validationErrors);

            // Validation des règles métier spécifiques (validation simplifiée pour la migration)
            if (employee.getBaseSalary() != null) {
                BigDecimal smigSenegal = new BigDecimal("60684");
                if (employee.getBaseSalary().compareTo(smigSenegal) < 0) {
                    validationErrors.add("Salaire inférieur au SMIG sénégalais");
                }
            }

            if (!validationErrors.isEmpty()) {
                log.warn("Erreurs de validation pour l'employé ID {}: {}",
                        employee.getId(), String.join(", ", validationErrors));
                auditService.logDataAccess("EMPLOYEE_VALIDATION_FAILED",
                        "Échec de validation employé ID: " + employee.getId(),
                        Map.of("employeeId", employee.getId().toString(), "errors", String.join(", ", validationErrors)));
                return false;
            }

            auditService.logDataAccess("EMPLOYEE_VALIDATION_SUCCESS",
                    "Validation employé réussie ID: " + employee.getId(),
                    Map.of("employeeId", employee.getId().toString()));
            return true;

        } catch (Exception e) {
            log.error("Erreur lors de la validation de l'employé ID: {}", employee.getId(), e);
            auditService.logDataAccess("EMPLOYEE_VALIDATION_ERROR",
                    "Erreur validation employé ID: " + employee.getId(),
                    Map.of("employeeId", employee.getId().toString(), "error", e.getMessage()));
            return false;
        }
    }

    /**
     * Valide les données d'une facture avant migration
     */
    public boolean validateInvoiceData(Invoice invoice) {
        log.debug("Validation des données de la facture ID: {}", invoice.getId());

        List<String> validationErrors = new ArrayList<>();

        try {
            // Validation des informations de base
            if (invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().trim().isEmpty()) {
                validationErrors.add("Numéro de facture manquant");
            }

            if (invoice.getInvoiceDate() == null) {
                validationErrors.add("Date d'émission manquante");
            } else if (invoice.getInvoiceDate().isAfter(LocalDate.now())) {
                validationErrors.add("Date d'émission future non autorisée");
            }

            // Validation des montants
            if (invoice.getTotalAmount() == null || invoice.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
                validationErrors.add("Montant total invalide");
            }

            if (invoice.getVatAmount() != null && invoice.getVatAmount().compareTo(BigDecimal.ZERO) < 0) {
                validationErrors.add("Montant TVA négatif non autorisé");
            }

            // Validation de la cohérence des montants
            if (invoice.getTotalAmount() != null && invoice.getSubtotalAmount() != null &&
                    invoice.getVatAmount() != null) {
                BigDecimal calculatedTotal = invoice.getSubtotalAmount().add(invoice.getVatAmount());
                if (invoice.getTotalAmount().compareTo(calculatedTotal) != 0) {
                    validationErrors.add("Incohérence dans les montants de la facture");
                }
            }

            // Validation du statut
            if (invoice.getStatus() == null) {
                validationErrors.add("Statut de facture manquant");
            }

            // Validation des règles métier spécifiques (validation simplifiée pour la migration)
            if (invoice.getVatRate() != null) {
                BigDecimal expectedVatRate = new BigDecimal("0.18"); // TVA sénégalaise 18%
                if (invoice.getVatRate().compareTo(expectedVatRate) != 0) {
                    validationErrors.add("Taux de TVA non conforme au standard sénégalais (18%)");
                }
            }

            if (!validationErrors.isEmpty()) {
                log.warn("Erreurs de validation pour la facture ID {}: {}",
                        invoice.getId(), String.join(", ", validationErrors));
                auditService.logDataAccess("INVOICE_VALIDATION_FAILED",
                        "Échec de validation facture ID: " + invoice.getId(),
                        Map.of("invoiceId", invoice.getId().toString(), "errors", String.join(", ", validationErrors)));
                return false;
            }

            auditService.logDataAccess("INVOICE_VALIDATION_SUCCESS",
                    "Validation facture réussie ID: " + invoice.getId(),
                    Map.of("invoiceId", invoice.getId().toString()));
            return true;

        } catch (Exception e) {
            log.error("Erreur lors de la validation de la facture ID: {}", invoice.getId(), e);
            auditService.logDataAccess("INVOICE_VALIDATION_ERROR",
                    "Erreur validation facture ID: " + invoice.getId(),
                    Map.of("invoiceId", invoice.getId().toString(), "error", e.getMessage()));
            return false;
        }
    }

    /**
     * Valide les données d'un enregistrement de paie avant migration
     */
    public boolean validatePayrollData(PayrollRecord payrollRecord) {
        log.debug("Validation des données de paie ID: {}", payrollRecord.getId());

        List<String> validationErrors = new ArrayList<>();

        try {
            // Validation des informations de base
            if (payrollRecord.getEmployee() == null) {
                validationErrors.add("Employé associé manquant");
            }

            if (payrollRecord.getPayrollPeriod() == null) {
                validationErrors.add("Période de paie manquante");
            }

            // Validation des montants salariaux
            if (payrollRecord.getGrossSalary() == null
                    || payrollRecord.getGrossSalary().compareTo(BigDecimal.ZERO) <= 0) {
                validationErrors.add("Salaire brut invalide");
            }

            if (payrollRecord.getNetSalary() == null || payrollRecord.getNetSalary().compareTo(BigDecimal.ZERO) <= 0) {
                validationErrors.add("Salaire net invalide");
            }

            // Validation de la cohérence des montants
            if (payrollRecord.getGrossSalary() != null && payrollRecord.getNetSalary() != null) {
                if (payrollRecord.getNetSalary().compareTo(payrollRecord.getGrossSalary()) > 0) {
                    validationErrors.add("Salaire net supérieur au salaire brut");
                }
            }

            // Validation des déductions
            if (payrollRecord.getIncomeTax() != null && payrollRecord.getIncomeTax().compareTo(BigDecimal.ZERO) < 0) {
                validationErrors.add("Impôt sur le revenu négatif non autorisé");
            }

            if (payrollRecord.getIpresContribution() != null &&
                    payrollRecord.getIpresContribution().compareTo(BigDecimal.ZERO) < 0) {
                validationErrors.add("Cotisations IPRES négatives non autorisées");
            }
            
            if (payrollRecord.getCssContribution() != null &&
                    payrollRecord.getCssContribution().compareTo(BigDecimal.ZERO) < 0) {
                validationErrors.add("Cotisations CSS négatives non autorisées");
            }

            // Validation des heures supplémentaires
            if (payrollRecord.getOvertimeHours() != null && payrollRecord.getOvertimeHours().compareTo(BigDecimal.ZERO) < 0) {
                validationErrors.add("Heures supplémentaires négatives non autorisées");
            }

            if (payrollRecord.getOvertimeAmount() != null
                    && payrollRecord.getOvertimeAmount().compareTo(BigDecimal.ZERO) < 0) {
                validationErrors.add("Montant heures supplémentaires négatif non autorisé");
            }

            // Validation des règles métier spécifiques (validation simplifiée pour la migration)
            if (payrollRecord.getGrossSalary() != null) {
                BigDecimal smigSenegal = new BigDecimal("60684");
                if (payrollRecord.getGrossSalary().compareTo(smigSenegal) < 0) {
                    validationErrors.add("Salaire brut inférieur au SMIG sénégalais");
                }
            }

            if (!validationErrors.isEmpty()) {
                log.warn("Erreurs de validation pour l'enregistrement de paie ID {}: {}",
                        payrollRecord.getId(), String.join(", ", validationErrors));
                auditService.logDataAccess("PAYROLL_VALIDATION_FAILED",
                        "Échec de validation paie ID: " + payrollRecord.getId(),
                        Map.of("payrollId", payrollRecord.getId().toString(), "errors", String.join(", ", validationErrors)));
                return false;
            }

            auditService.logDataAccess("PAYROLL_VALIDATION_SUCCESS",
                    "Validation paie réussie ID: " + payrollRecord.getId(),
                    Map.of("payrollId", payrollRecord.getId().toString()));
            return true;

        } catch (Exception e) {
            log.error("Erreur lors de la validation de l'enregistrement de paie ID: {}", payrollRecord.getId(), e);
            auditService.logDataAccess("PAYROLL_VALIDATION_ERROR",
                    "Erreur validation paie ID: " + payrollRecord.getId(),
                    Map.of("payrollId", payrollRecord.getId().toString(), "error", e.getMessage()));
            return false;
        }
    }

    /**
     * Valide les informations personnelles d'un employé
     */
    private void validatePersonalInfo(Employee employee, List<String> validationErrors) {
        if (employee.getFirstName() == null || employee.getFirstName().trim().isEmpty()) {
            validationErrors.add("Prénom manquant");
        }

        if (employee.getLastName() == null || employee.getLastName().trim().isEmpty()) {
            validationErrors.add("Nom de famille manquant");
        }

        if (employee.getEmail() != null && !employee.getEmail().trim().isEmpty()) {
            if (!EMAIL_PATTERN.matcher(employee.getEmail()).matches()) {
                validationErrors.add("Format d'email invalide");
            }
        }

        if (employee.getPhoneNumber() != null && !employee.getPhoneNumber().trim().isEmpty()) {
            if (!SENEGAL_PHONE_PATTERN.matcher(employee.getPhoneNumber()).matches()) {
                validationErrors.add("Format de numéro de téléphone sénégalais invalide");
            }
        }

        if (employee.getNationalId() != null && !employee.getNationalId().trim().isEmpty()) {
            if (!SENEGAL_TAX_NUMBER_PATTERN.matcher(employee.getNationalId()).matches()) {
                validationErrors.add("Numéro d'identité nationale sénégalais invalide");
            }
        }

        if (employee.getDateOfBirth() != null) {
            if (employee.getDateOfBirth().isAfter(LocalDate.now())) {
                validationErrors.add("Date de naissance future non autorisée");
            }

            // Vérification de l'âge minimum (16 ans au Sénégal)
            if (employee.getDateOfBirth().isAfter(LocalDate.now().minusYears(16))) {
                validationErrors.add("Âge minimum non respecté (16 ans)");
            }
        }
    }

    /**
     * Valide les informations d'emploi d'un employé
     */
    private void validateEmploymentInfo(Employee employee, List<String> validationErrors) {
        if (employee.getPosition() == null || employee.getPosition().trim().isEmpty()) {
            validationErrors.add("Poste manquant");
        }

        if (employee.getDepartment() == null || employee.getDepartment().trim().isEmpty()) {
            validationErrors.add("Département manquant");
        }

        if (employee.getHireDate() == null) {
            validationErrors.add("Date d'embauche manquante");
        } else if (employee.getHireDate().isAfter(LocalDate.now())) {
            validationErrors.add("Date d'embauche future non autorisée");
        }

        if (employee.getContractType() == null) {
            validationErrors.add("Type de contrat manquant");
        }

        if (employee.getBaseSalary() == null || employee.getBaseSalary().compareTo(BigDecimal.ZERO) <= 0) {
            validationErrors.add("Salaire de base invalide");
        } else {
            // Vérification du salaire minimum sénégalais (SMIG)
            BigDecimal smigSenegal = new BigDecimal("60684"); // SMIG mensuel au Sénégal
            if (employee.getBaseSalary().compareTo(smigSenegal) < 0) {
                validationErrors.add("Salaire inférieur au SMIG sénégalais");
            }
        }

        if (employee.getIsActive() == null) {
            validationErrors.add("Statut d'activité manquant");
        }
    }

    /**
     * Valide l'intégrité des données après migration
     */
    public boolean validateDataIntegrity(Object originalData, Object migratedData) {
        try {
            // Comparaison des données critiques pour s'assurer qu'elles n'ont pas été
            // corrompues
            if (originalData == null && migratedData == null) {
                return true;
            }

            if (originalData == null || migratedData == null) {
                return false;
            }

            // Validation spécifique selon le type de données
            if (originalData instanceof Employee && migratedData instanceof Employee) {
                return validateEmployeeIntegrity((Employee) originalData, (Employee) migratedData);
            } else if (originalData instanceof Invoice && migratedData instanceof Invoice) {
                return validateInvoiceIntegrity((Invoice) originalData, (Invoice) migratedData);
            } else if (originalData instanceof PayrollRecord && migratedData instanceof PayrollRecord) {
                return validatePayrollIntegrity((PayrollRecord) originalData, (PayrollRecord) migratedData);
            }

            return true;

        } catch (Exception e) {
            log.error("Erreur lors de la validation de l'intégrité des données", e);
            return false;
        }
    }

    /**
     * Valide l'intégrité des données d'un employé après migration
     */
    private boolean validateEmployeeIntegrity(Employee original, Employee migrated) {
        return original.getId().equals(migrated.getId()) &&
                original.getEmployeeNumber().equals(migrated.getEmployeeNumber()) &&
                original.getCreatedAt().equals(migrated.getCreatedAt());
    }

    /**
     * Valide l'intégrité des données d'une facture après migration
     */
    private boolean validateInvoiceIntegrity(Invoice original, Invoice migrated) {
        return original.getId().equals(migrated.getId()) &&
                original.getInvoiceNumber().equals(migrated.getInvoiceNumber()) &&
                original.getInvoiceDate().equals(migrated.getInvoiceDate());
    }

    /**
     * Valide l'intégrité des données d'un enregistrement de paie après migration
     */
    private boolean validatePayrollIntegrity(PayrollRecord original, PayrollRecord migrated) {
        return original.getId().equals(migrated.getId()) &&
                original.getPayrollPeriod().equals(migrated.getPayrollPeriod()) &&
                original.getEmployee().getId().equals(migrated.getEmployee().getId());
    }

    /**
     * Génère un rapport de validation pour la migration
     */
    public String generateValidationReport(int totalRecords, int validRecords, int invalidRecords) {
        return String.format(
                "Rapport de validation de migration:\n" +
                        "- Total des enregistrements: %d\n" +
                        "- Enregistrements valides: %d\n" +
                        "- Enregistrements invalides: %d\n" +
                        "- Taux de validité: %.2f%%",
                totalRecords, validRecords, invalidRecords,
                totalRecords > 0 ? (validRecords * 100.0 / totalRecords) : 0.0);
    }
}