package com.bantuops.backend.dto.sync;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO pour les requêtes de synchronisation entre frontend et backend
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncRequest {
    
    @NotNull
    private String entityType;
    
    @NotNull
    private SyncOperation operation;
    
    private List<Long> entityIds;
    
    private LocalDateTime lastSyncTimestamp;
    
    private Map<String, Object> filters;
    
    private String clientVersion;
    
    private String sessionId;
    
    private boolean forceSync;
    
    public enum SyncOperation {
        FULL_SYNC,
        INCREMENTAL_SYNC,
        CONFLICT_RESOLUTION,
        STATUS_CHECK
    }
}