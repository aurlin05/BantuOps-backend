package com.bantuops.backend.dto.sync;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO pour la résolution des conflits de synchronisation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConflictResolution {
    
    private String conflictId;
    
    private String entityType;
    
    private Long entityId;
    
    private ResolutionStrategy strategy;
    
    private Map<String, Object> resolvedData;
    
    private String resolvedBy;
    
    private LocalDateTime resolvedAt;
    
    private String reason;
    
    private Map<String, Object> originalFrontendData;
    
    private Map<String, Object> originalBackendData;
    
    public enum ResolutionStrategy {
        FRONTEND_WINS,
        BACKEND_WINS,
        MERGE_DATA,
        MANUAL_RESOLUTION,
        LATEST_TIMESTAMP_WINS,
        CUSTOM_RULE
    }
}