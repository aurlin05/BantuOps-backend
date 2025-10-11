package com.bantuops.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Service for Redis maintenance tasks including cache cleanup and session management
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisMaintenanceService {

    private final SessionManagementService sessionManagementService;
    private final DistributedSessionService distributedSessionService;
    private final CacheEvictionService cacheEvictionService;

    /**
     * Cleanup expired sessions every 5 minutes
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void cleanupExpiredSessions() {
        log.debug("Starting scheduled session cleanup");
        
        try {
            sessionManagementService.cleanupExpiredSessions();
            log.debug("Session cleanup completed");
        } catch (Exception e) {
            log.error("Session cleanup failed", e);
        }
    }

    /**
     * Cleanup orphaned distributed sessions every 15 minutes
     */
    @Scheduled(fixedRate = 900000) // 15 minutes
    public void cleanupOrphanedDistributedSessions() {
        log.debug("Starting orphaned distributed session cleanup");
        
        try {
            distributedSessionService.cleanupOrphanedSessions();
            log.debug("Orphaned session cleanup completed");
        } catch (Exception e) {
            log.error("Orphaned session cleanup failed", e);
        }
    }

    /**
     * Log cache statistics every hour
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    public void logCacheStatistics() {
        try {
            cacheEvictionService.logCacheStatistics();
        } catch (Exception e) {
            log.error("Failed to log cache statistics", e);
        }
    }

    /**
     * Log session statistics every 30 minutes
     */
    @Scheduled(fixedRate = 1800000) // 30 minutes
    public void logSessionStatistics() {
        try {
            var stats = sessionManagementService.getDistributedSessionStatistics();
            log.info("Session Statistics: {}", stats);
        } catch (Exception e) {
            log.error("Failed to log session statistics", e);
        }
    }

    /**
     * Perform comprehensive Redis health check every hour
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    public void performRedisHealthCheck() {
        log.debug("Starting Redis health check");
        
        try {
            // Check session management
            long activeSessionCount = sessionManagementService.getActiveSessionCount();
            log.info("Redis Health Check - Active sessions: {}", activeSessionCount);
            
            // Check distributed session registry
            var distributedStats = distributedSessionService.getDistributedSessionStatistics();
            log.info("Redis Health Check - Distributed session stats: {}", distributedStats);
            
            log.debug("Redis health check completed successfully");
        } catch (Exception e) {
            log.error("Redis health check failed", e);
        }
    }
}