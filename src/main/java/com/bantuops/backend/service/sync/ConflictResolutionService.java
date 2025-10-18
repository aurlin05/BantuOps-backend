package com.bantuops.backend.service.sync;

import com.bantuops.backend.dto.sync.ConflictResolution;
import com.bantuops.backend.entity.Employee;
import com.bantuops.backend.entity.Invoice;
import com.bantuops.backend.entity.PayrollRecord;
import com.bantuops.backend.repository.EmployeeRepository;
import com.bantuops.backend.repository.InvoiceRepository;
import com.bantuops.backend.repository.PayrollRecordRepository;
import com.bantuops.backend.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service de résolution des conflits de synchronisation.
 * Gère les stratégies de résolution et applique les corrections.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConflictResolutionService {

    private final EmployeeRepository employeeRepository;
    private final InvoiceRepository invoiceRepository;
    private final PayrollRecordRepository payrollRecordRepository;
    private final AuditService auditService;
    
    /**
     * Résout les conflits pour une liste d'entités
     */
    @Transactional
    public List<ConflictResolution> resolveConflicts(String entityType, List<Long> entityIds) {
        log.info("Résolution des conflits pour l'entité {} avec {} IDs", entityType, 
                entityIds != null ? entityIds.size() : 0);
        
        List<ConflictResolution> resolutions = new ArrayList<>();
        
        if (entityIds == null || entityIds.isEmpty()) {
            log.warn("Aucun ID d'entité fourni pour la résolution des conflits");
            return resolutions;
        }
        
        switch (entityType.toLowerCase()) {
            case "employee":
                resolutions.addAll(resolveEmployeeConflicts(entityIds));
                break;
            case "invoice":
                resolutions.addAll(resolveInvoiceConflicts(entityIds));
                break;
            case "payroll":
                resolutions.addAll(resolvePayrollConflicts(entityIds));
                break;
            default:
                log.warn("Type d'entité non supporté pour la résolution de conflits: {}", entityType);
        }
        
        // Audit des résolutions
        for (ConflictResolution resolution : resolutions) {
            auditService.logConflictResolution(resolution.getConflictId(), resolution.getEntityType(),
                resolution.getEntityId(), resolution.getStrategy(), resolution.getResolvedBy());
        }
        
        log.info("Résolution terminée. {} conflits résolus", resolutions.size());
        return resolutions;
    }
    
    /**
     * Résout un conflit spécifique avec une stratégie donnée
     */
    @Transactional
    public ConflictResolution resolveConflict(String entityType, Long entityId, 
                                            ConflictResolution.ResolutionStrategy strategy,
                                            Map<String, Object> frontendData,
                                            Map<String, Object> backendData) {
        log.info("Résolution du conflit pour {} ID {} avec stratégie {}", entityType, entityId, strategy);
        
        String conflictId = UUID.randomUUID().toString();
        String resolvedBy = getCurrentUserId();
        
        Map<String, Object> resolvedData = applyResolutionStrategy(strategy, frontendData, backendData);
        
        // Appliquer la résolution à l'entité
        boolean applied = applyResolutionToEntity(entityType, entityId, resolvedData);
        
        ConflictResolution resolution = ConflictResolution.builder()
            .conflictId(conflictId)
            .entityType(entityType)
            .entityId(entityId)
            .strategy(strategy)
            .resolvedData(resolvedData)
            .resolvedBy(resolvedBy)
            .resolvedAt(LocalDateTime.now())
            .reason("Résolution automatique de conflit")
            .originalFrontendData(frontendData)
            .originalBackendData(backendData)
            .build();
        
        if (applied) {
            auditService.logConflictResolution(conflictId, entityType, entityId, strategy, resolvedBy);
            log.info("Conflit {} résolu avec succès", conflictId);
        } else {
            log.error("Échec de l'application de la résolution pour le conflit {}", conflictId);
        }
        
        return resolution;
    }
    
    /**
     * Résout les conflits d'employés
     */
    private List<ConflictResolution> resolveEmployeeConflicts(List<Long> employeeIds) {
        List<ConflictResolution> resolutions = new ArrayList<>();
        
        for (Long employeeId : employeeIds) {
            Optional<Employee> employeeOpt = employeeRepository.findById(employeeId);
            if (employeeOpt.isPresent()) {
                Employee employee = employeeOpt.get();
                
                // Détecter les conflits potentiels et les résoudre
                ConflictResolution resolution = resolveEmployeeConflict(employee);
                if (resolution != null) {
                    resolutions.add(resolution);
                }
            }
        }
        
        return resolutions;
    }
    
    /**
     * Résout les conflits de factures
     */
    private List<ConflictResolution> resolveInvoiceConflicts(List<Long> invoiceIds) {
        List<ConflictResolution> resolutions = new ArrayList<>();
        
        for (Long invoiceId : invoiceIds) {
            Optional<Invoice> invoiceOpt = invoiceRepository.findById(invoiceId);
            if (invoiceOpt.isPresent()) {
                Invoice invoice = invoiceOpt.get();
                
                ConflictResolution resolution = resolveInvoiceConflict(invoice);
                if (resolution != null) {
                    resolutions.add(resolution);
                }
            }
        }
        
        return resolutions;
    }
    
    /**
     * Résout les conflits de paie
     */
    private List<ConflictResolution> resolvePayrollConflicts(List<Long> payrollIds) {
        List<ConflictResolution> resolutions = new ArrayList<>();
        
        for (Long payrollId : payrollIds) {
            Optional<PayrollRecord> recordOpt = payrollRecordRepository.findById(payrollId);
            if (recordOpt.isPresent()) {
                PayrollRecord record = recordOpt.get();
                
                ConflictResolution resolution = resolvePayrollConflict(record);
                if (resolution != null) {
                    resolutions.add(resolution);
                }
            }
        }
        
        return resolutions;
    }
    
    /**
     * Résout un conflit d'employé spécifique
     */
    private ConflictResolution resolveEmployeeConflict(Employee employee) {
        // Exemple de résolution : vérifier les données incohérentes
        Map<String, Object> backendData = convertEmployeeToMap(employee);
        Map<String, Object> frontendData = new HashMap<>(); // Simulated frontend data
        
        // Détecter les conflits (exemple simplifié)
        boolean hasConflict = detectEmployeeConflict(employee);
        
        if (hasConflict) {
            return resolveConflict("employee", employee.getId(), 
                ConflictResolution.ResolutionStrategy.BACKEND_WINS,
                frontendData, backendData);
        }
        
        return null;
    }
    
    /**
     * Résout un conflit de facture spécifique
     */
    private ConflictResolution resolveInvoiceConflict(Invoice invoice) {
        Map<String, Object> backendData = convertInvoiceToMap(invoice);
        Map<String, Object> frontendData = new HashMap<>();
        
        boolean hasConflict = detectInvoiceConflict(invoice);
        
        if (hasConflict) {
            return resolveConflict("invoice", invoice.getId(),
                ConflictResolution.ResolutionStrategy.LATEST_TIMESTAMP_WINS,
                frontendData, backendData);
        }
        
        return null;
    }
    
    /**
     * Résout un conflit de paie spécifique
     */
    private ConflictResolution resolvePayrollConflict(PayrollRecord record) {
        Map<String, Object> backendData = convertPayrollToMap(record);
        Map<String, Object> frontendData = new HashMap<>();
        
        boolean hasConflict = detectPayrollConflict(record);
        
        if (hasConflict) {
            return resolveConflict("payroll", record.getId(),
                ConflictResolution.ResolutionStrategy.BACKEND_WINS,
                frontendData, backendData);
        }
        
        return null;
    }
    
    /**
     * Applique une stratégie de résolution
     */
    private Map<String, Object> applyResolutionStrategy(ConflictResolution.ResolutionStrategy strategy,
                                                       Map<String, Object> frontendData,
                                                       Map<String, Object> backendData) {
        switch (strategy) {
            case FRONTEND_WINS:
                return new HashMap<>(frontendData);
            
            case BACKEND_WINS:
                return new HashMap<>(backendData);
            
            case MERGE_DATA:
                return mergeData(frontendData, backendData);
            
            case LATEST_TIMESTAMP_WINS:
                return resolveByTimestamp(frontendData, backendData);
            
            case CUSTOM_RULE:
                return applyCustomRules(frontendData, backendData);
            
            case MANUAL_RESOLUTION:
            default:
                // Pour la résolution manuelle, on privilégie le backend par défaut
                return new HashMap<>(backendData);
        }
    }
    
    /**
     * Fusionne les données frontend et backend
     */
    private Map<String, Object> mergeData(Map<String, Object> frontendData, Map<String, Object> backendData) {
        Map<String, Object> merged = new HashMap<>(backendData);
        
        // Fusionner en privilégiant les données non nulles du frontend
        for (Map.Entry<String, Object> entry : frontendData.entrySet()) {
            if (entry.getValue() != null) {
                merged.put(entry.getKey(), entry.getValue());
            }
        }
        
        return merged;
    }
    
    /**
     * Résout par timestamp (le plus récent gagne)
     */
    private Map<String, Object> resolveByTimestamp(Map<String, Object> frontendData, Map<String, Object> backendData) {
        LocalDateTime frontendTimestamp = extractTimestamp(frontendData);
        LocalDateTime backendTimestamp = extractTimestamp(backendData);
        
        if (frontendTimestamp != null && backendTimestamp != null) {
            return frontendTimestamp.isAfter(backendTimestamp) ? frontendData : backendData;
        }
        
        // Par défaut, privilégier le backend
        return backendData;
    }
    
    /**
     * Applique des règles personnalisées
     */
    private Map<String, Object> applyCustomRules(Map<String, Object> frontendData, Map<String, Object> backendData) {
        Map<String, Object> resolved = new HashMap<>(backendData);
        
        // Règles personnalisées pour différents champs
        // Exemple : pour les montants, privilégier le backend (plus sécurisé)
        if (frontendData.containsKey("totalAmount") && backendData.containsKey("totalAmount")) {
            resolved.put("totalAmount", backendData.get("totalAmount"));
        }
        
        // Pour les informations non critiques, privilégier le frontend (plus récent)
        if (frontendData.containsKey("description") && frontendData.get("description") != null) {
            resolved.put("description", frontendData.get("description"));
        }
        
        return resolved;
    }
    
    /**
     * Applique la résolution à l'entité
     */
    private boolean applyResolutionToEntity(String entityType, Long entityId, Map<String, Object> resolvedData) {
        try {
            switch (entityType.toLowerCase()) {
                case "employee":
                    return applyEmployeeResolution(entityId, resolvedData);
                case "invoice":
                    return applyInvoiceResolution(entityId, resolvedData);
                case "payroll":
                    return applyPayrollResolution(entityId, resolvedData);
                default:
                    log.warn("Type d'entité non supporté pour l'application de résolution: {}", entityType);
                    return false;
            }
        } catch (Exception e) {
            log.error("Erreur lors de l'application de la résolution pour {} ID {}: {}", 
                    entityType, entityId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Applique la résolution à un employé
     */
    private boolean applyEmployeeResolution(Long employeeId, Map<String, Object> resolvedData) {
        Optional<Employee> employeeOpt = employeeRepository.findById(employeeId);
        if (employeeOpt.isPresent()) {
            Employee employee = employeeOpt.get();
            updateEmployeeFromMap(employee, resolvedData);
            employeeRepository.save(employee);
            return true;
        }
        return false;
    }
    
    /**
     * Applique la résolution à une facture
     */
    private boolean applyInvoiceResolution(Long invoiceId, Map<String, Object> resolvedData) {
        Optional<Invoice> invoiceOpt = invoiceRepository.findById(invoiceId);
        if (invoiceOpt.isPresent()) {
            Invoice invoice = invoiceOpt.get();
            updateInvoiceFromMap(invoice, resolvedData);
            invoiceRepository.save(invoice);
            return true;
        }
        return false;
    }
    
    /**
     * Applique la résolution à un enregistrement de paie
     */
    private boolean applyPayrollResolution(Long payrollId, Map<String, Object> resolvedData) {
        Optional<PayrollRecord> recordOpt = payrollRecordRepository.findById(payrollId);
        if (recordOpt.isPresent()) {
            PayrollRecord record = recordOpt.get();
            updatePayrollFromMap(record, resolvedData);
            payrollRecordRepository.save(record);
            return true;
        }
        return false;
    }
    
    /**
     * Détecte les conflits d'employé
     */
    private boolean detectEmployeeConflict(Employee employee) {
        // Logique de détection de conflit simplifiée
        return employee.getPersonalInfo() != null && 
               (employee.getPersonalInfo().getEmail() == null || 
                employee.getPersonalInfo().getEmail().isEmpty());
    }
    
    /**
     * Détecte les conflits de facture
     */
    private boolean detectInvoiceConflict(Invoice invoice) {
        return invoice.getTotalAmount() == null || 
               invoice.getTotalAmount().compareTo(java.math.BigDecimal.ZERO) <= 0;
    }
    
    /**
     * Détecte les conflits de paie
     */
    private boolean detectPayrollConflict(PayrollRecord record) {
        return record.getGrossSalary() == null || 
               record.getNetSalary() == null ||
               record.getNetSalary().compareTo(record.getGrossSalary()) > 0;
    }
    
    /**
     * Convertit un employé en Map
     */
    private Map<String, Object> convertEmployeeToMap(Employee employee) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", employee.getId());
        data.put("employeeNumber", employee.getEmployeeNumber());
        data.put("personalInfo", employee.getPersonalInfo());
        data.put("employmentInfo", employee.getEmploymentInfo());
        data.put("updatedAt", employee.getUpdatedAt());
        return data;
    }
    
    /**
     * Convertit une facture en Map
     */
    private Map<String, Object> convertInvoiceToMap(Invoice invoice) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", invoice.getId());
        data.put("totalAmount", invoice.getTotalAmount());
        data.put("vatAmount", invoice.getVatAmount());
        data.put("status", invoice.getStatus());
        data.put("updatedAt", invoice.getUpdatedAt());
        return data;
    }
    
    /**
     * Convertit un enregistrement de paie en Map
     */
    private Map<String, Object> convertPayrollToMap(PayrollRecord record) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", record.getId());
        data.put("employeeId", record.getEmployee().getId());
        data.put("period", record.getPeriod());
        data.put("grossSalary", record.getGrossSalary());
        data.put("netSalary", record.getNetSalary());
        data.put("updatedAt", record.getUpdatedAt());
        return data;
    }
    
    /**
     * Met à jour un employé à partir d'une Map
     */
    private void updateEmployeeFromMap(Employee employee, Map<String, Object> data) {
        // Mise à jour simplifiée - à adapter selon les besoins
        if (data.containsKey("employeeNumber")) {
            employee.setEmployeeNumber((String) data.get("employeeNumber"));
        }
        // Ajouter d'autres champs selon les besoins
    }
    
    /**
     * Met à jour une facture à partir d'une Map
     */
    private void updateInvoiceFromMap(Invoice invoice, Map<String, Object> data) {
        if (data.containsKey("totalAmount")) {
            invoice.setTotalAmount((java.math.BigDecimal) data.get("totalAmount"));
        }
        if (data.containsKey("vatAmount")) {
            invoice.setVatAmount((java.math.BigDecimal) data.get("vatAmount"));
        }
    }
    
    /**
     * Met à jour un enregistrement de paie à partir d'une Map
     */
    private void updatePayrollFromMap(PayrollRecord record, Map<String, Object> data) {
        if (data.containsKey("grossSalary")) {
            record.setGrossSalary((java.math.BigDecimal) data.get("grossSalary"));
        }
        if (data.containsKey("netSalary")) {
            record.setNetSalary((java.math.BigDecimal) data.get("netSalary"));
        }
    }
    
    /**
     * Extrait le timestamp d'une Map de données
     */
    private LocalDateTime extractTimestamp(Map<String, Object> data) {
        Object timestamp = data.get("updatedAt");
        if (timestamp instanceof LocalDateTime) {
            return (LocalDateTime) timestamp;
        }
        return null;
    }
    
    /**
     * Obtient l'ID de l'utilisateur actuel
     */
    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }
}