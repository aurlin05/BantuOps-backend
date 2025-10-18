package com.bantuops.backend.dto.sync;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO pour les rapports de cohérence des données
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataConsistencyReport {
    
    private String reportId;
    
    private LocalDateTime generatedAt;
    
    private ConsistencyStatus overallStatus;
    
    private List<EntityConsistencyCheck> entityChecks;
    
    private List<DataInconsistency> inconsistencies;
    
    private ConsistencyMetrics metrics;
    
    private Map<String, Object> recommendations;
    
    public enum ConsistencyStatus {
        CONSISTENT,
        MINOR_INCONSISTENCIES,
        MAJOR_INCONSISTENCIES,
        CRITICAL_INCONSISTENCIES
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EntityConsistencyCheck {
        private String entityType;
        private int totalEntities;
        private int consistentEntities;
        private int inconsistentEntities;
        private double consistencyPercentage;
        private LocalDateTime lastChecked;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataInconsistency {
        private String entityType;
        private Long entityId;
        private String field;
        private Object frontendValue;
        private Object backendValue;
        private String inconsistencyType;
        private String severity;
        private LocalDateTime detectedAt;
        private String description;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsistencyMetrics {
        private double overallConsistencyPercentage;
        private int totalEntitiesChecked;
        private int totalInconsistencies;
        private long checkDurationMs;
        private Map<String, Integer> inconsistenciesByType;
        private Map<String, Integer> inconsistenciesBySeverity;
    }
}