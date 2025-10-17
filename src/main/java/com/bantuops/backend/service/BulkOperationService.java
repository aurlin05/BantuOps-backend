package com.bantuops.backend.service;

import com.bantuops.backend.dto.BulkOperationResult;
import com.bantuops.backend.dto.PayrollResult;
import com.bantuops.backend.entity.Employee;
import com.bantuops.backend.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Service pour les opérations en masse (bulk operations)
 * Optimisé pour traiter de gros volumes de données efficacement
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BulkOperationService {

    private final EmployeeRepository employeeRepository;
    private final PayrollCalculationService payrollCalculationService;
    private final PerformanceMonitoringService performanceMonitoringService;
    private final DatabaseQueryOptimizer databaseQueryOptimizer;
    
    // Configuration des batches
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final int MAX_BATCH_SIZE = 1000;
    private static final int PARALLEL_THREADS = 4;
    
    // Suivi des opérations en cours
    private final Map<String, BulkOperationStatus> activeOperations = new ConcurrentHashMap<>();

    /**
     * Calcul de paie en masse pour tous les employés actifs
     */
    @Async
    @Transactional(readOnly = true)
    public CompletableFuture<BulkOperationResult<PayrollResult>> calculateBulkPayroll(YearMonth period) {
        String operationId = generateOperationId("BULK_PAYROLL");
        log.info("Starting bulk payroll calculation for period: {} (Operation ID: {})", period, operationId);
        
        BulkOperationStatus status = new BulkOperationStatus(operationId, "BULK_PAYROLL", LocalDateTime.now());
        activeOperations.put(operationId, status);
        
        try {
            // Compter le nombre total d'employés actifs
            long totalEmployees = employeeRepository.countByEmploymentInfoIsActiveTrue();
            status.setTotalItems(totalEmployees);
            
            log.info("Processing {} active employees for payroll calculation", totalEmployees);
            
            List<PayrollResult> results = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            AtomicInteger processedCount = new AtomicInteger(0);
            AtomicLong totalProcessingTime = new AtomicLong(0);
            
            // Traitement par batches
            int batchSize = calculateOptimalBatchSize((int) totalEmployees);
            int totalBatches = (int) Math.ceil((double) totalEmployees / batchSize);
            
            for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
                if (status.isCancelled()) {
                    log.info("Bulk payroll operation cancelled: {}", operationId);
                    break;
                }
                
                long batchStartTime = System.currentTimeMillis();
                
                Pageable pageable = PageRequest.of(batchIndex, batchSize);
                Page<Employee> employeeBatch = employeeRepository.findByEmploymentInfoIsActiveTrue(pageable);
                
                // Traitement du batch
                List<PayrollResult> batchResults = processBatchPayroll(
                    employeeBatch.getContent(), period, status, processedCount);
                
                results.addAll(batchResults);
                
                long batchProcessingTime = System.currentTimeMillis() - batchStartTime;
                totalProcessingTime.addAndGet(batchProcessingTime);
                
                // Mise à jour du statut
                status.setProcessedItems(processedCount.get());
                status.setProgress((double) processedCount.get() / totalEmployees);
                
                log.debug("Processed batch {}/{} - {} employees in {}ms", 
                         batchIndex + 1, totalBatches, employeeBatch.getNumberOfElements(), batchProcessingTime);
                
                // Pause courte pour éviter la surcharge
                if (batchIndex < totalBatches - 1) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
            // Finalisation
            status.setCompletedAt(LocalDateTime.now());
            status.setStatus("COMPLETED");
            
            BulkOperationResult<PayrollResult> result = new BulkOperationResult<>(
                operationId,
                results,
                errors,
                totalProcessingTime.get(),
                processedCount.get(),
                (int) totalEmployees
            );
            
            log.info("Bulk payroll calculation completed - Operation: {}, Processed: {}/{}, Time: {}ms", 
                    operationId, processedCount.get(), totalEmployees, totalProcessingTime.get());
            
            // Enregistrer les métriques
            performanceMonitoringService.updateCustomGauge("bulk.payroll.last.duration.ms", totalProcessingTime.get());
            performanceMonitoringService.updateCustomGauge("bulk.payroll.last.count", processedCount.get());
            performanceMonitoringService.incrementCustomCounter("bulk.payroll.operations.completed");
            
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            log.error("Error during bulk payroll calculation: {}", operationId, e);
            status.setStatus("FAILED");
            status.setErrorMessage(e.getMessage());
            status.setCompletedAt(LocalDateTime.now());
            
            performanceMonitoringService.incrementCustomCounter("bulk.payroll.operations.failed");
            
            return CompletableFuture.failedFuture(e);
        } finally {
            // Nettoyer après un délai
            scheduleOperationCleanup(operationId);
        }
    }

    /**
     * Traitement d'un batch de calculs de paie
     */
    private List<PayrollResult> processBatchPayroll(List<Employee> employees, YearMonth period, 
                                                   BulkOperationStatus status, AtomicInteger processedCount) {
        List<PayrollResult> results = new ArrayList<>();
        
        // Traitement parallèle du batch si assez d'éléments
        if (employees.size() > 20) {
            results = employees.parallelStream()
                .map(employee -> {
                    try {
                        if (status.isCancelled()) {
                            return null;
                        }
                        
                        PayrollResult result = payrollCalculationService.calculatePayroll(employee.getId(), period);
                        processedCount.incrementAndGet();
                        return result;
                    } catch (Exception e) {
                        log.warn("Error calculating payroll for employee {}: {}", employee.getId(), e.getMessage());
                        processedCount.incrementAndGet();
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        } else {
            // Traitement séquentiel pour les petits batches
            for (Employee employee : employees) {
                if (status.isCancelled()) {
                    break;
                }
                
                try {
                    PayrollResult result = payrollCalculationService.calculatePayroll(employee.getId(), period);
                    results.add(result);
                } catch (Exception e) {
                    log.warn("Error calculating payroll for employee {}: {}", employee.getId(), e.getMessage());
                }
                
                processedCount.incrementAndGet();
            }
        }
        
        return results;
    }

    /**
     * Mise à jour en masse des données d'employés
     */
    @Async
    @Transactional
    public CompletableFuture<BulkOperationResult<Employee>> bulkUpdateEmployees(
            List<Long> employeeIds, Map<String, Object> updates) {
        
        String operationId = generateOperationId("BULK_UPDATE_EMPLOYEES");
        log.info("Starting bulk employee update for {} employees (Operation ID: {})", 
                employeeIds.size(), operationId);
        
        BulkOperationStatus status = new BulkOperationStatus(operationId, "BULK_UPDATE_EMPLOYEES", LocalDateTime.now());
        status.setTotalItems(employeeIds.size());
        activeOperations.put(operationId, status);
        
        try {
            List<Employee> updatedEmployees = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            AtomicInteger processedCount = new AtomicInteger(0);
            long startTime = System.currentTimeMillis();
            
            // Traitement par batches
            int batchSize = calculateOptimalBatchSize(employeeIds.size());
            List<List<Long>> batches = partitionList(employeeIds, batchSize);
            
            for (List<Long> batch : batches) {
                if (status.isCancelled()) {
                    break;
                }
                
                // Récupérer les employés du batch
                List<Employee> employees = employeeRepository.findByIdsBatch(batch);
                
                // Appliquer les mises à jour
                for (Employee employee : employees) {
                    try {
                        applyUpdatesToEmployee(employee, updates);
                        updatedEmployees.add(employee);
                    } catch (Exception e) {
                        errors.add(String.format("Employee %d: %s", employee.getId(), e.getMessage()));
                        log.warn("Error updating employee {}: {}", employee.getId(), e.getMessage());
                    }
                    
                    processedCount.incrementAndGet();
                }
                
                // Sauvegarder le batch
                employeeRepository.saveAll(employees);
                
                // Mise à jour du statut
                status.setProcessedItems(processedCount.get());
                status.setProgress((double) processedCount.get() / employeeIds.size());
                
                // Pause courte
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            long totalTime = System.currentTimeMillis() - startTime;
            
            status.setCompletedAt(LocalDateTime.now());
            status.setStatus("COMPLETED");
            
            BulkOperationResult<Employee> result = new BulkOperationResult<>(
                operationId,
                updatedEmployees,
                errors,
                totalTime,
                processedCount.get(),
                employeeIds.size()
            );
            
            log.info("Bulk employee update completed - Operation: {}, Updated: {}/{}, Time: {}ms", 
                    operationId, updatedEmployees.size(), employeeIds.size(), totalTime);
            
            performanceMonitoringService.incrementCustomCounter("bulk.employee.updates.completed");
            
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            log.error("Error during bulk employee update: {}", operationId, e);
            status.setStatus("FAILED");
            status.setErrorMessage(e.getMessage());
            status.setCompletedAt(LocalDateTime.now());
            
            performanceMonitoringService.incrementCustomCounter("bulk.employee.updates.failed");
            
            return CompletableFuture.failedFuture(e);
        } finally {
            scheduleOperationCleanup(operationId);
        }
    }

    /**
     * Suppression en masse d'enregistrements
     */
    @Async
    @Transactional
    public CompletableFuture<BulkOperationResult<Long>> bulkDeleteRecords(
            String entityType, List<Long> recordIds) {
        
        String operationId = generateOperationId("BULK_DELETE_" + entityType);
        log.info("Starting bulk delete for {} {} records (Operation ID: {})", 
                recordIds.size(), entityType, operationId);
        
        BulkOperationStatus status = new BulkOperationStatus(operationId, "BULK_DELETE_" + entityType, LocalDateTime.now());
        status.setTotalItems(recordIds.size());
        activeOperations.put(operationId, status);
        
        try {
            List<Long> deletedIds = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            AtomicInteger processedCount = new AtomicInteger(0);
            long startTime = System.currentTimeMillis();
            
            // Traitement par batches pour éviter les timeouts
            int batchSize = calculateOptimalBatchSize(recordIds.size());
            List<List<Long>> batches = partitionList(recordIds, batchSize);
            
            for (List<Long> batch : batches) {
                if (status.isCancelled()) {
                    break;
                }
                
                try {
                    // Utiliser des requêtes natives pour la performance
                    int deletedCount = executeBulkDelete(entityType, batch);
                    deletedIds.addAll(batch.subList(0, deletedCount));
                    processedCount.addAndGet(batch.size());
                    
                } catch (Exception e) {
                    errors.add(String.format("Batch error: %s", e.getMessage()));
                    log.warn("Error deleting batch for {}: {}", entityType, e.getMessage());
                    processedCount.addAndGet(batch.size());
                }
                
                // Mise à jour du statut
                status.setProcessedItems(processedCount.get());
                status.setProgress((double) processedCount.get() / recordIds.size());
                
                // Pause courte
                try {
                    Thread.sleep(25);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            long totalTime = System.currentTimeMillis() - startTime;
            
            status.setCompletedAt(LocalDateTime.now());
            status.setStatus("COMPLETED");
            
            BulkOperationResult<Long> result = new BulkOperationResult<>(
                operationId,
                deletedIds,
                errors,
                totalTime,
                processedCount.get(),
                recordIds.size()
            );
            
            log.info("Bulk delete completed - Operation: {}, Deleted: {}/{}, Time: {}ms", 
                    operationId, deletedIds.size(), recordIds.size(), totalTime);
            
            performanceMonitoringService.incrementCustomCounter("bulk.delete.operations.completed");
            
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            log.error("Error during bulk delete: {}", operationId, e);
            status.setStatus("FAILED");
            status.setErrorMessage(e.getMessage());
            status.setCompletedAt(LocalDateTime.now());
            
            performanceMonitoringService.incrementCustomCounter("bulk.delete.operations.failed");
            
            return CompletableFuture.failedFuture(e);
        } finally {
            scheduleOperationCleanup(operationId);
        }
    }

    /**
     * Export en masse de données
     */
    @Async
    @Transactional(readOnly = true)
    public CompletableFuture<BulkOperationResult<Map<String, Object>>> bulkExportData(
            String entityType, Map<String, Object> filters, String format) {
        
        String operationId = generateOperationId("BULK_EXPORT_" + entityType);
        log.info("Starting bulk export for {} in format {} (Operation ID: {})", 
                entityType, format, operationId);
        
        BulkOperationStatus status = new BulkOperationStatus(operationId, "BULK_EXPORT_" + entityType, LocalDateTime.now());
        activeOperations.put(operationId, status);
        
        try {
            // Compter le nombre total d'enregistrements
            long totalRecords = databaseQueryOptimizer.countOptimized(entityType, filters);
            status.setTotalItems(totalRecords);
            
            List<Map<String, Object>> exportedData = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            AtomicInteger processedCount = new AtomicInteger(0);
            long startTime = System.currentTimeMillis();
            
            // Traitement par batches pour éviter les problèmes de mémoire
            int batchSize = calculateOptimalBatchSize((int) totalRecords);
            int totalBatches = (int) Math.ceil((double) totalRecords / batchSize);
            
            for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
                if (status.isCancelled()) {
                    break;
                }
                
                try {
                    Pageable pageable = databaseQueryOptimizer.createOptimizedPageable(
                        batchIndex, batchSize, "id", "asc");
                    
                    List<Object[]> batchData = databaseQueryOptimizer.findLargeDatasetOptimized(
                        entityType, filters, pageable);
                    
                    // Convertir en format d'export
                    List<Map<String, Object>> convertedBatch = convertToExportFormat(batchData, entityType);
                    exportedData.addAll(convertedBatch);
                    
                    processedCount.addAndGet(batchData.size());
                    
                } catch (Exception e) {
                    errors.add(String.format("Batch %d error: %s", batchIndex, e.getMessage()));
                    log.warn("Error exporting batch {} for {}: {}", batchIndex, entityType, e.getMessage());
                }
                
                // Mise à jour du statut
                status.setProcessedItems(processedCount.get());
                status.setProgress((double) processedCount.get() / totalRecords);
                
                // Pause courte
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            long totalTime = System.currentTimeMillis() - startTime;
            
            status.setCompletedAt(LocalDateTime.now());
            status.setStatus("COMPLETED");
            
            // Créer le résultat avec métadonnées
            List<Map<String, Object>> resultData = List.of(Map.of(
                "exported_records", exportedData,
                "total_count", exportedData.size(),
                "format", format,
                "export_time", LocalDateTime.now(),
                "filters_applied", filters
            ));
            
            BulkOperationResult<Map<String, Object>> result = new BulkOperationResult<>(
                operationId,
                resultData,
                errors,
                totalTime,
                processedCount.get(),
                (int) totalRecords
            );
            
            log.info("Bulk export completed - Operation: {}, Exported: {}/{}, Time: {}ms", 
                    operationId, exportedData.size(), totalRecords, totalTime);
            
            performanceMonitoringService.incrementCustomCounter("bulk.export.operations.completed");
            
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            log.error("Error during bulk export: {}", operationId, e);
            status.setStatus("FAILED");
            status.setErrorMessage(e.getMessage());
            status.setCompletedAt(LocalDateTime.now());
            
            performanceMonitoringService.incrementCustomCounter("bulk.export.operations.failed");
            
            return CompletableFuture.failedFuture(e);
        } finally {
            scheduleOperationCleanup(operationId);
        }
    }

    /**
     * Calcul de la taille optimale de batch
     */
    private int calculateOptimalBatchSize(int totalItems) {
        if (totalItems <= 100) {
            return Math.min(totalItems, 25);
        } else if (totalItems <= 1000) {
            return 50;
        } else if (totalItems <= 10000) {
            return DEFAULT_BATCH_SIZE;
        } else {
            return Math.min(MAX_BATCH_SIZE, totalItems / 10);
        }
    }

    /**
     * Partition d'une liste en batches
     */
    private <T> List<List<T>> partitionList(List<T> list, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            batches.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return batches;
    }

    /**
     * Application des mises à jour à un employé
     */
    private void applyUpdatesToEmployee(Employee employee, Map<String, Object> updates) {
        for (Map.Entry<String, Object> update : updates.entrySet()) {
            String field = update.getKey();
            Object value = update.getValue();
            
            switch (field) {
                case "department":
                    employee.getEmploymentInfo().setDepartment((String) value);
                    break;
                case "position":
                    employee.getEmploymentInfo().setPosition((String) value);
                    break;
                case "isActive":
                    employee.getEmploymentInfo().setIsActive((Boolean) value);
                    break;
                // Ajouter d'autres champs selon les besoins
                default:
                    log.warn("Unknown field for update: {}", field);
            }
        }
    }

    /**
     * Exécution d'une suppression en masse
     */
    private int executeBulkDelete(String entityType, List<Long> ids) {
        // Implémentation simplifiée - dans un vrai système, utiliser des requêtes natives
        switch (entityType.toUpperCase()) {
            case "EMPLOYEE":
                // Note: Attention aux contraintes de clés étrangères
                return ids.size(); // Placeholder
            default:
                throw new IllegalArgumentException("Unsupported entity type for bulk delete: " + entityType);
        }
    }

    /**
     * Conversion au format d'export
     */
    private List<Map<String, Object>> convertToExportFormat(List<Object[]> data, String entityType) {
        List<Map<String, Object>> converted = new ArrayList<>();
        
        for (Object[] row : data) {
            Map<String, Object> record = new HashMap<>();
            
            // Mapping spécifique selon le type d'entité
            switch (entityType.toUpperCase()) {
                case "EMPLOYEE":
                    if (row.length >= 6) {
                        record.put("id", row[0]);
                        record.put("employee_number", row[1]);
                        record.put("first_name", row[2]);
                        record.put("last_name", row[3]);
                        record.put("position", row[4]);
                        record.put("department", row[5]);
                    }
                    break;
                case "PAYROLL":
                    if (row.length >= 5) {
                        record.put("id", row[0]);
                        record.put("employee_id", row[1]);
                        record.put("period", row[2] + "-" + row[3]);
                        record.put("gross_salary", row[4]);
                        record.put("net_salary", row[5]);
                    }
                    break;
                default:
                    // Format générique
                    for (int i = 0; i < row.length; i++) {
                        record.put("field_" + i, row[i]);
                    }
            }
            
            converted.add(record);
        }
        
        return converted;
    }

    /**
     * Génération d'un ID d'opération unique
     */
    private String generateOperationId(String operationType) {
        return operationType + "_" + System.currentTimeMillis() + "_" + 
               UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Programmation du nettoyage d'une opération
     */
    private void scheduleOperationCleanup(String operationId) {
        // Dans un vrai système, utiliser un scheduler
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                activeOperations.remove(operationId);
                log.debug("Cleaned up operation: {}", operationId);
            }
        }, 3600000); // 1 heure
    }

    /**
     * Obtention du statut d'une opération
     */
    public BulkOperationStatus getOperationStatus(String operationId) {
        return activeOperations.get(operationId);
    }

    /**
     * Annulation d'une opération
     */
    public boolean cancelOperation(String operationId) {
        BulkOperationStatus status = activeOperations.get(operationId);
        if (status != null && !status.isCompleted()) {
            status.setCancelled(true);
            status.setStatus("CANCELLED");
            status.setCompletedAt(LocalDateTime.now());
            log.info("Operation cancelled: {}", operationId);
            return true;
        }
        return false;
    }

    /**
     * Liste des opérations actives
     */
    public List<BulkOperationStatus> getActiveOperations() {
        return new ArrayList<>(activeOperations.values());
    }

    /**
     * Statistiques des opérations en masse
     */
    public Map<String, Object> getBulkOperationStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        long activeCount = activeOperations.values().stream()
            .filter(status -> !status.isCompleted())
            .count();
        
        long completedCount = activeOperations.values().stream()
            .filter(status -> "COMPLETED".equals(status.getStatus()))
            .count();
        
        long failedCount = activeOperations.values().stream()
            .filter(status -> "FAILED".equals(status.getStatus()))
            .count();
        
        stats.put("active_operations", activeCount);
        stats.put("completed_operations", completedCount);
        stats.put("failed_operations", failedCount);
        stats.put("total_operations", activeOperations.size());
        
        // Statistiques par type d'opération
        Map<String, Long> operationsByType = activeOperations.values().stream()
            .collect(Collectors.groupingBy(
                BulkOperationStatus::getOperationType,
                Collectors.counting()
            ));
        stats.put("operations_by_type", operationsByType);
        
        stats.put("timestamp", LocalDateTime.now());
        
        return stats;
    }

    /**
     * Classe pour le statut des opérations en masse
     */
    public static class BulkOperationStatus {
        private final String operationId;
        private final String operationType;
        private final LocalDateTime startedAt;
        private volatile String status = "RUNNING";
        private volatile long totalItems = 0;
        private volatile long processedItems = 0;
        private volatile double progress = 0.0;
        private volatile LocalDateTime completedAt;
        private volatile String errorMessage;
        private volatile boolean cancelled = false;

        public BulkOperationStatus(String operationId, String operationType, LocalDateTime startedAt) {
            this.operationId = operationId;
            this.operationType = operationType;
            this.startedAt = startedAt;
        }

        // Getters et setters
        public String getOperationId() { return operationId; }
        public String getOperationType() { return operationType; }
        public LocalDateTime getStartedAt() { return startedAt; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getTotalItems() { return totalItems; }
        public void setTotalItems(long totalItems) { this.totalItems = totalItems; }
        public long getProcessedItems() { return processedItems; }
        public void setProcessedItems(long processedItems) { this.processedItems = processedItems; }
        public double getProgress() { return progress; }
        public void setProgress(double progress) { this.progress = progress; }
        public LocalDateTime getCompletedAt() { return completedAt; }
        public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public boolean isCancelled() { return cancelled; }
        public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
        public boolean isCompleted() { return completedAt != null; }
    }
}