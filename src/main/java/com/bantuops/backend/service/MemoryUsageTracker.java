package com.bantuops.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service de suivi de l'utilisation mémoire avec alertes
 * Surveille la mémoire heap, non-heap et génère des alertes
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryUsageTracker {

    private final PerformanceMonitoringService performanceMonitoringService;
    
    // Beans JMX pour les métriques mémoire
    private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private final List<MemoryPoolMXBean> memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans();
    private final List<GarbageCollectorMXBean> gcMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
    
    // Historique des métriques mémoire
    private final List<MemorySnapshot> memoryHistory = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, List<Long>> poolUsageHistory = new ConcurrentHashMap<>();
    
    // Seuils d'alerte
    private static final double HIGH_MEMORY_THRESHOLD = 0.85;      // 85%
    private static final double CRITICAL_MEMORY_THRESHOLD = 0.95;  // 95%
    private static final long MEMORY_LEAK_THRESHOLD_MB = 100;      // 100MB d'augmentation constante
    private static final int MAX_HISTORY_SIZE = 288;              // 24 heures avec collecte toutes les 5 minutes
    
    // État des alertes
    private volatile boolean highMemoryAlertActive = false;
    private volatile boolean criticalMemoryAlertActive = false;
    private volatile LocalDateTime lastMemoryAlert = null;

    /**
     * Surveillance périodique de la mémoire (toutes les 5 minutes)
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void monitorMemoryUsage() {
        try {
            collectMemoryMetrics();
            analyzeMemoryTrends();
            checkMemoryThresholds();
            detectMemoryLeaks();
            cleanupOldHistory();
        } catch (Exception e) {
            log.error("Error monitoring memory usage", e);
        }
    }

    /**
     * Collecte des métriques mémoire
     */
    private void collectMemoryMetrics() {
        // Métriques heap
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        long heapUsed = heapUsage.getUsed();
        long heapMax = heapUsage.getMax();
        double heapUsageRatio = (double) heapUsed / heapMax;
        
        // Métriques non-heap
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();
        long nonHeapUsed = nonHeapUsage.getUsed();
        long nonHeapMax = nonHeapUsage.getMax();
        double nonHeapUsageRatio = nonHeapMax > 0 ? (double) nonHeapUsed / nonHeapMax : 0.0;
        
        // Enregistrer dans le service de monitoring
        performanceMonitoringService.updateCustomGauge("memory.heap.used.bytes", heapUsed);
        performanceMonitoringService.updateCustomGauge("memory.heap.max.bytes", heapMax);
        performanceMonitoringService.updateCustomGauge("memory.heap.usage.ratio", heapUsageRatio);
        performanceMonitoringService.updateCustomGauge("memory.nonheap.used.bytes", nonHeapUsed);
        performanceMonitoringService.updateCustomGauge("memory.nonheap.usage.ratio", nonHeapUsageRatio);
        
        // Créer un snapshot
        MemorySnapshot snapshot = new MemorySnapshot(
            LocalDateTime.now(),
            heapUsed,
            heapMax,
            nonHeapUsed,
            nonHeapMax,
            collectPoolUsages(),
            collectGCStats()
        );
        
        synchronized (memoryHistory) {
            memoryHistory.add(snapshot);
            
            // Limiter la taille de l'historique
            if (memoryHistory.size() > MAX_HISTORY_SIZE) {
                memoryHistory.remove(0);
            }
        }
        
        log.debug("Memory metrics collected - Heap: {:.2f}% ({}/{}), Non-Heap: {:.2f}%", 
                 heapUsageRatio * 100, formatBytes(heapUsed), formatBytes(heapMax),
                 nonHeapUsageRatio * 100);
    }

    /**
     * Collecte des utilisations des pools mémoire
     */
    private Map<String, Long> collectPoolUsages() {
        Map<String, Long> poolUsages = new HashMap<>();
        
        for (MemoryPoolMXBean poolMXBean : memoryPoolMXBeans) {
            String poolName = poolMXBean.getName();
            MemoryUsage usage = poolMXBean.getUsage();
            
            if (usage != null) {
                long used = usage.getUsed();
                long max = usage.getMax();
                
                poolUsages.put(poolName, used);
                
                // Enregistrer dans le monitoring
                performanceMonitoringService.updateCustomGauge(
                    "memory.pool." + sanitizePoolName(poolName) + ".used.bytes", used);
                
                if (max > 0) {
                    double usageRatio = (double) used / max;
                    performanceMonitoringService.updateCustomGauge(
                        "memory.pool." + sanitizePoolName(poolName) + ".usage.ratio", usageRatio);
                }
                
                // Maintenir l'historique du pool
                poolUsageHistory.computeIfAbsent(poolName, k -> Collections.synchronizedList(new ArrayList<>()))
                               .add(used);
                
                // Limiter l'historique du pool
                List<Long> history = poolUsageHistory.get(poolName);
                if (history.size() > MAX_HISTORY_SIZE) {
                    history.remove(0);
                }
            }
        }
        
        return poolUsages;
    }

    /**
     * Collecte des statistiques de garbage collection
     */
    private Map<String, Object> collectGCStats() {
        Map<String, Object> gcStats = new HashMap<>();
        
        long totalCollections = 0;
        long totalCollectionTime = 0;
        
        for (GarbageCollectorMXBean gcMXBean : gcMXBeans) {
            String gcName = gcMXBean.getName();
            long collections = gcMXBean.getCollectionCount();
            long collectionTime = gcMXBean.getCollectionTime();
            
            totalCollections += collections;
            totalCollectionTime += collectionTime;
            
            gcStats.put(gcName + ".collections", collections);
            gcStats.put(gcName + ".time.ms", collectionTime);
            
            // Enregistrer dans le monitoring
            performanceMonitoringService.updateCustomGauge(
                "gc." + sanitizeGCName(gcName) + ".collections", collections);
            performanceMonitoringService.updateCustomGauge(
                "gc." + sanitizeGCName(gcName) + ".time.ms", collectionTime);
        }
        
        gcStats.put("total.collections", totalCollections);
        gcStats.put("total.time.ms", totalCollectionTime);
        
        performanceMonitoringService.updateCustomGauge("gc.total.collections", totalCollections);
        performanceMonitoringService.updateCustomGauge("gc.total.time.ms", totalCollectionTime);
        
        return gcStats;
    }

    /**
     * Analyse des tendances mémoire
     */
    private void analyzeMemoryTrends() {
        if (memoryHistory.size() < 10) {
            return; // Pas assez de données
        }
        
        synchronized (memoryHistory) {
            // Analyser la tendance des 10 dernières mesures
            List<MemorySnapshot> recentSnapshots = memoryHistory.subList(
                Math.max(0, memoryHistory.size() - 10), memoryHistory.size());
            
            // Calculer la tendance de la mémoire heap
            double heapTrend = calculateMemoryTrend(recentSnapshots, MemorySnapshot::getHeapUsed);
            performanceMonitoringService.updateCustomGauge("memory.heap.trend.mb.per.hour", heapTrend);
            
            // Calculer la tendance de la mémoire non-heap
            double nonHeapTrend = calculateMemoryTrend(recentSnapshots, MemorySnapshot::getNonHeapUsed);
            performanceMonitoringService.updateCustomGauge("memory.nonheap.trend.mb.per.hour", nonHeapTrend);
            
            log.debug("Memory trends - Heap: {:.2f} MB/h, Non-Heap: {:.2f} MB/h", heapTrend, nonHeapTrend);
        }
    }

    /**
     * Calcul de la tendance mémoire
     */
    private double calculateMemoryTrend(List<MemorySnapshot> snapshots, 
                                       java.util.function.ToLongFunction<MemorySnapshot> extractor) {
        if (snapshots.size() < 2) {
            return 0.0;
        }
        
        MemorySnapshot first = snapshots.get(0);
        MemorySnapshot last = snapshots.get(snapshots.size() - 1);
        
        long memoryDiff = extractor.applyAsLong(last) - extractor.applyAsLong(first);
        long timeDiff = java.time.Duration.between(first.getTimestamp(), last.getTimestamp()).toMinutes();
        
        if (timeDiff > 0) {
            // Convertir en MB par heure
            return (memoryDiff / (1024.0 * 1024.0)) * (60.0 / timeDiff);
        }
        
        return 0.0;
    }

    /**
     * Vérification des seuils mémoire
     */
    private void checkMemoryThresholds() {
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        double heapUsageRatio = (double) heapUsage.getUsed() / heapUsage.getMax();
        
        // Vérification du seuil critique
        if (heapUsageRatio > CRITICAL_MEMORY_THRESHOLD) {
            if (!criticalMemoryAlertActive) {
                triggerCriticalMemoryAlert(heapUsageRatio);
                criticalMemoryAlertActive = true;
                highMemoryAlertActive = true; // Implique aussi l'alerte haute
            }
        } else if (heapUsageRatio > HIGH_MEMORY_THRESHOLD) {
            if (!highMemoryAlertActive) {
                triggerHighMemoryAlert(heapUsageRatio);
                highMemoryAlertActive = true;
            }
            criticalMemoryAlertActive = false;
        } else {
            // Réinitialiser les alertes si la mémoire est revenue à la normale
            if (highMemoryAlertActive || criticalMemoryAlertActive) {
                log.info("Memory usage returned to normal: {:.2f}%", heapUsageRatio * 100);
                highMemoryAlertActive = false;
                criticalMemoryAlertActive = false;
            }
        }
    }

    /**
     * Détection des fuites mémoire
     */
    private void detectMemoryLeaks() {
        if (memoryHistory.size() < 12) { // Au moins 1 heure de données
            return;
        }
        
        synchronized (memoryHistory) {
            // Analyser les 12 dernières mesures (1 heure)
            List<MemorySnapshot> recentSnapshots = memoryHistory.subList(
                Math.max(0, memoryHistory.size() - 12), memoryHistory.size());
            
            // Vérifier si la mémoire augmente constamment
            boolean consistentIncrease = true;
            long totalIncrease = 0;
            
            for (int i = 1; i < recentSnapshots.size(); i++) {
                long currentUsage = recentSnapshots.get(i).getHeapUsed();
                long previousUsage = recentSnapshots.get(i - 1).getHeapUsed();
                long increase = currentUsage - previousUsage;
                
                if (increase <= 0) {
                    consistentIncrease = false;
                    break;
                }
                
                totalIncrease += increase;
            }
            
            // Si augmentation constante et significative
            if (consistentIncrease && totalIncrease > MEMORY_LEAK_THRESHOLD_MB * 1024 * 1024) {
                triggerMemoryLeakAlert(totalIncrease);
            }
        }
    }

    /**
     * Déclenchement d'alerte mémoire haute
     */
    private void triggerHighMemoryAlert(double usageRatio) {
        log.warn("HIGH MEMORY USAGE ALERT: {:.2f}% heap memory used", usageRatio * 100);
        performanceMonitoringService.incrementCustomCounter("alerts.memory.high");
        lastMemoryAlert = LocalDateTime.now();
        
        // Suggérer un garbage collection
        suggestGarbageCollection();
    }

    /**
     * Déclenchement d'alerte mémoire critique
     */
    private void triggerCriticalMemoryAlert(double usageRatio) {
        log.error("CRITICAL MEMORY USAGE ALERT: {:.2f}% heap memory used", usageRatio * 100);
        performanceMonitoringService.incrementCustomCounter("alerts.memory.critical");
        lastMemoryAlert = LocalDateTime.now();
        
        // Forcer un garbage collection
        forceGarbageCollection();
    }

    /**
     * Déclenchement d'alerte fuite mémoire
     */
    private void triggerMemoryLeakAlert(long totalIncrease) {
        log.error("POTENTIAL MEMORY LEAK DETECTED: {} MB consistent increase over 1 hour", 
                 totalIncrease / (1024 * 1024));
        performanceMonitoringService.incrementCustomCounter("alerts.memory.leak");
        lastMemoryAlert = LocalDateTime.now();
    }

    /**
     * Suggestion de garbage collection
     */
    private void suggestGarbageCollection() {
        log.info("Suggesting garbage collection due to high memory usage");
        // Dans un environnement de production, on pourrait déclencher un GC programmé
        // System.gc(); // Généralement déconseillé en production
    }

    /**
     * Force un garbage collection (uniquement en cas critique)
     */
    private void forceGarbageCollection() {
        log.warn("Forcing garbage collection due to critical memory usage");
        System.gc(); // Utilisé uniquement en cas critique
        
        // Attendre un peu et vérifier l'amélioration
        try {
            Thread.sleep(1000);
            MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
            double newUsageRatio = (double) heapUsage.getUsed() / heapUsage.getMax();
            log.info("Memory usage after forced GC: {:.2f}%", newUsageRatio * 100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Nettoyage de l'ancien historique
     */
    private void cleanupOldHistory() {
        // L'historique est déjà limité dans collectMemoryMetrics()
        // Nettoyer l'historique des pools
        poolUsageHistory.values().forEach(history -> {
            if (history.size() > MAX_HISTORY_SIZE) {
                synchronized (history) {
                    while (history.size() > MAX_HISTORY_SIZE) {
                        history.remove(0);
                    }
                }
            }
        });
    }

    /**
     * Rapport de l'utilisation mémoire
     */
    public Map<String, Object> getMemoryUsageReport() {
        Map<String, Object> report = new HashMap<>();
        
        // Métriques actuelles
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();
        
        report.put("heap", Map.of(
            "used_mb", heapUsage.getUsed() / (1024 * 1024),
            "max_mb", heapUsage.getMax() / (1024 * 1024),
            "usage_ratio", (double) heapUsage.getUsed() / heapUsage.getMax(),
            "committed_mb", heapUsage.getCommitted() / (1024 * 1024)
        ));
        
        report.put("non_heap", Map.of(
            "used_mb", nonHeapUsage.getUsed() / (1024 * 1024),
            "max_mb", nonHeapUsage.getMax() > 0 ? nonHeapUsage.getMax() / (1024 * 1024) : -1,
            "usage_ratio", nonHeapUsage.getMax() > 0 ? (double) nonHeapUsage.getUsed() / nonHeapUsage.getMax() : 0.0,
            "committed_mb", nonHeapUsage.getCommitted() / (1024 * 1024)
        ));
        
        // Pools mémoire
        Map<String, Map<String, Object>> pools = new HashMap<>();
        for (MemoryPoolMXBean poolMXBean : memoryPoolMXBeans) {
            MemoryUsage usage = poolMXBean.getUsage();
            if (usage != null) {
                pools.put(poolMXBean.getName(), Map.of(
                    "used_mb", usage.getUsed() / (1024 * 1024),
                    "max_mb", usage.getMax() > 0 ? usage.getMax() / (1024 * 1024) : -1,
                    "type", poolMXBean.getType().toString()
                ));
            }
        }
        report.put("memory_pools", pools);
        
        // Statistiques GC
        Map<String, Object> gcStats = collectGCStats();
        report.put("garbage_collection", gcStats);
        
        // État des alertes
        report.put("alerts", Map.of(
            "high_memory_active", highMemoryAlertActive,
            "critical_memory_active", criticalMemoryAlertActive,
            "last_alert", lastMemoryAlert
        ));
        
        // Tendances (si disponibles)
        if (memoryHistory.size() >= 10) {
            synchronized (memoryHistory) {
                List<MemorySnapshot> recentSnapshots = memoryHistory.subList(
                    Math.max(0, memoryHistory.size() - 10), memoryHistory.size());
                
                double heapTrend = calculateMemoryTrend(recentSnapshots, MemorySnapshot::getHeapUsed);
                double nonHeapTrend = calculateMemoryTrend(recentSnapshots, MemorySnapshot::getNonHeapUsed);
                
                report.put("trends", Map.of(
                    "heap_mb_per_hour", heapTrend,
                    "non_heap_mb_per_hour", nonHeapTrend
                ));
            }
        }
        
        report.put("timestamp", LocalDateTime.now());
        
        return report;
    }

    /**
     * Recommandations d'optimisation mémoire
     */
    public List<String> getMemoryOptimizationRecommendations() {
        List<String> recommendations = new ArrayList<>();
        
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        double heapUsageRatio = (double) heapUsage.getUsed() / heapUsage.getMax();
        
        if (heapUsageRatio > HIGH_MEMORY_THRESHOLD) {
            recommendations.add("Consider increasing heap size (-Xmx) or optimizing memory usage");
        }
        
        if (criticalMemoryAlertActive) {
            recommendations.add("URGENT: Critical memory usage detected - investigate memory leaks");
        }
        
        // Analyser les pools mémoire
        for (MemoryPoolMXBean poolMXBean : memoryPoolMXBeans) {
            MemoryUsage usage = poolMXBean.getUsage();
            if (usage != null && usage.getMax() > 0) {
                double poolUsageRatio = (double) usage.getUsed() / usage.getMax();
                if (poolUsageRatio > 0.9) {
                    recommendations.add(String.format("Memory pool '%s' is %.1f%% full - consider tuning", 
                                                     poolMXBean.getName(), poolUsageRatio * 100));
                }
            }
        }
        
        // Analyser les tendances
        if (memoryHistory.size() >= 10) {
            synchronized (memoryHistory) {
                List<MemorySnapshot> recentSnapshots = memoryHistory.subList(
                    Math.max(0, memoryHistory.size() - 10), memoryHistory.size());
                
                double heapTrend = calculateMemoryTrend(recentSnapshots, MemorySnapshot::getHeapUsed);
                if (heapTrend > 50) { // Plus de 50MB/h d'augmentation
                    recommendations.add("Memory usage is increasing rapidly - investigate potential memory leaks");
                }
            }
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("Memory usage is within normal parameters");
        }
        
        return recommendations;
    }

    /**
     * Formatage des bytes en format lisible
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    /**
     * Sanitisation du nom de pool pour les métriques
     */
    private String sanitizePoolName(String poolName) {
        return poolName.replaceAll("[^a-zA-Z0-9._-]", "_").toLowerCase();
    }

    /**
     * Sanitisation du nom de GC pour les métriques
     */
    private String sanitizeGCName(String gcName) {
        return gcName.replaceAll("[^a-zA-Z0-9._-]", "_").toLowerCase();
    }

    /**
     * Réinitialisation des statistiques
     */
    public void resetStatistics() {
        memoryHistory.clear();
        poolUsageHistory.clear();
        highMemoryAlertActive = false;
        criticalMemoryAlertActive = false;
        lastMemoryAlert = null;
        log.info("Memory usage statistics reset");
    }

    /**
     * Classe pour les snapshots mémoire
     */
    public static class MemorySnapshot {
        private final LocalDateTime timestamp;
        private final long heapUsed;
        private final long heapMax;
        private final long nonHeapUsed;
        private final long nonHeapMax;
        private final Map<String, Long> poolUsages;
        private final Map<String, Object> gcStats;

        public MemorySnapshot(LocalDateTime timestamp, long heapUsed, long heapMax, 
                             long nonHeapUsed, long nonHeapMax, 
                             Map<String, Long> poolUsages, Map<String, Object> gcStats) {
            this.timestamp = timestamp;
            this.heapUsed = heapUsed;
            this.heapMax = heapMax;
            this.nonHeapUsed = nonHeapUsed;
            this.nonHeapMax = nonHeapMax;
            this.poolUsages = new HashMap<>(poolUsages);
            this.gcStats = new HashMap<>(gcStats);
        }

        public LocalDateTime getTimestamp() { return timestamp; }
        public long getHeapUsed() { return heapUsed; }
        public long getHeapMax() { return heapMax; }
        public long getNonHeapUsed() { return nonHeapUsed; }
        public long getNonHeapMax() { return nonHeapMax; }
        public Map<String, Long> getPoolUsages() { return new HashMap<>(poolUsages); }
        public Map<String, Object> getGcStats() { return new HashMap<>(gcStats); }
    }
}