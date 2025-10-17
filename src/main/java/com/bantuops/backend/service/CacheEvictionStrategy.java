package com.bantuops.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service pour la gestion intelligente de l'éviction des caches
 * Maintient la cohérence des données en évictant les caches appropriés
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CacheEvictionStrategy {

    private final CachedCalculationService cachedCalculationService;
    private final CacheManager cacheManager;
    
    // Suivi des modifications pour éviction intelligente
    private final Set<Long> modifiedEmployees = ConcurrentHashMap.newKeySet();
    private final Set<String> modifiedConfigurations = ConcurrentHashMap.newKeySet();
    private volatile LocalDateTime lastTaxRateUpdate = LocalDateTime.now();
    private volatile LocalDateTime lastAttendanceRuleUpdate = LocalDateTime.now();

    /**
     * Éviction automatique périodique (toutes les heures)
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    @Async
    public void scheduledCacheEviction() {
        log.debug("Starting scheduled cache eviction...");
        
        try {
            // Éviction des caches expirés ou potentiellement obsolètes
            evictStaleCalculations();
            evictOldFinancialReports();
            processModifiedEmployees();
            processModifiedConfigurations();
            
            log.info("Scheduled cache eviction completed successfully");
        } catch (Exception e) {
            log.error("Error during scheduled cache eviction", e);
        }
    }

    /**
     * Éviction lors de la modification d'un employé
     */
    public void onEmployeeModified(Long employeeId) {
        log.debug("Employee {} modified, scheduling cache eviction", employeeId);
        
        // Éviction immédiate des caches critiques
        cachedCalculationService.evictEmployeeCache(employeeId);
        
        // Marquer pour éviction différée des caches liés
        modifiedEmployees.add(employeeId);
        
        // Éviction des calculs de paie pour tous les mois
        YearMonth currentMonth = YearMonth.now();
        for (int i = 0; i < 6; i++) { // 6 derniers mois
            YearMonth period = currentMonth.minusMonths(i);
            cachedCalculationService.evictPayrollCache(employeeId, period);
        }
        
        log.info("Cache eviction triggered for employee: {}", employeeId);
    }

    /**
     * Éviction lors de la modification des taux de taxes
     */
    public void onTaxRatesModified() {
        log.info("Tax rates modified, evicting related caches");
        
        cachedCalculationService.evictTaxRatesCache();
        
        // Éviction de tous les calculs de paie car ils dépendent des taux de taxes
        evictCacheByName("payroll-calculations");
        evictCacheByName("tax-calculations");
        
        lastTaxRateUpdate = LocalDateTime.now();
        
        log.info("Tax rates cache eviction completed");
    }

    /**
     * Éviction lors de la modification des règles d'assiduité
     */
    public void onAttendanceRulesModified() {
        log.info("Attendance rules modified, evicting related caches");
        
        cachedCalculationService.evictAttendanceRulesCache();
        
        // Éviction des calculs qui dépendent des règles d'assiduité
        evictCacheByName("frequent-calculations");
        
        lastAttendanceRuleUpdate = LocalDateTime.now();
        
        log.info("Attendance rules cache eviction completed");
    }

    /**
     * Éviction lors de la modification de la configuration système
     */
    public void onSystemConfigModified(String configKey) {
        log.debug("System configuration '{}' modified", configKey);
        
        modifiedConfigurations.add(configKey);
        
        // Éviction immédiate pour les configurations critiques
        if (isCriticalConfiguration(configKey)) {
            evictCriticalConfigurationCaches(configKey);
        }
    }

    /**
     * Éviction lors de la modification d'une facture
     */
    public void onInvoiceModified(Long invoiceId) {
        log.debug("Invoice {} modified, evicting related caches", invoiceId);
        
        // Éviction des rapports financiers qui pourraient inclure cette facture
        evictCacheByName("financial-reports");
        
        // Éviction du cache de la facture spécifique
        evictCacheByKey("invoices", invoiceId.toString());
    }

    /**
     * Éviction lors du changement de période comptable
     */
    public void onAccountingPeriodChange(YearMonth newPeriod) {
        log.info("Accounting period changed to {}, evicting period-sensitive caches", newPeriod);
        
        // Éviction de tous les caches sensibles à la période
        evictCacheByName("payroll-calculations");
        evictCacheByName("financial-reports");
        evictCacheByName("tax-calculations");
        
        log.info("Accounting period cache eviction completed");
    }

    /**
     * Éviction intelligente basée sur les dépendances
     */
    public void evictDependentCaches(String entityType, Long entityId) {
        log.debug("Evicting dependent caches for {} with ID {}", entityType, entityId);
        
        switch (entityType.toUpperCase()) {
            case "EMPLOYEE":
                evictEmployeeDependentCaches(entityId);
                break;
            case "INVOICE":
                evictInvoiceDependentCaches(entityId);
                break;
            case "ATTENDANCE":
                evictAttendanceDependentCaches(entityId);
                break;
            case "PAYROLL":
                evictPayrollDependentCaches(entityId);
                break;
            default:
                log.warn("Unknown entity type for cache eviction: {}", entityType);
        }
    }

    /**
     * Éviction des calculs obsolètes
     */
    private void evictStaleCalculations() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(2);
        
        // Les calculs fréquents de plus de 2 heures sont considérés comme obsolètes
        if (lastTaxRateUpdate.isBefore(cutoff)) {
            evictCacheByName("frequent-calculations");
        }
    }

    /**
     * Éviction des anciens rapports financiers
     */
    private void evictOldFinancialReports() {
        // Les rapports financiers sont évictés après 15 minutes (selon la config Redis)
        // Cette méthode peut forcer l'éviction si nécessaire
        evictCacheByName("financial-reports");
        log.debug("Old financial reports evicted");
    }

    /**
     * Traitement des employés modifiés
     */
    private void processModifiedEmployees() {
        if (!modifiedEmployees.isEmpty()) {
            log.debug("Processing {} modified employees for cache eviction", modifiedEmployees.size());
            
            for (Long employeeId : modifiedEmployees) {
                try {
                    cachedCalculationService.evictAllEmployeeRelatedCaches(employeeId);
                } catch (Exception e) {
                    log.warn("Failed to evict caches for employee {}: {}", employeeId, e.getMessage());
                }
            }
            
            modifiedEmployees.clear();
            log.info("Processed modified employees cache eviction");
        }
    }

    /**
     * Traitement des configurations modifiées
     */
    private void processModifiedConfigurations() {
        if (!modifiedConfigurations.isEmpty()) {
            log.debug("Processing {} modified configurations for cache eviction", modifiedConfigurations.size());
            
            for (String configKey : modifiedConfigurations) {
                try {
                    evictConfigurationDependentCaches(configKey);
                } catch (Exception e) {
                    log.warn("Failed to evict caches for configuration {}: {}", configKey, e.getMessage());
                }
            }
            
            modifiedConfigurations.clear();
            log.info("Processed modified configurations cache eviction");
        }
    }

    /**
     * Éviction des caches dépendants d'un employé
     */
    private void evictEmployeeDependentCaches(Long employeeId) {
        cachedCalculationService.evictAllEmployeeRelatedCaches(employeeId);
        
        // Éviction des rapports qui pourraient inclure cet employé
        evictCacheByName("financial-reports");
    }

    /**
     * Éviction des caches dépendants d'une facture
     */
    private void evictInvoiceDependentCaches(Long invoiceId) {
        evictCacheByKey("invoices", invoiceId.toString());
        evictCacheByName("financial-reports");
    }

    /**
     * Éviction des caches dépendants de l'assiduité
     */
    private void evictAttendanceDependentCaches(Long employeeId) {
        // Éviction des calculs d'assiduité
        evictCacheByName("frequent-calculations");
        
        // Éviction des calculs de paie qui dépendent de l'assiduité
        YearMonth currentMonth = YearMonth.now();
        cachedCalculationService.evictPayrollCache(employeeId, currentMonth);
    }

    /**
     * Éviction des caches dépendants de la paie
     */
    private void evictPayrollDependentCaches(Long employeeId) {
        YearMonth currentMonth = YearMonth.now();
        for (int i = 0; i < 3; i++) {
            YearMonth period = currentMonth.minusMonths(i);
            cachedCalculationService.evictPayrollCache(employeeId, period);
        }
        
        evictCacheByName("financial-reports");
    }

    /**
     * Vérifie si une configuration est critique
     */
    private boolean isCriticalConfiguration(String configKey) {
        List<String> criticalConfigs = Arrays.asList(
            "default.vat.rate",
            "minimum.wage",
            "overtime.rate.multiplier",
            "tax.brackets",
            "social.contribution.rates"
        );
        
        return criticalConfigs.contains(configKey);
    }

    /**
     * Éviction des caches pour les configurations critiques
     */
    private void evictCriticalConfigurationCaches(String configKey) {
        log.info("Critical configuration '{}' changed, evicting dependent caches", configKey);
        
        switch (configKey) {
            case "default.vat.rate":
                evictCacheByName("frequent-calculations");
                evictCacheByName("financial-reports");
                break;
            case "minimum.wage":
            case "overtime.rate.multiplier":
                evictCacheByName("payroll-calculations");
                evictCacheByName("tax-calculations");
                break;
            case "tax.brackets":
            case "social.contribution.rates":
                cachedCalculationService.evictTaxRatesCache();
                evictCacheByName("payroll-calculations");
                break;
        }
    }

    /**
     * Éviction des caches dépendants d'une configuration
     */
    private void evictConfigurationDependentCaches(String configKey) {
        // Éviction du cache de configuration système
        evictCacheByKey("system-config", configKey);
        
        // Éviction des caches dépendants selon la configuration
        if (configKey.contains("tax") || configKey.contains("rate")) {
            evictCacheByName("tax-calculations");
            evictCacheByName("payroll-calculations");
        }
        
        if (configKey.contains("attendance") || configKey.contains("working")) {
            cachedCalculationService.evictAttendanceRulesCache();
        }
    }

    /**
     * Éviction d'un cache complet par nom
     */
    private void evictCacheByName(String cacheName) {
        try {
            if (cacheManager.getCache(cacheName) != null) {
                cacheManager.getCache(cacheName).clear();
                log.debug("Cache '{}' evicted completely", cacheName);
            }
        } catch (Exception e) {
            log.warn("Failed to evict cache '{}': {}", cacheName, e.getMessage());
        }
    }

    /**
     * Éviction d'une clé spécifique dans un cache
     */
    private void evictCacheByKey(String cacheName, String key) {
        try {
            if (cacheManager.getCache(cacheName) != null) {
                cacheManager.getCache(cacheName).evict(key);
                log.debug("Key '{}' evicted from cache '{}'", key, cacheName);
            }
        } catch (Exception e) {
            log.warn("Failed to evict key '{}' from cache '{}': {}", key, cacheName, e.getMessage());
        }
    }

    /**
     * Éviction d'urgence de tous les caches
     */
    public void emergencyEvictAll() {
        log.warn("Emergency cache eviction triggered");
        
        try {
            cachedCalculationService.evictAllCaches();
            
            // Nettoyage des trackers
            modifiedEmployees.clear();
            modifiedConfigurations.clear();
            
            log.info("Emergency cache eviction completed");
        } catch (Exception e) {
            log.error("Failed to perform emergency cache eviction", e);
        }
    }

    /**
     * Statistiques d'éviction pour monitoring
     */
    public CacheEvictionStats getEvictionStats() {
        return CacheEvictionStats.builder()
                .modifiedEmployeesCount(modifiedEmployees.size())
                .modifiedConfigurationsCount(modifiedConfigurations.size())
                .lastTaxRateUpdate(lastTaxRateUpdate)
                .lastAttendanceRuleUpdate(lastAttendanceRuleUpdate)
                .build();
    }

    /**
     * Classe pour les statistiques d'éviction
     */
    public static class CacheEvictionStats {
        private final int modifiedEmployeesCount;
        private final int modifiedConfigurationsCount;
        private final LocalDateTime lastTaxRateUpdate;
        private final LocalDateTime lastAttendanceRuleUpdate;

        private CacheEvictionStats(Builder builder) {
            this.modifiedEmployeesCount = builder.modifiedEmployeesCount;
            this.modifiedConfigurationsCount = builder.modifiedConfigurationsCount;
            this.lastTaxRateUpdate = builder.lastTaxRateUpdate;
            this.lastAttendanceRuleUpdate = builder.lastAttendanceRuleUpdate;
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public int getModifiedEmployeesCount() { return modifiedEmployeesCount; }
        public int getModifiedConfigurationsCount() { return modifiedConfigurationsCount; }
        public LocalDateTime getLastTaxRateUpdate() { return lastTaxRateUpdate; }
        public LocalDateTime getLastAttendanceRuleUpdate() { return lastAttendanceRuleUpdate; }

        public static class Builder {
            private int modifiedEmployeesCount;
            private int modifiedConfigurationsCount;
            private LocalDateTime lastTaxRateUpdate;
            private LocalDateTime lastAttendanceRuleUpdate;

            public Builder modifiedEmployeesCount(int count) {
                this.modifiedEmployeesCount = count;
                return this;
            }

            public Builder modifiedConfigurationsCount(int count) {
                this.modifiedConfigurationsCount = count;
                return this;
            }

            public Builder lastTaxRateUpdate(LocalDateTime dateTime) {
                this.lastTaxRateUpdate = dateTime;
                return this;
            }

            public Builder lastAttendanceRuleUpdate(LocalDateTime dateTime) {
                this.lastAttendanceRuleUpdate = dateTime;
                return this;
            }

            public CacheEvictionStats build() {
                return new CacheEvictionStats(this);
            }
        }
    }
}