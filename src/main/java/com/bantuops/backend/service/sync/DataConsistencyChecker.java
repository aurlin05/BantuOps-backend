package com.bantuops.backend.service.sync;

import com.bantuops.backend.dto.sync.DataConsistencyReport;
import com.bantuops.backend.entity.Employee;
import com.bantuops.backend.entity.Invoice;
import com.bantuops.backend.entity.PayrollRecord;
import com.bantuops.backend.repository.EmployeeRepository;
import com.bantuops.backend.repository.InvoiceRepository;
import com.bantuops.backend.repository.PayrollRecordRepository;
import com.bantuops.backend.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service de vérification de la cohérence des données entre frontend et backend.
 * Détecte les incohérences et génère des rapports de cohérence.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataConsistencyChecker {

    private final EmployeeRepository employeeRepository;
    private final InvoiceRepository invoiceRepository;
    private final PayrollRecordRepository payrollRecordRepository;
    private final AuditService auditService;
    
    /**
     * Vérifie la cohérence globale des données
     */
    @Transactional(readOnly = true)
    public DataConsistencyReport checkConsistency(String entityType, List<Long> entityIds) {
        log.info("Vérification de la cohérence pour l'entité {} avec {} IDs", entityType, 
                entityIds != null ? entityIds.size() : "tous");
        
        long startTime = System.currentTimeMillis();
        String reportId = UUID.randomUUID().toString();
        
        List<DataConsistencyReport.EntityConsistencyCheck> entityChecks = new ArrayList<>();
        List<DataConsistencyReport.DataInconsistency> inconsistencies = new ArrayList<>();
        
        switch (entityType.toLowerCase()) {
            case "employee":
                checkEmployeeConsistency(entityIds, entityChecks, inconsistencies);
                break;
            case "invoice":
                checkInvoiceConsistency(entityIds, entityChecks, inconsistencies);
                break;
            case "payroll":
                checkPayrollConsistency(entityIds, entityChecks, inconsistencies);
                break;
            case "all":
                checkEmployeeConsistency(null, entityChecks, inconsistencies);
                checkInvoiceConsistency(null, entityChecks, inconsistencies);
                checkPayrollConsistency(null, entityChecks, inconsistencies);
                break;
            default:
                log.warn("Type d'entité non supporté pour la vérification de cohérence: {}", entityType);
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        // Calculer les métriques
        int totalEntities = entityChecks.stream().mapToInt(DataConsistencyReport.EntityConsistencyCheck::getTotalEntities).sum();
        int totalInconsistencies = inconsistencies.size();
        double overallConsistency = totalEntities > 0 ? 
            ((double) (totalEntities - totalInconsistencies) / totalEntities) * 100 : 100.0;
        
        Map<String, Integer> inconsistenciesByType = inconsistencies.stream()
            .collect(Collectors.groupingBy(
                DataConsistencyReport.DataInconsistency::getInconsistencyType,
                Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
            ));
        
        Map<String, Integer> inconsistenciesBySeverity = inconsistencies.stream()
            .collect(Collectors.groupingBy(
                DataConsistencyReport.DataInconsistency::getSeverity,
                Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
            ));
        
        DataConsistencyReport.ConsistencyMetrics metrics = DataConsistencyReport.ConsistencyMetrics.builder()
            .overallConsistencyPercentage(overallConsistency)
            .totalEntitiesChecked(totalEntities)
            .totalInconsistencies(totalInconsistencies)
            .checkDurationMs(duration)
            .inconsistenciesByType(inconsistenciesByType)
            .inconsistenciesBySeverity(inconsistenciesBySeverity)
            .build();
        
        // Déterminer le statut global
        DataConsistencyReport.ConsistencyStatus status = determineConsistencyStatus(overallConsistency, inconsistencies);
        
        // Générer des recommandations
        Map<String, Object> recommendations = generateRecommendations(inconsistencies);
        
        DataConsistencyReport report = DataConsistencyReport.builder()
            .reportId(reportId)
            .generatedAt(LocalDateTime.now())
            .overallStatus(status)
            .entityChecks(entityChecks)
            .inconsistencies(inconsistencies)
            .metrics(metrics)
            .recommendations(recommendations)
            .build();
        
        // Audit du rapport de cohérence
        auditService.logConsistencyCheck(reportId, entityType, status, totalInconsistencies);
        
        log.info("Vérification de cohérence terminée. Rapport: {}, Statut: {}, Incohérences: {}", 
                reportId, status, totalInconsistencies);
        
        return report;
    }
    
    /**
     * Vérifie la cohérence d'une entité spécifique
     */
    @Transactional(readOnly = true)
    public DataConsistencyReport checkEntityConsistency(String entityType, Long entityId) {
        return checkConsistency(entityType, Collections.singletonList(entityId));
    }
    
    /**
     * Vérifie la cohérence des employés
     */
    private void checkEmployeeConsistency(List<Long> entityIds, 
                                         List<DataConsistencyReport.EntityConsistencyCheck> entityChecks,
                                         List<DataConsistencyReport.DataInconsistency> inconsistencies) {
        List<Employee> employees;
        
        if (entityIds != null && !entityIds.isEmpty()) {
            employees = employeeRepository.findAllById(entityIds);
        } else {
            employees = employeeRepository.findAll();
        }
        
        int totalEmployees = employees.size();
        int inconsistentEmployees = 0;
        
        for (Employee employee : employees) {
            List<DataConsistencyReport.DataInconsistency> employeeInconsistencies = 
                checkSingleEmployeeConsistency(employee);
            
            if (!employeeInconsistencies.isEmpty()) {
                inconsistentEmployees++;
                inconsistencies.addAll(employeeInconsistencies);
            }
        }
        
        double consistencyPercentage = totalEmployees > 0 ? 
            ((double) (totalEmployees - inconsistentEmployees) / totalEmployees) * 100 : 100.0;
        
        entityChecks.add(DataConsistencyReport.EntityConsistencyCheck.builder()
            .entityType("employee")
            .totalEntities(totalEmployees)
            .consistentEntities(totalEmployees - inconsistentEmployees)
            .inconsistentEntities(inconsistentEmployees)
            .consistencyPercentage(consistencyPercentage)
            .lastChecked(LocalDateTime.now())
            .build());
    }
    
    /**
     * Vérifie la cohérence des factures
     */
    private void checkInvoiceConsistency(List<Long> entityIds,
                                        List<DataConsistencyReport.EntityConsistencyCheck> entityChecks,
                                        List<DataConsistencyReport.DataInconsistency> inconsistencies) {
        List<Invoice> invoices;
        
        if (entityIds != null && !entityIds.isEmpty()) {
            invoices = invoiceRepository.findAllById(entityIds);
        } else {
            invoices = invoiceRepository.findAll();
        }
        
        int totalInvoices = invoices.size();
        int inconsistentInvoices = 0;
        
        for (Invoice invoice : invoices) {
            List<DataConsistencyReport.DataInconsistency> invoiceInconsistencies = 
                checkSingleInvoiceConsistency(invoice);
            
            if (!invoiceInconsistencies.isEmpty()) {
                inconsistentInvoices++;
                inconsistencies.addAll(invoiceInconsistencies);
            }
        }
        
        double consistencyPercentage = totalInvoices > 0 ? 
            ((double) (totalInvoices - inconsistentInvoices) / totalInvoices) * 100 : 100.0;
        
        entityChecks.add(DataConsistencyReport.EntityConsistencyCheck.builder()
            .entityType("invoice")
            .totalEntities(totalInvoices)
            .consistentEntities(totalInvoices - inconsistentInvoices)
            .inconsistentEntities(inconsistentInvoices)
            .consistencyPercentage(consistencyPercentage)
            .lastChecked(LocalDateTime.now())
            .build());
    }
    
    /**
     * Vérifie la cohérence des enregistrements de paie
     */
    private void checkPayrollConsistency(List<Long> entityIds,
                                        List<DataConsistencyReport.EntityConsistencyCheck> entityChecks,
                                        List<DataConsistencyReport.DataInconsistency> inconsistencies) {
        List<PayrollRecord> payrollRecords;
        
        if (entityIds != null && !entityIds.isEmpty()) {
            payrollRecords = payrollRecordRepository.findAllById(entityIds);
        } else {
            payrollRecords = payrollRecordRepository.findAll();
        }
        
        int totalRecords = payrollRecords.size();
        int inconsistentRecords = 0;
        
        for (PayrollRecord record : payrollRecords) {
            List<DataConsistencyReport.DataInconsistency> recordInconsistencies = 
                checkSinglePayrollConsistency(record);
            
            if (!recordInconsistencies.isEmpty()) {
                inconsistentRecords++;
                inconsistencies.addAll(recordInconsistencies);
            }
        }
        
        double consistencyPercentage = totalRecords > 0 ? 
            ((double) (totalRecords - inconsistentRecords) / totalRecords) * 100 : 100.0;
        
        entityChecks.add(DataConsistencyReport.EntityConsistencyCheck.builder()
            .entityType("payroll")
            .totalEntities(totalRecords)
            .consistentEntities(totalRecords - inconsistentRecords)
            .inconsistentEntities(inconsistentRecords)
            .consistencyPercentage(consistencyPercentage)
            .lastChecked(LocalDateTime.now())
            .build());
    }
    
    /**
     * Vérifie la cohérence d'un employé individuel
     */
    private List<DataConsistencyReport.DataInconsistency> checkSingleEmployeeConsistency(Employee employee) {
        List<DataConsistencyReport.DataInconsistency> inconsistencies = new ArrayList<>();
        
        // Vérifier les données personnelles
        if (employee.getPersonalInfo() != null) {
            if (employee.getPersonalInfo().getEmail() != null && 
                !isValidEmail(employee.getPersonalInfo().getEmail())) {
                inconsistencies.add(createInconsistency("employee", employee.getId(), "email",
                    employee.getPersonalInfo().getEmail(), null, "INVALID_FORMAT", "MEDIUM",
                    "Format d'email invalide"));
            }
            
            if (employee.getPersonalInfo().getPhoneNumber() != null && 
                !isValidSenegalPhoneNumber(employee.getPersonalInfo().getPhoneNumber())) {
                inconsistencies.add(createInconsistency("employee", employee.getId(), "phoneNumber",
                    employee.getPersonalInfo().getPhoneNumber(), null, "INVALID_FORMAT", "MEDIUM",
                    "Format de numéro de téléphone sénégalais invalide"));
            }
        }
        
        // Vérifier les données d'emploi
        if (employee.getEmploymentInfo() != null) {
            if (employee.getEmploymentInfo().getBaseSalary() != null && 
                employee.getEmploymentInfo().getBaseSalary().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                inconsistencies.add(createInconsistency("employee", employee.getId(), "baseSalary",
                    employee.getEmploymentInfo().getBaseSalary(), null, "INVALID_VALUE", "HIGH",
                    "Salaire de base doit être positif"));
            }
        }
        
        return inconsistencies;
    }
    
    /**
     * Vérifie la cohérence d'une facture individuelle
     */
    private List<DataConsistencyReport.DataInconsistency> checkSingleInvoiceConsistency(Invoice invoice) {
        List<DataConsistencyReport.DataInconsistency> inconsistencies = new ArrayList<>();
        
        // Vérifier les montants
        if (invoice.getTotalAmount() != null && 
            invoice.getTotalAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            inconsistencies.add(createInconsistency("invoice", invoice.getId(), "totalAmount",
                invoice.getTotalAmount(), null, "INVALID_VALUE", "HIGH",
                "Montant total doit être positif"));
        }
        
        if (invoice.getVatAmount() != null && 
            invoice.getVatAmount().compareTo(java.math.BigDecimal.ZERO) < 0) {
            inconsistencies.add(createInconsistency("invoice", invoice.getId(), "vatAmount",
                invoice.getVatAmount(), null, "INVALID_VALUE", "MEDIUM",
                "Montant TVA ne peut pas être négatif"));
        }
        
        // Vérifier la cohérence TVA
        if (invoice.getTotalAmount() != null && invoice.getVatAmount() != null) {
            java.math.BigDecimal expectedVat = invoice.getTotalAmount()
                .multiply(new java.math.BigDecimal("0.18")); // TVA sénégalaise 18%
            
            if (invoice.getVatAmount().subtract(expectedVat).abs()
                .compareTo(new java.math.BigDecimal("0.01")) > 0) {
                inconsistencies.add(createInconsistency("invoice", invoice.getId(), "vatCalculation",
                    invoice.getVatAmount(), expectedVat, "CALCULATION_ERROR", "HIGH",
                    "Calcul de TVA incorrect"));
            }
        }
        
        return inconsistencies;
    }
    
    /**
     * Vérifie la cohérence d'un enregistrement de paie individuel
     */
    private List<DataConsistencyReport.DataInconsistency> checkSinglePayrollConsistency(PayrollRecord record) {
        List<DataConsistencyReport.DataInconsistency> inconsistencies = new ArrayList<>();
        
        // Vérifier les montants
        if (record.getGrossSalary() != null && 
            record.getGrossSalary().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            inconsistencies.add(createInconsistency("payroll", record.getId(), "grossSalary",
                record.getGrossSalary(), null, "INVALID_VALUE", "HIGH",
                "Salaire brut doit être positif"));
        }
        
        if (record.getNetSalary() != null && 
            record.getNetSalary().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            inconsistencies.add(createInconsistency("payroll", record.getId(), "netSalary",
                record.getNetSalary(), null, "INVALID_VALUE", "HIGH",
                "Salaire net doit être positif"));
        }
        
        // Vérifier la cohérence brut/net
        if (record.getGrossSalary() != null && record.getNetSalary() != null &&
            record.getNetSalary().compareTo(record.getGrossSalary()) > 0) {
            inconsistencies.add(createInconsistency("payroll", record.getId(), "salaryConsistency",
                record.getNetSalary(), record.getGrossSalary(), "LOGIC_ERROR", "CRITICAL",
                "Salaire net ne peut pas être supérieur au salaire brut"));
        }
        
        return inconsistencies;
    }
    
    /**
     * Crée une incohérence de données
     */
    private DataConsistencyReport.DataInconsistency createInconsistency(String entityType, Long entityId,
                                                                       String field, Object frontendValue,
                                                                       Object backendValue, String type,
                                                                       String severity, String description) {
        return DataConsistencyReport.DataInconsistency.builder()
            .entityType(entityType)
            .entityId(entityId)
            .field(field)
            .frontendValue(frontendValue)
            .backendValue(backendValue)
            .inconsistencyType(type)
            .severity(severity)
            .detectedAt(LocalDateTime.now())
            .description(description)
            .build();
    }
    
    /**
     * Détermine le statut de cohérence global
     */
    private DataConsistencyReport.ConsistencyStatus determineConsistencyStatus(double consistencyPercentage,
                                                                              List<DataConsistencyReport.DataInconsistency> inconsistencies) {
        long criticalCount = inconsistencies.stream()
            .filter(i -> "CRITICAL".equals(i.getSeverity()))
            .count();
        
        long highCount = inconsistencies.stream()
            .filter(i -> "HIGH".equals(i.getSeverity()))
            .count();
        
        if (criticalCount > 0) {
            return DataConsistencyReport.ConsistencyStatus.CRITICAL_INCONSISTENCIES;
        } else if (highCount > 5 || consistencyPercentage < 80) {
            return DataConsistencyReport.ConsistencyStatus.MAJOR_INCONSISTENCIES;
        } else if (consistencyPercentage < 95) {
            return DataConsistencyReport.ConsistencyStatus.MINOR_INCONSISTENCIES;
        } else {
            return DataConsistencyReport.ConsistencyStatus.CONSISTENT;
        }
    }
    
    /**
     * Génère des recommandations basées sur les incohérences
     */
    private Map<String, Object> generateRecommendations(List<DataConsistencyReport.DataInconsistency> inconsistencies) {
        Map<String, Object> recommendations = new HashMap<>();
        
        Map<String, Long> typeCount = inconsistencies.stream()
            .collect(Collectors.groupingBy(
                DataConsistencyReport.DataInconsistency::getInconsistencyType,
                Collectors.counting()
            ));
        
        List<String> actions = new ArrayList<>();
        
        if (typeCount.getOrDefault("INVALID_FORMAT", 0L) > 0) {
            actions.add("Valider les formats de données lors de la saisie");
        }
        
        if (typeCount.getOrDefault("CALCULATION_ERROR", 0L) > 0) {
            actions.add("Vérifier les algorithmes de calcul");
        }
        
        if (typeCount.getOrDefault("LOGIC_ERROR", 0L) > 0) {
            actions.add("Revoir la logique métier");
        }
        
        recommendations.put("actions", actions);
        recommendations.put("priority", determinePriority(inconsistencies));
        recommendations.put("estimatedFixTime", estimateFixTime(inconsistencies));
        
        return recommendations;
    }
    
    /**
     * Détermine la priorité des corrections
     */
    private String determinePriority(List<DataConsistencyReport.DataInconsistency> inconsistencies) {
        long criticalCount = inconsistencies.stream()
            .filter(i -> "CRITICAL".equals(i.getSeverity()))
            .count();
        
        if (criticalCount > 0) {
            return "URGENT";
        } else if (inconsistencies.size() > 10) {
            return "HIGH";
        } else {
            return "MEDIUM";
        }
    }
    
    /**
     * Estime le temps de correction
     */
    private String estimateFixTime(List<DataConsistencyReport.DataInconsistency> inconsistencies) {
        int totalInconsistencies = inconsistencies.size();
        
        if (totalInconsistencies <= 5) {
            return "1-2 heures";
        } else if (totalInconsistencies <= 20) {
            return "4-8 heures";
        } else {
            return "1-2 jours";
        }
    }
    
    /**
     * Valide le format d'email
     */
    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }
    
    /**
     * Valide le format de numéro de téléphone sénégalais
     */
    private boolean isValidSenegalPhoneNumber(String phoneNumber) {
        return phoneNumber != null && phoneNumber.matches("^(\\+221|221)?[0-9]{9}$");
    }
    
    /**
     * Vérifie si le rapport a des incohérences
     */
    public boolean hasInconsistencies(DataConsistencyReport report) {
        return report != null && report.getInconsistencies() != null && !report.getInconsistencies().isEmpty();
    }
}