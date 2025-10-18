package com.bantuops.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class CacheEvictionService {

    private final CacheManager cacheManager;
    private final CachedCalculationService cachedCalculationService;

    /**
     * Scheduled cache cleanup - runs every hour
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    public void scheduledCacheCleanup() {
        log.debug("Starting scheduled cache cleanup");

        try {
            // Clear expired entries (Redis handles this automatically, but we can log it)
            log.debug("Cache cleanup completed");
        } catch (Exception e) {
            log.error("Cache cleanup failed", e);
        }
    }

    /**
     * Clear all caches (for administrative purposes)
     */
    public void clearAllCaches() {
        log.info("Clearing all caches");

        try {
            cacheManager.getCacheNames().forEach(cacheName -> {
                Objects.requireNonNull(cacheManager.getCache(cacheName)).clear();
                log.debug("Cleared cache: {}", cacheName);
            });

            log.info("All caches cleared successfully");
        } catch (Exception e) {
            log.error("Failed to clear all caches", e);
        }
    }

    /**
     * Clear specific cache by name
     */
    public void clearCache(String cacheName) {
        log.info("Clearing cache: {}", cacheName);

        try {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
                log.info("Cache '{}' cleared successfully", cacheName);
            } else {
                log.warn("Cache '{}' not found", cacheName);
            }
        } catch (Exception e) {
            log.error("Failed to clear cache '{}'", cacheName, e);
        }
    }

    /**
     * Evict cache when employee data changes
     */
    public void onEmployeeDataChanged(Long employeeId) {
        log.debug("Employee data changed, evicting related caches for employee: {}", employeeId);

        cachedCalculationService.evictAllEmployeeRelatedCaches(employeeId);
    }

    /**
     * Evict payroll cache for specific employee and period
     */
    public void onPayrollDataChanged(Long employeeId, java.time.YearMonth period) {
        log.debug("Payroll data changed for employee: {}, period: {}", employeeId, period);

        cachedCalculationService.evictPayrollCache(employeeId, period);
    }

    /**
     * Evict cache when user permissions change
     */
    public void onUserPermissionsChanged(Long userId) {
        log.debug("User permissions changed, evicting cache for user: {}", userId);

        // Clear user-related caches - would need specific implementation
        clearCache("user-permissions");
    }

    /**
     * Evict cache when attendance rules change
     */
    public void onAttendanceRulesChanged() {
        log.debug("Attendance rules changed, evicting related caches");

        cachedCalculationService.evictAttendanceRulesCache();
    }

    /**
     * Evict cache when tax rates or payroll rules change
     */
    public void onPayrollRulesChanged() {
        log.debug("Payroll rules changed, evicting payroll calculation caches");

        cachedCalculationService.evictTaxRatesCache();
        clearCache("payroll-calculations");
        clearCache("tax-calculations");
        clearCache("business-rules");
    }

    /**
     * Evict cache when frequent calculations need refresh
     */
    public void onFrequentDataChanged() {
        log.debug("Frequent data changed, evicting frequent calculations cache");

        clearCache("frequent-calculations");
    }

    /**
     * Evict session-related caches
     */
    public void onSessionDataChanged() {
        log.debug("Session data changed, evicting session-related caches");

        clearCache("session-metadata");
    }

    /**
     * Evict audit caches
     */
    public void onAuditDataChanged() {
        log.debug("Audit data changed, evicting audit caches");

        clearCache("audit-cache");
    }

    /**
     * Get cache statistics (for monitoring)
     */
    public void logCacheStatistics() {
        log.info("=== Cache Statistics ===");

        cacheManager.getCacheNames().forEach(cacheName -> {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                // Note: Detailed statistics would require a specific cache implementation
                log.info("Cache '{}' is active", cacheName);
            }
        });

        log.info("=== End Cache Statistics ===");
    }
}