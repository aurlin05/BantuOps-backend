package com.bantuops.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service de suivi des performances de base de données
 * Surveille les requêtes lentes, les connexions et les statistiques
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DatabasePerformanceTracker {

    private final EntityManager entityManager;
    private final DataSource dataSource;
    private final PerformanceMonitoringService performanceMonitoringService;
    
    // Suivi des requêtes
    private final Map<String, QueryStats> queryStatistics = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> slowQueries = new ConcurrentHashMap<>();
    private final List<SlowQueryRecord> recentSlowQueries = Collections.synchronizedList(new ArrayList<>());
    
    // Seuils de performance
    private static final long SLOW_QUERY_THRESHOLD_MS = 1000; // 1 seconde
    private static final long VERY_SLOW_QUERY_THRESHOLD_MS = 5000; // 5 secondes
    private static final int MAX_SLOW_QUERY_RECORDS = 100;

    /**
     * Surveillance périodique des performances de base de données (toutes les 5 minutes)
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void monitorDatabasePerformance() {
        try {
            collectDatabaseStatistics();
            analyzeSlowQueries();
            checkConnectionPoolHealth();
            cleanupOldRecords();
        } catch (Exception e) {
            log.error("Error monitoring database performance", e);
        }
    }

    /**
     * Enregistrement d'une requête exécutée
     */
    public void recordQuery(String queryType, String query, long executionTimeMs) {
        // Mise à jour des statistiques générales
        QueryStats stats = queryStatistics.computeIfAbsent(queryType, k -> new QueryStats());
        stats.recordExecution(executionTimeMs);
        
        // Enregistrement dans le service de monitoring
        performanceMonitoringService.recordDatabaseQuery(executionTimeMs);
        
        // Suivi des requêtes lentes
        if (executionTimeMs > SLOW_QUERY_THRESHOLD_MS) {
            recordSlowQuery(queryType, query, executionTimeMs);
        }
        
        log.debug("Query recorded - Type: {}, Duration: {}ms", queryType, executionTimeMs);
    }

    /**
     * Enregistrement d'une requête lente
     */
    private void recordSlowQuery(String queryType, String query, long executionTimeMs) {
        // Incrémenter le compteur de requêtes lentes
        slowQueries.computeIfAbsent(queryType, k -> new AtomicLong(0)).incrementAndGet();
        
        // Enregistrer les détails de la requête lente
        SlowQueryRecord record = new SlowQueryRecord(
            queryType, 
            query, 
            executionTimeMs, 
            LocalDateTime.now()
        );
        
        synchronized (recentSlowQueries) {
            recentSlowQueries.add(record);
            
            // Limiter le nombre d'enregistrements
            if (recentSlowQueries.size() > MAX_SLOW_QUERY_RECORDS) {
                recentSlowQueries.remove(0);
            }
        }
        
        // Log d'alerte pour les requêtes très lentes
        if (executionTimeMs > VERY_SLOW_QUERY_THRESHOLD_MS) {
            log.warn("Very slow query detected - Type: {}, Duration: {}ms, Query: {}", 
                    queryType, executionTimeMs, truncateQuery(query));
        }
    }

    /**
     * Collecte des statistiques de base de données
     */
    private void collectDatabaseStatistics() {
        try (Connection connection = dataSource.getConnection()) {
            collectPostgreSQLStatistics(connection);
        } catch (SQLException e) {
            log.error("Error collecting database statistics", e);
        }
    }

    /**
     * Collecte des statistiques spécifiques à PostgreSQL
     */
    private void collectPostgreSQLStatistics(Connection connection) throws SQLException {
        // Statistiques des tables
        String tableStatsQuery = 
            "SELECT schemaname, tablename, n_tup_ins, n_tup_upd, n_tup_del, " +
            "n_live_tup, n_dead_tup, seq_scan, seq_tup_read, idx_scan, idx_tup_fetch " +
            "FROM pg_stat_user_tables " +
            "WHERE schemaname = 'public'";
            
        try (PreparedStatement stmt = connection.prepareStatement(tableStatsQuery);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                String tableName = rs.getString("tablename");
                long seqScans = rs.getLong("seq_scan");
                long idxScans = rs.getLong("idx_scan");
                long liveTuples = rs.getLong("n_live_tup");
                long deadTuples = rs.getLong("n_dead_tup");
                
                // Calculer le ratio d'utilisation des index
                double indexUsageRatio = (seqScans + idxScans) > 0 ? 
                    (double) idxScans / (seqScans + idxScans) : 0.0;
                
                // Calculer le ratio de tuples morts
                double deadTupleRatio = (liveTuples + deadTuples) > 0 ? 
                    (double) deadTuples / (liveTuples + deadTuples) : 0.0;
                
                // Enregistrer les métriques
                performanceMonitoringService.updateCustomGauge(
                    "database.table." + tableName + ".index.usage.ratio", indexUsageRatio);
                performanceMonitoringService.updateCustomGauge(
                    "database.table." + tableName + ".dead.tuple.ratio", deadTupleRatio);
                
                // Alertes pour les tables problématiques
                if (indexUsageRatio < 0.5 && (seqScans + idxScans) > 1000) {
                    log.warn("Low index usage for table {}: {:.2f}%", tableName, indexUsageRatio * 100);
                }
                
                if (deadTupleRatio > 0.2) {
                    log.warn("High dead tuple ratio for table {}: {:.2f}%", tableName, deadTupleRatio * 100);
                }
            }
        }
        
        // Statistiques des index
        collectIndexStatistics(connection);
        
        // Statistiques des requêtes actives
        collectActiveQueryStatistics(connection);
        
        // Statistiques de cache
        collectCacheStatistics(connection);
    }

    /**
     * Collecte des statistiques des index
     */
    private void collectIndexStatistics(Connection connection) throws SQLException {
        String indexStatsQuery = 
            "SELECT schemaname, tablename, indexname, idx_scan, idx_tup_read, idx_tup_fetch " +
            "FROM pg_stat_user_indexes " +
            "WHERE schemaname = 'public' " +
            "ORDER BY idx_scan DESC";
            
        try (PreparedStatement stmt = connection.prepareStatement(indexStatsQuery);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                String indexName = rs.getString("indexname");
                long indexScans = rs.getLong("idx_scan");
                
                performanceMonitoringService.updateCustomGauge(
                    "database.index." + indexName + ".scans", indexScans);
                
                // Identifier les index inutilisés
                if (indexScans == 0) {
                    log.debug("Unused index detected: {}", indexName);
                    performanceMonitoringService.incrementCustomCounter("database.unused.indexes");
                }
            }
        }
    }

    /**
     * Collecte des statistiques des requêtes actives
     */
    private void collectActiveQueryStatistics(Connection connection) throws SQLException {
        String activeQueriesQuery = 
            "SELECT count(*) as active_queries, " +
            "count(CASE WHEN state = 'active' THEN 1 END) as running_queries, " +
            "count(CASE WHEN state = 'idle in transaction' THEN 1 END) as idle_in_transaction " +
            "FROM pg_stat_activity " +
            "WHERE datname = current_database()";
            
        try (PreparedStatement stmt = connection.prepareStatement(activeQueriesQuery);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                int activeQueries = rs.getInt("active_queries");
                int runningQueries = rs.getInt("running_queries");
                int idleInTransaction = rs.getInt("idle_in_transaction");
                
                performanceMonitoringService.updateCustomGauge("database.active.queries", activeQueries);
                performanceMonitoringService.updateCustomGauge("database.running.queries", runningQueries);
                performanceMonitoringService.updateCustomGauge("database.idle.in.transaction", idleInTransaction);
                
                // Alerte pour trop de connexions inactives en transaction
                if (idleInTransaction > 10) {
                    log.warn("High number of idle in transaction connections: {}", idleInTransaction);
                }
            }
        }
    }

    /**
     * Collecte des statistiques de cache
     */
    private void collectCacheStatistics(Connection connection) throws SQLException {
        String cacheStatsQuery = 
            "SELECT sum(heap_blks_read) as heap_read, " +
            "sum(heap_blks_hit) as heap_hit, " +
            "sum(idx_blks_read) as idx_read, " +
            "sum(idx_blks_hit) as idx_hit " +
            "FROM pg_statio_user_tables";
            
        try (PreparedStatement stmt = connection.prepareStatement(cacheStatsQuery);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                long heapRead = rs.getLong("heap_read");
                long heapHit = rs.getLong("heap_hit");
                long idxRead = rs.getLong("idx_read");
                long idxHit = rs.getLong("idx_hit");
                
                // Calculer les ratios de cache hit
                double heapCacheRatio = (heapRead + heapHit) > 0 ? 
                    (double) heapHit / (heapRead + heapHit) : 0.0;
                double indexCacheRatio = (idxRead + idxHit) > 0 ? 
                    (double) idxHit / (idxRead + idxHit) : 0.0;
                
                performanceMonitoringService.updateCustomGauge("database.heap.cache.ratio", heapCacheRatio);
                performanceMonitoringService.updateCustomGauge("database.index.cache.ratio", indexCacheRatio);
                
                // Alertes pour faible cache hit ratio
                if (heapCacheRatio < 0.9) {
                    log.warn("Low heap cache hit ratio: {:.2f}%", heapCacheRatio * 100);
                }
                if (indexCacheRatio < 0.95) {
                    log.warn("Low index cache hit ratio: {:.2f}%", indexCacheRatio * 100);
                }
            }
        }
    }

    /**
     * Analyse des requêtes lentes
     */
    private void analyzeSlowQueries() {
        if (recentSlowQueries.isEmpty()) {
            return;
        }
        
        Map<String, List<SlowQueryRecord>> queriesByType = new HashMap<>();
        
        synchronized (recentSlowQueries) {
            for (SlowQueryRecord record : recentSlowQueries) {
                queriesByType.computeIfAbsent(record.getQueryType(), k -> new ArrayList<>())
                           .add(record);
            }
        }
        
        // Analyser chaque type de requête
        for (Map.Entry<String, List<SlowQueryRecord>> entry : queriesByType.entrySet()) {
            String queryType = entry.getKey();
            List<SlowQueryRecord> queries = entry.getValue();
            
            if (queries.size() > 5) { // Plus de 5 requêtes lentes du même type
                double avgDuration = queries.stream()
                    .mapToLong(SlowQueryRecord::getExecutionTimeMs)
                    .average()
                    .orElse(0.0);
                
                log.warn("Frequent slow queries detected - Type: {}, Count: {}, Avg Duration: {:.2f}ms", 
                        queryType, queries.size(), avgDuration);
                
                performanceMonitoringService.incrementCustomCounter("database.frequent.slow.queries." + queryType);
            }
        }
    }

    /**
     * Vérification de la santé du pool de connexions
     */
    private void checkConnectionPoolHealth() {
        try (Connection connection = dataSource.getConnection()) {
            // Test de connectivité simple
            try (PreparedStatement stmt = connection.prepareStatement("SELECT 1")) {
                stmt.executeQuery();
            }
            
            performanceMonitoringService.updateCustomGauge("database.connection.health", 1.0);
        } catch (SQLException e) {
            log.error("Database connection health check failed", e);
            performanceMonitoringService.updateCustomGauge("database.connection.health", 0.0);
            performanceMonitoringService.incrementCustomCounter("database.connection.failures");
        }
    }

    /**
     * Nettoyage des anciens enregistrements
     */
    private void cleanupOldRecords() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        
        synchronized (recentSlowQueries) {
            recentSlowQueries.removeIf(record -> record.getTimestamp().isBefore(cutoff));
        }
        
        log.debug("Cleaned up old slow query records, remaining: {}", recentSlowQueries.size());
    }

    /**
     * Obtention des statistiques de requêtes
     */
    public Map<String, QueryStats> getQueryStatistics() {
        return new HashMap<>(queryStatistics);
    }

    /**
     * Obtention des requêtes lentes récentes
     */
    public List<SlowQueryRecord> getRecentSlowQueries() {
        synchronized (recentSlowQueries) {
            return new ArrayList<>(recentSlowQueries);
        }
    }

    /**
     * Obtention du rapport de performance de base de données
     */
    public Map<String, Object> getDatabasePerformanceReport() {
        Map<String, Object> report = new HashMap<>();
        
        // Statistiques générales
        report.put("total_queries", queryStatistics.values().stream()
                .mapToLong(QueryStats::getTotalExecutions)
                .sum());
        
        report.put("avg_query_time_ms", queryStatistics.values().stream()
                .mapToDouble(QueryStats::getAverageExecutionTime)
                .average()
                .orElse(0.0));
        
        report.put("slow_queries_count", recentSlowQueries.size());
        
        // Top requêtes lentes par type
        Map<String, Long> slowQueriesByType = new HashMap<>();
        slowQueries.forEach((type, count) -> slowQueriesByType.put(type, count.get()));
        report.put("slow_queries_by_type", slowQueriesByType);
        
        // Statistiques par type de requête
        Map<String, Map<String, Object>> queryTypeStats = new HashMap<>();
        queryStatistics.forEach((type, stats) -> {
            Map<String, Object> typeReport = new HashMap<>();
            typeReport.put("total_executions", stats.getTotalExecutions());
            typeReport.put("avg_execution_time_ms", stats.getAverageExecutionTime());
            typeReport.put("min_execution_time_ms", stats.getMinExecutionTime());
            typeReport.put("max_execution_time_ms", stats.getMaxExecutionTime());
            queryTypeStats.put(type, typeReport);
        });
        report.put("query_type_statistics", queryTypeStats);
        
        report.put("timestamp", LocalDateTime.now());
        
        return report;
    }

    /**
     * Recommandations d'optimisation
     */
    public List<String> getOptimizationRecommendations() {
        List<String> recommendations = new ArrayList<>();
        
        // Analyser les requêtes lentes fréquentes
        Map<String, Long> slowQueryCounts = new HashMap<>();
        slowQueries.forEach((type, count) -> slowQueryCounts.put(type, count.get()));
        
        slowQueryCounts.entrySet().stream()
            .filter(entry -> entry.getValue() > 10)
            .forEach(entry -> recommendations.add(
                String.format("Consider optimizing %s queries - %d slow executions detected", 
                             entry.getKey(), entry.getValue())));
        
        // Analyser les statistiques de requêtes
        queryStatistics.entrySet().stream()
            .filter(entry -> entry.getValue().getAverageExecutionTime() > SLOW_QUERY_THRESHOLD_MS)
            .forEach(entry -> recommendations.add(
                String.format("Query type %s has high average execution time: %.2fms", 
                             entry.getKey(), entry.getValue().getAverageExecutionTime())));
        
        if (recommendations.isEmpty()) {
            recommendations.add("No specific optimization recommendations at this time");
        }
        
        return recommendations;
    }

    /**
     * Troncature d'une requête pour le logging
     */
    private String truncateQuery(String query) {
        if (query == null) return "null";
        return query.length() > 200 ? query.substring(0, 200) + "..." : query;
    }

    /**
     * Réinitialisation des statistiques
     */
    public void resetStatistics() {
        queryStatistics.clear();
        slowQueries.clear();
        recentSlowQueries.clear();
        log.info("Database performance statistics reset");
    }

    /**
     * Classe pour les statistiques de requêtes
     */
    public static class QueryStats {
        private final AtomicLong totalExecutions = new AtomicLong(0);
        private final AtomicLong totalExecutionTime = new AtomicLong(0);
        private volatile long minExecutionTime = Long.MAX_VALUE;
        private volatile long maxExecutionTime = Long.MIN_VALUE;

        public void recordExecution(long executionTimeMs) {
            totalExecutions.incrementAndGet();
            totalExecutionTime.addAndGet(executionTimeMs);
            
            // Mise à jour thread-safe des min/max
            synchronized (this) {
                if (executionTimeMs < minExecutionTime) {
                    minExecutionTime = executionTimeMs;
                }
                if (executionTimeMs > maxExecutionTime) {
                    maxExecutionTime = executionTimeMs;
                }
            }
        }

        public long getTotalExecutions() { return totalExecutions.get(); }
        public double getAverageExecutionTime() { 
            long executions = totalExecutions.get();
            return executions > 0 ? (double) totalExecutionTime.get() / executions : 0.0;
        }
        public long getMinExecutionTime() { return minExecutionTime == Long.MAX_VALUE ? 0 : minExecutionTime; }
        public long getMaxExecutionTime() { return maxExecutionTime == Long.MIN_VALUE ? 0 : maxExecutionTime; }
    }

    /**
     * Classe pour les enregistrements de requêtes lentes
     */
    public static class SlowQueryRecord {
        private final String queryType;
        private final String query;
        private final long executionTimeMs;
        private final LocalDateTime timestamp;

        public SlowQueryRecord(String queryType, String query, long executionTimeMs, LocalDateTime timestamp) {
            this.queryType = queryType;
            this.query = query;
            this.executionTimeMs = executionTimeMs;
            this.timestamp = timestamp;
        }

        public String getQueryType() { return queryType; }
        public String getQuery() { return query; }
        public long getExecutionTimeMs() { return executionTimeMs; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
}