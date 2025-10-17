package com.bantuops.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO pour les résultats d'opérations en masse
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkOperationResult<T> {
    
    private String operationId;
    private List<T> results;
    private List<String> errors;
    private long processingTimeMs;
    private int processedCount;
    private int totalCount;
    private LocalDateTime completedAt;
    private String status;
    
    public BulkOperationResult(String operationId, List<T> results, List<String> errors, 
                              long processingTimeMs, int processedCount, int totalCount) {
        this.operationId = operationId;
        this.results = results;
        this.errors = errors;
        this.processingTimeMs = processingTimeMs;
        this.processedCount = processedCount;
        this.totalCount = totalCount;
        this.completedAt = LocalDateTime.now();
        this.status = errors.isEmpty() ? "SUCCESS" : "PARTIAL_SUCCESS";
    }
    
    public double getSuccessRate() {
        if (totalCount == 0) return 0.0;
        return (double) (processedCount - errors.size()) / totalCount;
    }
    
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
    
    public int getErrorCount() {
        return errors.size();
    }
    
    public int getSuccessCount() {
        return processedCount - errors.size();
    }
}