package com.bantuops.backend.service;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service pour l'optimisation du pool de connexions HikariCP
 * Surveille et ajuste automatiquement les paramètres du pool
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConnectionPoolOptimizer implements HealthIndicator {

    private final DataSource dataSource;
    private final MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
    
    // Métriques de surveillance
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicInteger idleConnections = new AtomicInteger(0);
    private final AtomicLong totalConnections = new AtomicLong(0);
    private final AtomicLong connectionWaitTime = new AtomicLong(0);
    private final AtomicInteger connectionTimeouts = new AtomicInteger(0);
    
    // Seuils d'optimisation
    private static final double HIGH_USAGE_THRESHOLD = 0.8; // 80%
    private static final double LOW_USAGE_THRESHOLD = 0.3;  // 30%
    private static final long MAX_WAIT_TIME_MS = 5000;      // 5 secondes
    private static final int MAX_TIMEOUTS_PER_HOUR = 10;

    /**
     * Surveillance périodique du pool de connexions (toutes les 5 minutes)
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void monitorConnectionPool() {
        try {
            if (dataSource instanceof HikariDataSource) {
                HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
                HikariPoolMXBean poolMXBean = hikariDataSource.getHikariPoolMXBean();
                
                if (poolMXBean != null) {
                    updateMetrics(poolMXBean);
                    analyzeAndOptimize(hikariDataSource, poolMXBean);
                }
            }
        } catch (Exception e) {
            log.error("Error monitoring connection pool", e);
        }
    }

    /**
     * Mise à jour des métriques du pool
     */
    private void updateMetrics(HikariPoolMXBean poolMXBean) {
        activeConnections.set(poolMXBean.getActiveConnections());
        idleConnections.set(poolMXBean.getIdleConnections());
        totalConnections.set(poolMXBean.getTotalConnections());
        
        log.debug("Connection pool metrics - Active: {}, Idle: {}, Total: {}", 
                 activeConnections.get(), idleConnections.get(), totalConnections.get());
    }

    /**
     * Analyse et optimisation automatique du pool
     */
    private void analyzeAndOptimize(HikariDataSource dataSource, HikariPoolMXBean poolMXBean) {
        int maxPoolSize = dataSource.getMaximumPoolSize();
        int currentActive = activeConnections.get();
        int currentTotal = (int) totalConnections.get();
        
        double usageRatio = (double) currentActive / maxPoolSize;
        
        log.debug("Pool usage ratio: {:.2f} ({}/{})", usageRatio, currentActive, maxPoolSize);
        
        // Optimisation basée sur l'utilisation
        if (usageRatio > HIGH_USAGE_THRESHOLD) {
            optimizeForHighUsage(dataSource, poolMXBean);
        } else if (usageRatio < LOW_USAGE_THRESHOLD) {
            optimizeForLowUsage(dataSource, poolMXBean);
        }
        
        // Optimisation basée sur les temps d'attente
        if (connectionWaitTime.get() > MAX_WAIT_TIME_MS) {
            optimizeForSlowConnections(dataSource);
        }
        
        // Optimisation basée sur les timeouts
        if (connectionTimeouts.get() > MAX_TIMEOUTS_PER_HOUR) {
            optimizeForTimeouts(dataSource);
        }
    }

    /**
     * Optimisation pour usage élevé
     */
    private void optimizeForHighUsage(HikariDataSource dataSource, HikariPoolMXBean poolMXBean) {
        int currentMaxSize = dataSource.getMaximumPoolSize();
        int currentMinIdle = dataSource.getMinimumIdle();
        
        // Augmenter la taille du pool si nécessaire
        if (currentMaxSize < 50) { // Limite raisonnable
            int newMaxSize = Math.min(currentMaxSize + 5, 50);
            dataSource.setMaximumPoolSize(newMaxSize);
            log.info("Increased maximum pool size from {} to {} due to high usage", 
                    currentMaxSize, newMaxSize);
        }
        
        // Augmenter le minimum idle
        if (currentMinIdle < currentMaxSize * 0.5) {
            int newMinIdle = (int) (currentMaxSize * 0.5);
            dataSource.setMinimumIdle(newMinIdle);
            log.info("Increased minimum idle connections from {} to {}", 
                    currentMinIdle, newMinIdle);
        }
        
        // Réduire le timeout de connexion pour éviter les blocages
        if (dataSource.getConnectionTimeout() > 20000) {
            dataSource.setConnectionTimeout(20000); // 20 secondes
            log.info("Reduced connection timeout to 20 seconds for high usage");
        }
    }

    /**
     * Optimisation pour usage faible
     */
    private void optimizeForLowUsage(HikariDataSource dataSource, HikariPoolMXBean poolMXBean) {
        int currentMaxSize = dataSource.getMaximumPoolSize();
        int currentMinIdle = dataSource.getMinimumIdle();
        
        // Réduire le minimum idle pour économiser les ressources
        if (currentMinIdle > 2) {
            int newMinIdle = Math.max(currentMinIdle - 2, 2);
            dataSource.setMinimumIdle(newMinIdle);
            log.info("Decreased minimum idle connections from {} to {} due to low usage", 
                    currentMinIdle, newMinIdle);
        }
        
        // Réduire la durée de vie des connexions inactives
        if (dataSource.getIdleTimeout() > 300000) { // 5 minutes
            dataSource.setIdleTimeout(300000);
            log.info("Reduced idle timeout to 5 minutes for low usage");
        }
    }

    /**
     * Optimisation pour connexions lentes
     */
    private void optimizeForSlowConnections(HikariDataSource dataSource) {
        // Augmenter le timeout de connexion
        long currentTimeout = dataSource.getConnectionTimeout();
        if (currentTimeout < 60000) { // Moins de 60 secondes
            dataSource.setConnectionTimeout(60000);
            log.info("Increased connection timeout from {} to 60 seconds due to slow connections", 
                    currentTimeout);
        }
        
        // Réduire la durée de vie maximale des connexions
        if (dataSource.getMaxLifetime() > 1200000) { // 20 minutes
            dataSource.setMaxLifetime(1200000);
            log.info("Reduced max lifetime to 20 minutes for slow connections");
        }
    }

    /**
     * Optimisation pour timeouts fréquents
     */
    private void optimizeForTimeouts(HikariDataSource dataSource) {
        // Augmenter la taille du pool
        int currentMaxSize = dataSource.getMaximumPoolSize();
        if (currentMaxSize < 30) {
            int newMaxSize = Math.min(currentMaxSize + 10, 30);
            dataSource.setMaximumPoolSize(newMaxSize);
            log.info("Increased maximum pool size from {} to {} due to frequent timeouts", 
                    currentMaxSize, newMaxSize);
        }
        
        // Augmenter le timeout de validation
        if (dataSource.getValidationTimeout() < 10000) {
            dataSource.setValidationTimeout(10000); // 10 secondes
            log.info("Increased validation timeout to 10 seconds");
        }
        
        // Réinitialiser le compteur de timeouts
        connectionTimeouts.set(0);
    }

    /**
     * Configuration optimale pour différents environnements
     */
    public void applyOptimalConfiguration(String environment) {
        if (!(dataSource instanceof HikariDataSource)) {
            log.warn("DataSource is not HikariDataSource, cannot apply optimization");
            return;
        }
        
        HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
        
        switch (environment.toLowerCase()) {
            case "development":
                applyDevelopmentConfig(hikariDataSource);
                break;
            case "testing":
                applyTestingConfig(hikariDataSource);
                break;
            case "production":
                applyProductionConfig(hikariDataSource);
                break;
            default:
                log.warn("Unknown environment: {}, applying default configuration", environment);
                applyDefaultConfig(hikariDataSource);
        }
    }

    /**
     * Configuration pour l'environnement de développement
     */
    private void applyDevelopmentConfig(HikariDataSource dataSource) {
        dataSource.setMaximumPoolSize(10);
        dataSource.setMinimumIdle(2);
        dataSource.setConnectionTimeout(30000);
        dataSource.setIdleTimeout(600000); // 10 minutes
        dataSource.setMaxLifetime(1800000); // 30 minutes
        dataSource.setLeakDetectionThreshold(60000); // 1 minute
        
        log.info("Applied development configuration to connection pool");
    }

    /**
     * Configuration pour l'environnement de test
     */
    private void applyTestingConfig(HikariDataSource dataSource) {
        dataSource.setMaximumPoolSize(5);
        dataSource.setMinimumIdle(1);
        dataSource.setConnectionTimeout(10000);
        dataSource.setIdleTimeout(300000); // 5 minutes
        dataSource.setMaxLifetime(900000); // 15 minutes
        dataSource.setLeakDetectionThreshold(30000); // 30 secondes
        
        log.info("Applied testing configuration to connection pool");
    }

    /**
     * Configuration pour l'environnement de production
     */
    private void applyProductionConfig(HikariDataSource dataSource) {
        dataSource.setMaximumPoolSize(25);
        dataSource.setMinimumIdle(5);
        dataSource.setConnectionTimeout(30000);
        dataSource.setIdleTimeout(600000); // 10 minutes
        dataSource.setMaxLifetime(1800000); // 30 minutes
        dataSource.setLeakDetectionThreshold(0); // Désactivé en production
        
        // Optimisations spécifiques à la production
        dataSource.addDataSourceProperty("cachePrepStmts", "true");
        dataSource.addDataSourceProperty("prepStmtCacheSize", "250");
        dataSource.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        dataSource.addDataSourceProperty("useServerPrepStmts", "true");
        dataSource.addDataSourceProperty("useLocalSessionState", "true");
        dataSource.addDataSourceProperty("rewriteBatchedStatements", "true");
        dataSource.addDataSourceProperty("cacheResultSetMetadata", "true");
        dataSource.addDataSourceProperty("cacheServerConfiguration", "true");
        dataSource.addDataSourceProperty("elideSetAutoCommits", "true");
        dataSource.addDataSourceProperty("maintainTimeStats", "false");
        
        log.info("Applied production configuration to connection pool");
    }

    /**
     * Configuration par défaut
     */
    private void applyDefaultConfig(HikariDataSource dataSource) {
        dataSource.setMaximumPoolSize(15);
        dataSource.setMinimumIdle(3);
        dataSource.setConnectionTimeout(30000);
        dataSource.setIdleTimeout(600000);
        dataSource.setMaxLifetime(1800000);
        
        log.info("Applied default configuration to connection pool");
    }

    /**
     * Diagnostic complet du pool de connexions
     */
    public Map<String, Object> diagnoseConnectionPool() {
        Map<String, Object> diagnosis = new HashMap<>();
        
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            HikariPoolMXBean poolMXBean = hikariDataSource.getHikariPoolMXBean();
            
            if (poolMXBean != null) {
                diagnosis.put("active_connections", poolMXBean.getActiveConnections());
                diagnosis.put("idle_connections", poolMXBean.getIdleConnections());
                diagnosis.put("total_connections", poolMXBean.getTotalConnections());
                diagnosis.put("threads_awaiting_connection", poolMXBean.getThreadsAwaitingConnection());
                
                diagnosis.put("maximum_pool_size", hikariDataSource.getMaximumPoolSize());
                diagnosis.put("minimum_idle", hikariDataSource.getMinimumIdle());
                diagnosis.put("connection_timeout", hikariDataSource.getConnectionTimeout());
                diagnosis.put("idle_timeout", hikariDataSource.getIdleTimeout());
                diagnosis.put("max_lifetime", hikariDataSource.getMaxLifetime());
                diagnosis.put("validation_timeout", hikariDataSource.getValidationTimeout());
                
                // Calculs dérivés
                double usageRatio = (double) poolMXBean.getActiveConnections() / hikariDataSource.getMaximumPoolSize();
                diagnosis.put("usage_ratio", usageRatio);
                diagnosis.put("usage_status", getUsageStatus(usageRatio));
                
                diagnosis.put("connection_wait_time_ms", connectionWaitTime.get());
                diagnosis.put("connection_timeouts", connectionTimeouts.get());
            }
        }
        
        return diagnosis;
    }

    /**
     * Statut d'utilisation basé sur le ratio
     */
    private String getUsageStatus(double usageRatio) {
        if (usageRatio > HIGH_USAGE_THRESHOLD) {
            return "HIGH";
        } else if (usageRatio < LOW_USAGE_THRESHOLD) {
            return "LOW";
        } else {
            return "NORMAL";
        }
    }

    /**
     * Health check pour Spring Boot Actuator
     */
    @Override
    public Health health() {
        try {
            Map<String, Object> diagnosis = diagnoseConnectionPool();
            
            if (dataSource instanceof HikariDataSource) {
                HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
                HikariPoolMXBean poolMXBean = hikariDataSource.getHikariPoolMXBean();
                
                if (poolMXBean != null) {
                    int threadsAwaiting = poolMXBean.getThreadsAwaitingConnection();
                    double usageRatio = (double) poolMXBean.getActiveConnections() / hikariDataSource.getMaximumPoolSize();
                    
                    // Déterminer le statut de santé
                    if (threadsAwaiting > 5 || usageRatio > 0.95) {
                        return Health.down()
                                .withDetails(diagnosis)
                                .withDetail("issue", "High connection pool pressure")
                                .build();
                    } else if (connectionTimeouts.get() > MAX_TIMEOUTS_PER_HOUR) {
                        return Health.down()
                                .withDetails(diagnosis)
                                .withDetail("issue", "Too many connection timeouts")
                                .build();
                    } else {
                        return Health.up()
                                .withDetails(diagnosis)
                                .build();
                    }
                }
            }
            
            return Health.unknown()
                    .withDetail("reason", "Unable to access HikariCP metrics")
                    .build();
                    
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }

    /**
     * Réinitialisation des métriques
     */
    public void resetMetrics() {
        activeConnections.set(0);
        idleConnections.set(0);
        totalConnections.set(0);
        connectionWaitTime.set(0);
        connectionTimeouts.set(0);
        
        log.info("Connection pool metrics reset");
    }

    /**
     * Forcer la fermeture des connexions inactives
     */
    public void evictIdleConnections() {
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            HikariPoolMXBean poolMXBean = hikariDataSource.getHikariPoolMXBean();
            
            if (poolMXBean != null) {
                try {
                    // Utiliser l'API MBean pour forcer l'éviction
                    ObjectName poolName = new ObjectName("com.zaxxer.hikari:type=Pool (HikariPool-1)");
                    if (mBeanServer.isRegistered(poolName)) {
                        mBeanServer.invoke(poolName, "softEvictConnections", null, null);
                        log.info("Forced eviction of idle connections");
                    }
                } catch (Exception e) {
                    log.warn("Failed to evict idle connections: {}", e.getMessage());
                }
            }
        }
    }
}