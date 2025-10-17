package com.bantuops.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service de traitement asynchrone pour les tâches longues
 * Gère les tâches en arrière-plan avec suivi et monitoring
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncProcessingService {

    private final PerformanceMonitoringService performanceMonitoringService;
    
    // Pool de threads configuré
    private final ThreadPoolTaskExecutor taskExecutor = createTaskExecutor();
    
    // Suivi des tâches asynchrones
    private final Map<String, AsyncTaskStatus> activeTasks = new ConcurrentHashMap<>();
    private final AtomicLong taskIdCounter = new AtomicLong(0);
    
    // Configuration
    private static final int CORE_POOL_SIZE = 5;
    private static final int MAX_POOL_SIZE = 20;
    private static final int QUEUE_CAPACITY = 100;
    private static final int KEEP_ALIVE_SECONDS = 60;

    /**
     * Configuration du pool de threads
     */
    private ThreadPoolTaskExecutor createTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(KEEP_ALIVE_SECONDS);
        executor.setThreadNamePrefix("BantuOps-Async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        
        log.info("Async task executor initialized - Core: {}, Max: {}, Queue: {}", 
                CORE_POOL_SIZE, MAX_POOL_SIZE, QUEUE_CAPACITY);
        
        return executor;
    }

    /**
     * Traitement asynchrone de calculs de paie en masse
     */
    @Async
    public CompletableFuture<String> processPayrollCalculationsAsync(List<Long> employeeIds, 
                                                                    String period, 
                                                                    Map<String, Object> options) {
        String taskId = generateTaskId("PAYROLL_CALC");
        AsyncTaskStatus taskStatus = new AsyncTaskStatus(taskId, "PAYROLL_CALCULATION", LocalDateTime.now());
        taskStatus.setTotalItems(employeeIds.size());
        activeTasks.put(taskId, taskStatus);
        
        log.info("Starting async payroll calculation - Task: {}, Employees: {}", taskId, employeeIds.size());
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                AtomicInteger processedCount = new AtomicInteger(0);
                AtomicInteger successCount = new AtomicInteger(0);
                AtomicInteger errorCount = new AtomicInteger(0);
                
                // Traitement parallèle avec contrôle de concurrence
                List<CompletableFuture<Boolean>> futures = new ArrayList<>();
                
                // Diviser en batches pour éviter la surcharge
                int batchSize = Math.min(50, Math.max(1, employeeIds.size() / 10));
                List<List<Long>> batches = partitionList(employeeIds, batchSize);
                
                for (List<Long> batch : batches) {
                    CompletableFuture<Boolean> batchFuture = CompletableFuture.supplyAsync(() -> {
                        try {
                            for (Long employeeId : batch) {
                                if (taskStatus.isCancelled()) {
                                    return false;
                                }
                                
                                try {
                                    // Simuler le calcul de paie
                                    Thread.sleep(100); // Simulation
                                    successCount.incrementAndGet();
                                    
                                } catch (Exception e) {
                                    log.warn("Error processing payroll for employee {}: {}", employeeId, e.getMessage());
                                    errorCount.incrementAndGet();
                                }
                                
                                int processed = processedCount.incrementAndGet();
                                taskStatus.setProcessedItems(processed);
                                taskStatus.setProgress((double) processed / employeeIds.size());
                                
                                // Mise à jour périodique des métriques
                                if (processed % 10 == 0) {
                                    performanceMonitoringService.updateCustomGauge(
                                        "async.payroll.progress." + taskId, taskStatus.getProgress());
                                }
                            }
                            return true;
                            
                        } catch (Exception e) {
                            log.error("Error processing payroll batch", e);
                            return false;
                        }
                    }, taskExecutor);
                    
                    futures.add(batchFuture);
                }
                
                // Attendre la completion de tous les batches
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                
                // Finaliser le statut
                taskStatus.setCompletedAt(LocalDateTime.now());
                taskStatus.setStatus("COMPLETED");
                taskStatus.setSuccessCount(successCount.get());
                taskStatus.setErrorCount(errorCount.get());
                
                String result = String.format("Payroll calculation completed - Success: %d, Errors: %d", 
                                             successCount.get(), errorCount.get());
                
                log.info("Async payroll calculation completed - Task: {}, Result: {}", taskId, result);
                
                // Enregistrer les métriques finales
                performanceMonitoringService.incrementCustomCounter("async.payroll.tasks.completed");
                performanceMonitoringService.updateCustomGauge("async.payroll.last.success.count", successCount.get());
                performanceMonitoringService.updateCustomGauge("async.payroll.last.error.count", errorCount.get());
                
                return result;
                
            } catch (Exception e) {
                log.error("Error in async payroll calculation - Task: {}", taskId, e);
                taskStatus.setStatus("FAILED");
                taskStatus.setErrorMessage(e.getMessage());
                taskStatus.setCompletedAt(LocalDateTime.now());
                
                performanceMonitoringService.incrementCustomCounter("async.payroll.tasks.failed");
                
                throw new RuntimeException("Async payroll calculation failed", e);
            }
        }, taskExecutor);
    }

    /**
     * Génération asynchrone de rapports
     */
    @Async
    public CompletableFuture<String> generateReportAsync(String reportType, 
                                                        Map<String, Object> parameters) {
        String taskId = generateTaskId("REPORT_GEN");
        AsyncTaskStatus taskStatus = new AsyncTaskStatus(taskId, "REPORT_GENERATION", LocalDateTime.now());
        activeTasks.put(taskId, taskStatus);
        
        log.info("Starting async report generation - Task: {}, Type: {}", taskId, reportType);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                taskStatus.setStatus("PROCESSING");
                
                // Simulation de génération de rapport
                int totalSteps = 5;
                taskStatus.setTotalItems(totalSteps);
                
                for (int step = 1; step <= totalSteps; step++) {
                    if (taskStatus.isCancelled()) {
                        throw new RuntimeException("Report generation cancelled");
                    }
                    
                    // Simuler le traitement
                    Thread.sleep(2000);
                    
                    taskStatus.setProcessedItems(step);
                    taskStatus.setProgress((double) step / totalSteps);
                    
                    log.debug("Report generation progress - Task: {}, Step: {}/{}", taskId, step, totalSteps);
                }
                
                // Finaliser
                String reportPath = "/reports/" + reportType + "_" + taskId + ".pdf";
                taskStatus.setCompletedAt(LocalDateTime.now());
                taskStatus.setStatus("COMPLETED");
                taskStatus.setResult(reportPath);
                
                log.info("Async report generation completed - Task: {}, Path: {}", taskId, reportPath);
                
                performanceMonitoringService.incrementCustomCounter("async.report.tasks.completed");
                
                return reportPath;
                
            } catch (Exception e) {
                log.error("Error in async report generation - Task: {}", taskId, e);
                taskStatus.setStatus("FAILED");
                taskStatus.setErrorMessage(e.getMessage());
                taskStatus.setCompletedAt(LocalDateTime.now());
                
                performanceMonitoringService.incrementCustomCounter("async.report.tasks.failed");
                
                throw new RuntimeException("Async report generation failed", e);
            }
        }, taskExecutor);
    }

    /**
     * Export asynchrone de données
     */
    @Async
    public CompletableFuture<String> exportDataAsync(String entityType, 
                                                    Map<String, Object> filters, 
                                                    String format) {
        String taskId = generateTaskId("DATA_EXPORT");
        AsyncTaskStatus taskStatus = new AsyncTaskStatus(taskId, "DATA_EXPORT", LocalDateTime.now());
        activeTasks.put(taskId, taskStatus);
        
        log.info("Starting async data export - Task: {}, Entity: {}, Format: {}", taskId, entityType, format);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                taskStatus.setStatus("PROCESSING");
                
                // Simuler le comptage des enregistrements
                int totalRecords = 1000; // Simulation
                taskStatus.setTotalItems(totalRecords);
                
                // Simuler l'export par batches
                int batchSize = 100;
                int processedRecords = 0;
                
                while (processedRecords < totalRecords) {
                    if (taskStatus.isCancelled()) {
                        throw new RuntimeException("Data export cancelled");
                    }
                    
                    int currentBatchSize = Math.min(batchSize, totalRecords - processedRecords);
                    
                    // Simuler le traitement du batch
                    Thread.sleep(500);
                    
                    processedRecords += currentBatchSize;
                    taskStatus.setProcessedItems(processedRecords);
                    taskStatus.setProgress((double) processedRecords / totalRecords);
                    
                    if (processedRecords % 200 == 0) {
                        log.debug("Data export progress - Task: {}, Records: {}/{}", 
                                taskId, processedRecords, totalRecords);
                    }
                }
                
                // Finaliser
                String exportPath = "/exports/" + entityType + "_" + taskId + "." + format;
                taskStatus.setCompletedAt(LocalDateTime.now());
                taskStatus.setStatus("COMPLETED");
                taskStatus.setResult(exportPath);
                
                log.info("Async data export completed - Task: {}, Path: {}, Records: {}", 
                        taskId, exportPath, totalRecords);
                
                performanceMonitoringService.incrementCustomCounter("async.export.tasks.completed");
                performanceMonitoringService.updateCustomGauge("async.export.last.record.count", totalRecords);
                
                return exportPath;
                
            } catch (Exception e) {
                log.error("Error in async data export - Task: {}", taskId, e);
                taskStatus.setStatus("FAILED");
                taskStatus.setErrorMessage(e.getMessage());
                taskStatus.setCompletedAt(LocalDateTime.now());
                
                performanceMonitoringService.incrementCustomCounter("async.export.tasks.failed");
                
                throw new RuntimeException("Async data export failed", e);
            }
        }, taskExecutor);
    }

    /**
     * Traitement asynchrone de notifications en masse
     */
    @Async
    public CompletableFuture<String> sendBulkNotificationsAsync(List<String> recipients, 
                                                               String message, 
                                                               String type) {
        String taskId = generateTaskId("BULK_NOTIFY");
        AsyncTaskStatus taskStatus = new AsyncTaskStatus(taskId, "BULK_NOTIFICATION", LocalDateTime.now());
        taskStatus.setTotalItems(recipients.size());
        activeTasks.put(taskId, taskStatus);
        
        log.info("Starting async bulk notifications - Task: {}, Recipients: {}", taskId, recipients.size());
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                AtomicInteger sentCount = new AtomicInteger(0);
                AtomicInteger failedCount = new AtomicInteger(0);
                
                // Traitement parallèle avec limitation de concurrence
                Semaphore semaphore = new Semaphore(10); // Max 10 notifications simultanées
                
                List<CompletableFuture<Void>> futures = recipients.stream()
                    .map(recipient -> CompletableFuture.runAsync(() -> {
                        try {
                            semaphore.acquire();
                            
                            if (taskStatus.isCancelled()) {
                                return;
                            }
                            
                            // Simuler l'envoi de notification
                            Thread.sleep(200);
                            
                            // Simuler succès/échec (90% de succès)
                            if (Math.random() < 0.9) {
                                sentCount.incrementAndGet();
                            } else {
                                failedCount.incrementAndGet();
                            }
                            
                            int processed = sentCount.get() + failedCount.get();
                            taskStatus.setProcessedItems(processed);
                            taskStatus.setProgress((double) processed / recipients.size());
                            
                        } catch (Exception e) {
                            log.warn("Error sending notification to {}: {}", recipient, e.getMessage());
                            failedCount.incrementAndGet();
                        } finally {
                            semaphore.release();
                        }
                    }, taskExecutor))
                    .collect(ArrayList::new, (list, future) -> list.add(future), ArrayList::addAll);
                
                // Attendre la completion
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                
                // Finaliser
                taskStatus.setCompletedAt(LocalDateTime.now());
                taskStatus.setStatus("COMPLETED");
                taskStatus.setSuccessCount(sentCount.get());
                taskStatus.setErrorCount(failedCount.get());
                
                String result = String.format("Bulk notifications completed - Sent: %d, Failed: %d", 
                                             sentCount.get(), failedCount.get());
                
                log.info("Async bulk notifications completed - Task: {}, Result: {}", taskId, result);
                
                performanceMonitoringService.incrementCustomCounter("async.notification.tasks.completed");
                performanceMonitoringService.updateCustomGauge("async.notification.last.sent.count", sentCount.get());
                performanceMonitoringService.updateCustomGauge("async.notification.last.failed.count", failedCount.get());
                
                return result;
                
            } catch (Exception e) {
                log.error("Error in async bulk notifications - Task: {}", taskId, e);
                taskStatus.setStatus("FAILED");
                taskStatus.setErrorMessage(e.getMessage());
                taskStatus.setCompletedAt(LocalDateTime.now());
                
                performanceMonitoringService.incrementCustomCounter("async.notification.tasks.failed");
                
                throw new RuntimeException("Async bulk notifications failed", e);
            }
        }, taskExecutor);
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
     * Génération d'un ID de tâche unique
     */
    private String generateTaskId(String taskType) {
        return taskType + "_" + taskIdCounter.incrementAndGet() + "_" + System.currentTimeMillis();
    }

    /**
     * Obtention du statut d'une tâche
     */
    public AsyncTaskStatus getTaskStatus(String taskId) {
        return activeTasks.get(taskId);
    }

    /**
     * Annulation d'une tâche
     */
    public boolean cancelTask(String taskId) {
        AsyncTaskStatus taskStatus = activeTasks.get(taskId);
        if (taskStatus != null && !taskStatus.isCompleted()) {
            taskStatus.setCancelled(true);
            taskStatus.setStatus("CANCELLED");
            taskStatus.setCompletedAt(LocalDateTime.now());
            
            log.info("Task cancelled: {}", taskId);
            performanceMonitoringService.incrementCustomCounter("async.tasks.cancelled");
            
            return true;
        }
        return false;
    }

    /**
     * Liste des tâches actives
     */
    public List<AsyncTaskStatus> getActiveTasks() {
        return new ArrayList<>(activeTasks.values());
    }

    /**
     * Nettoyage des tâches terminées anciennes
     */
    public void cleanupCompletedTasks() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        
        activeTasks.entrySet().removeIf(entry -> {
            AsyncTaskStatus status = entry.getValue();
            return status.isCompleted() && status.getCompletedAt().isBefore(cutoff);
        });
        
        log.debug("Cleaned up old completed tasks, remaining: {}", activeTasks.size());
    }

    /**
     * Statistiques des tâches asynchrones
     */
    public Map<String, Object> getAsyncTaskStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        long runningTasks = activeTasks.values().stream()
            .filter(task -> "PROCESSING".equals(task.getStatus()) || "RUNNING".equals(task.getStatus()))
            .count();
        
        long completedTasks = activeTasks.values().stream()
            .filter(task -> "COMPLETED".equals(task.getStatus()))
            .count();
        
        long failedTasks = activeTasks.values().stream()
            .filter(task -> "FAILED".equals(task.getStatus()))
            .count();
        
        long cancelledTasks = activeTasks.values().stream()
            .filter(task -> "CANCELLED".equals(task.getStatus()))
            .count();
        
        stats.put("running_tasks", runningTasks);
        stats.put("completed_tasks", completedTasks);
        stats.put("failed_tasks", failedTasks);
        stats.put("cancelled_tasks", cancelledTasks);
        stats.put("total_tasks", activeTasks.size());
        
        // Statistiques du pool de threads
        stats.put("thread_pool", Map.of(
            "core_pool_size", taskExecutor.getCorePoolSize(),
            "max_pool_size", taskExecutor.getMaxPoolSize(),
            "active_count", taskExecutor.getActiveCount(),
            "pool_size", taskExecutor.getPoolSize(),
            "queue_size", taskExecutor.getThreadPoolExecutor().getQueue().size()
        ));
        
        stats.put("timestamp", LocalDateTime.now());
        
        return stats;
    }

    /**
     * Arrêt gracieux du service
     */
    public void shutdown() {
        log.info("Shutting down async processing service...");
        
        // Annuler toutes les tâches en cours
        activeTasks.values().stream()
            .filter(task -> !task.isCompleted())
            .forEach(task -> {
                task.setCancelled(true);
                task.setStatus("CANCELLED");
                task.setCompletedAt(LocalDateTime.now());
            });
        
        // Arrêter le pool de threads
        taskExecutor.shutdown();
        
        try {
            if (!taskExecutor.getThreadPoolExecutor().awaitTermination(30, TimeUnit.SECONDS)) {
                taskExecutor.getThreadPoolExecutor().shutdownNow();
            }
        } catch (InterruptedException e) {
            taskExecutor.getThreadPoolExecutor().shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        log.info("Async processing service shutdown completed");
    }

    /**
     * Classe pour le statut des tâches asynchrones
     */
    public static class AsyncTaskStatus {
        private final String taskId;
        private final String taskType;
        private final LocalDateTime startedAt;
        private volatile String status = "RUNNING";
        private volatile long totalItems = 0;
        private volatile long processedItems = 0;
        private volatile double progress = 0.0;
        private volatile LocalDateTime completedAt;
        private volatile String errorMessage;
        private volatile boolean cancelled = false;
        private volatile Object result;
        private volatile int successCount = 0;
        private volatile int errorCount = 0;

        public AsyncTaskStatus(String taskId, String taskType, LocalDateTime startedAt) {
            this.taskId = taskId;
            this.taskType = taskType;
            this.startedAt = startedAt;
        }

        // Getters et setters
        public String getTaskId() { return taskId; }
        public String getTaskType() { return taskType; }
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
        public Object getResult() { return result; }
        public void setResult(Object result) { this.result = result; }
        public int getSuccessCount() { return successCount; }
        public void setSuccessCount(int successCount) { this.successCount = successCount; }
        public int getErrorCount() { return errorCount; }
        public void setErrorCount(int errorCount) { this.errorCount = errorCount; }
        public boolean isCompleted() { return completedAt != null; }
    }
}