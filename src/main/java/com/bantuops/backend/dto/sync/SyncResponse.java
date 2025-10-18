package com.bantuops.backend.dto.sync;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO pour les réponses de synchronisation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncResponse {
    
    private String syncId;
    
    private SyncStatus status;
    
    private LocalDateTime syncTimestamp;
    
    private List<SyncedEntity> syncedEntities;
    
    private List<SyncConflict> conflicts;
    
    private SyncMetrics metrics;
    
    private String message;
    
    private Map<String, Object> metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyncedEntity {
        private String entityType;
        private Long entityId;
        private String operation;
        private LocalDateTime timestamp;
        private Map<String, Object> data;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyncConflict {
        private String entityType;
        private Long entityId;
        private String conflictType;
        private Map<String, Object> frontendData;
        private Map<String, Object> backendData;
        private LocalDateTime detectedAt;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyncMetrics {
        private int totalEntities;
        private int syncedEntities;
        private int conflictedEntities;
        private int failedEntities;
        private long durationMs;
    }
}