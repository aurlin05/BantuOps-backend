package com.bantuops.backend.service.sync;

import com.bantuops.backend.dto.sync.SyncStatus;
import com.bantuops.backend.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service de monitoring du statut des synchronisations.
 * Utilise Redis pour le stockage distribué des statuts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SyncStatusMonitor {

    private final RedisTemplate<String, Object> redisTemplate;
    private final AuditService auditService;
    
    // Cache local pour les performances
    private final Map<String, SyncStatusInfo> localStatusCache = new ConcurrentHashMap<>();
    
    // Préfixes Redis
    private static final String SYNC_STATUS_PREFIX = "sync:status:";
    private static final String SYNC_HISTORY_PREFIX = "sync:history:";
    private static final String SYNC_METRICS_PREFIX = "sync:metrics:";
    
    // TTL pour les données Redis (24 heures)
    private static final Duration REDIS_TTL = Duration.ofHours(24);
    
    /**
     * Met à jour le statut d'une synchronisation
     */
    public void updateSyncStatus(String syncId, SyncStatus status) {
        log.debug("Mise à jour du statut de synchronisation {} vers {}", syncId, status);
        
        LocalDateTime timestamp = LocalDateTime.now();
        SyncStatusInfo statusInfo = SyncStatusInfo.builder()
            .syncId(syncId)
            .status(status)
            .timestamp(timestamp)
            .lastUpdated(timestamp)
            .build();
        
        // Mettre à jour le cache local
        localStatusCache.put(syncId, statusInfo);
        
        // Mettre à jour Redis
        String redisKey = SYNC_STATUS_PREFIX + syncId;
        redisTemplate.opsForValue().set(redisKey, statusInfo, REDIS_TTL);
        
        // Ajouter à l'historique
        addToSyncHistory(syncId, status, timestamp);
        
        // Mettre à jour les métriques
        updateSyncMetrics(syncId, status);
        
        // Audit du changement de statut
        auditService.logSyncStatusChange(syncId, status, timestamp);
        
        log.debug("Statut de synchronisation {} mis à jour vers {} à {}", syncId, status, timestamp);
    }
    
    /**
     * Obtient le statut actuel d'une synchronisation
     */
    public SyncStatus getSyncStatus(String syncId) {
        // Vérifier d'abord le cache local
        SyncStatusInfo statusInfo = localStatusCache.get(syncId);
        if (statusInfo != null) {
            return statusInfo.getStatus();
        }
        
        // Vérifier Redis
        String redisKey = SYNC_STATUS_PREFIX + syncId;
        Object redisValue = redisTemplate.opsForValue().get(redisKey);
        
        if (redisValue instanceof SyncStatusInfo) {
            statusInfo = (SyncStatusInfo) redisValue;
            // Mettre à jour le cache local
            localStatusCache.put(syncId, statusInfo);
            return statusInfo.getStatus();
        }
        
        log.warn("Aucun statut trouvé pour la synchronisation {}", syncId);
        return SyncStatus.PENDING; // Statut par défaut
    }
    
    /**
     * Obtient les informations détaillées du statut
     */
    public SyncStatusInfo getSyncStatusInfo(String syncId) {
        // Vérifier le cache local
        SyncStatusInfo statusInfo = localStatusCache.get(syncId);
        if (statusInfo != null) {
            return statusInfo;
        }
        
        // Vérifier Redis
        String redisKey = SYNC_STATUS_PREFIX + syncId;
        Object redisValue = redisTemplate.opsForValue().get(redisKey);
        
        if (redisValue instanceof SyncStatusInfo) {
            statusInfo = (SyncStatusInfo) redisValue;
            localStatusCache.put(syncId, statusInfo);
            return statusInfo;
        }
        
        return null;
    }
    
    /**
     * Obtient tous les statuts de synchronisation actifs
     */
    public Map<String, SyncStatus> getAllActiveSyncStatuses() {
        Map<String, SyncStatus> activeStatuses = new HashMap<>();
        
        // Récupérer depuis Redis
        Set<String> keys = redisTemplate.keys(SYNC_STATUS_PREFIX + "*");
        if (keys != null) {
            for (String key : keys) {
                String syncId = key.substring(SYNC_STATUS_PREFIX.length());
                Object value = redisTemplate.opsForValue().get(key);
                
                if (value instanceof SyncStatusInfo) {
                    SyncStatusInfo statusInfo = (SyncStatusInfo) value;
                    // Ne retourner que les synchronisations actives (non terminées)
                    if (isActiveStatus(statusInfo.getStatus())) {
                        activeStatuses.put(syncId, statusInfo.getStatus());
                    }
                }
            }
        }
        
        return activeStatuses;
    }
    
    /**
     * Obtient l'historique d'une synchronisation
     */
    public List<SyncStatusHistory> getSyncHistory(String syncId) {
        String redisKey = SYNC_HISTORY_PREFIX + syncId;
        List<Object> historyData = redisTemplate.opsForList().range(redisKey, 0, -1);
        
        if (historyData != null) {
            return historyData.stream()
                .filter(item -> item instanceof SyncStatusHistory)
                .map(item -> (SyncStatusHistory) item)
                .sorted(Comparator.comparing(SyncStatusHistory::getTimestamp))
                .collect(Collectors.toList());
        }
        
        return new ArrayList<>();
    }
    
    /**
     * Obtient les métriques de synchronisation
     */
    public SyncMetrics getSyncMetrics(String syncId) {
        String redisKey = SYNC_METRICS_PREFIX + syncId;
        Object metricsData = redisTemplate.opsForValue().get(redisKey);
        
        if (metricsData instanceof SyncMetrics) {
            return (SyncMetrics) metricsData;
        }
        
        return null;
    }
    
    /**
     * Obtient les métriques globales de synchronisation
     */
    public GlobalSyncMetrics getGlobalSyncMetrics() {
        Map<SyncStatus, Integer> statusCounts = new HashMap<>();
        int totalSyncs = 0;
        long totalDuration = 0;
        int activeSyncs = 0;
        
        Set<String> keys = redisTemplate.keys(SYNC_STATUS_PREFIX + "*");
        if (keys != null) {
            for (String key : keys) {
                Object value = redisTemplate.opsForValue().get(key);
                if (value instanceof SyncStatusInfo) {
                    SyncStatusInfo statusInfo = (SyncStatusInfo) value;
                    SyncStatus status = statusInfo.getStatus();
                    
                    statusCounts.merge(status, 1, Integer::sum);
                    totalSyncs++;
                    
                    if (isActiveStatus(status)) {
                        activeSyncs++;
                    }
                }
            }
        }
        
        return GlobalSyncMetrics.builder()
            .totalSynchronizations(totalSyncs)
            .activeSynchronizations(activeSyncs)
            .statusDistribution(statusCounts)
            .averageDurationMs(totalSyncs > 0 ? totalDuration / totalSyncs : 0)
            .lastUpdated(LocalDateTime.now())
            .build();
    }
    
    /**
     * Nettoie les statuts expirés
     */
    public void cleanupExpiredStatuses() {
        log.info("Nettoyage des statuts de synchronisation expirés");
        
        LocalDateTime cutoffTime = LocalDateTime.now().minus(REDIS_TTL);
        int cleanedCount = 0;
        
        // Nettoyer le cache local
        Iterator<Map.Entry<String, SyncStatusInfo>> iterator = localStatusCache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, SyncStatusInfo> entry = iterator.next();
            if (entry.getValue().getTimestamp().isBefore(cutoffTime)) {
                iterator.remove();
                cleanedCount++;
            }
        }
        
        log.info("Nettoyage terminé. {} statuts supprimés du cache local", cleanedCount);
    }
    
    /**
     * Ajoute un événement à l'historique de synchronisation
     */
    private void addToSyncHistory(String syncId, SyncStatus status, LocalDateTime timestamp) {
        String redisKey = SYNC_HISTORY_PREFIX + syncId;
        
        SyncStatusHistory historyEntry = SyncStatusHistory.builder()
            .syncId(syncId)
            .status(status)
            .timestamp(timestamp)
            .build();
        
        redisTemplate.opsForList().leftPush(redisKey, historyEntry);
        redisTemplate.expire(redisKey, REDIS_TTL);
        
        // Limiter l'historique à 100 entrées
        redisTemplate.opsForList().trim(redisKey, 0, 99);
    }
    
    /**
     * Met à jour les métriques de synchronisation
     */
    private void updateSyncMetrics(String syncId, SyncStatus status) {
        String redisKey = SYNC_METRICS_PREFIX + syncId;
        
        SyncMetrics metrics = getSyncMetrics(syncId);
        if (metrics == null) {
            metrics = SyncMetrics.builder()
                .syncId(syncId)
                .startTime(LocalDateTime.now())
                .statusChanges(new ArrayList<>())
                .build();
        }
        
        // Ajouter le changement de statut
        metrics.getStatusChanges().add(SyncStatusHistory.builder()
            .syncId(syncId)
            .status(status)
            .timestamp(LocalDateTime.now())
            .build());
        
        // Mettre à jour les temps selon le statut
        switch (status) {
            case IN_PROGRESS:
                if (metrics.getStartTime() == null) {
                    metrics.setStartTime(LocalDateTime.now());
                }
                break;
            case COMPLETED:
            case FAILED:
            case CANCELLED:
                metrics.setEndTime(LocalDateTime.now());
                if (metrics.getStartTime() != null) {
                    metrics.setDurationMs(Duration.between(metrics.getStartTime(), metrics.getEndTime()).toMillis());
                }
                break;
        }
        
        metrics.setLastUpdated(LocalDateTime.now());
        
        redisTemplate.opsForValue().set(redisKey, metrics, REDIS_TTL);
    }
    
    /**
     * Vérifie si un statut est considéré comme actif
     */
    private boolean isActiveStatus(SyncStatus status) {
        return status == SyncStatus.PENDING || 
               status == SyncStatus.IN_PROGRESS || 
               status == SyncStatus.CONFLICT_DETECTED;
    }
    
    /**
     * Classe interne pour les informations de statut
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SyncStatusInfo {
        private String syncId;
        private SyncStatus status;
        private LocalDateTime timestamp;
        private LocalDateTime lastUpdated;
        private Map<String, Object> metadata;
    }
    
    /**
     * Classe interne pour l'historique des statuts
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SyncStatusHistory {
        private String syncId;
        private SyncStatus status;
        private LocalDateTime timestamp;
        private String details;
    }
    
    /**
     * Classe interne pour les métriques de synchronisation
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SyncMetrics {
        private String syncId;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Long durationMs;
        private List<SyncStatusHistory> statusChanges;
        private LocalDateTime lastUpdated;
        private Map<String, Object> additionalMetrics;
    }
    
    /**
     * Classe interne pour les métriques globales
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class GlobalSyncMetrics {
        private int totalSynchronizations;
        private int activeSynchronizations;
        private Map<SyncStatus, Integer> statusDistribution;
        private long averageDurationMs;
        private LocalDateTime lastUpdated;
        private Map<String, Object> additionalStats;
    }
}