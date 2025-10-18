package com.bantuops.backend.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.concurrent.TimeUnit;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Service de monitoring des performances avec métriques détaillées
 * Collecte et expose les métriques de performance de l'application
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PerformanceMonitoringService implements HealthIndicator {

    private final MeterRegistry meterRegistry;

    // Beans JMX pour les métriques système
    private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private final OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();
    private final RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();

    // Compteurs et métriques personnalisées
    private final Map<String, AtomicLong> customCounters = new ConcurrentHashMap<>();
    private final Map<String, DoubleAdder> customGauges = new ConcurrentHashMap<>();
    private final Map<String, Timer> customTimers = new ConcurrentHashMap<>();

    // Métriques de performance
    private Counter payrollCalculationsCounter;
    private Counter invoiceGenerationsCounter;
    private Counter attendanceRecordsCounter;
    private Counter cacheHitsCounter;
    private Counter cacheMissesCounter;
    private Counter databaseQueriesCounter;
    private Counter apiRequestsCounter;
    private Counter errorCounter;

    private Timer payrollCalculationTimer;
    private Timer databaseQueryTimer;
    private Timer apiResponseTimer;
    private Timer cacheOperationTimer;

    // Seuils d'alerte
    private static final double HIGH_MEMORY_THRESHOLD = 0.85; // 85%
    private static final double HIGH_CPU_THRESHOLD = 0.80; // 80%
    private static final long SLOW_QUERY_THRESHOLD_MS = 5000; // 5 secondes
    private static final long SLOW_API_THRESHOLD_MS = 2000; // 2 secondes

    @PostConstruct
    public void initializeMetrics() {
        // Initialisation des compteurs
        payrollCalculationsCounter = Counter.builder("bantuops.payroll.calculations.total")
                .description("Total number of payroll calculations")
                .register(meterRegistry);

        invoiceGenerationsCounter = Counter.builder("bantuops.invoice.generations.total")
                .description("Total number of invoice generations")
                .register(meterRegistry);

        attendanceRecordsCounter = Counter.builder("bantuops.attendance.records.total")
                .description("Total number of attendance records processed")
                .register(meterRegistry);

        cacheHitsCounter = Counter.builder("bantuops.cache.hits.total")
                .description("Total number of cache hits")
                .register(meterRegistry);

        cacheMissesCounter = Counter.builder("bantuops.cache.misses.total")
                .description("Total number of cache misses")
                .register(meterRegistry);

        databaseQueriesCounter = Counter.builder("bantuops.database.queries.total")
                .description("Total number of database queries")
                .register(meterRegistry);

        apiRequestsCounter = Counter.builder("bantuops.api.requests.total")
                .description("Total number of API requests")
                .register(meterRegistry);

        errorCounter = Counter.builder("bantuops.errors.total")
                .description("Total number of errors")
                .register(meterRegistry);

        // Initialisation des timers
        payrollCalculationTimer = Timer.builder("bantuops.payroll.calculation.duration")
                .description("Time taken for payroll calculations")
                .register(meterRegistry);

        databaseQueryTimer = Timer.builder("bantuops.database.query.duration")
                .description("Time taken for database queries")
                .register(meterRegistry);

        apiResponseTimer = Timer.builder("bantuops.api.response.duration")
                .description("Time taken for API responses")
                .register(meterRegistry);

        cacheOperationTimer = Timer.builder("bantuops.cache.operation.duration")
                .description("Time taken for cache operations")
                .register(meterRegistry);

        // Gauges pour les métriques système
        Gauge.builder("bantuops.system.memory.used.ratio", this, PerformanceMonitoringService::getMemoryUsageRatio)
                .description("Ratio of used memory")
                .register(meterRegistry);

        Gauge.builder("bantuops.system.cpu.usage", this, PerformanceMonitoringService::getCpuUsage)
                .description("CPU usage percentage")
                .register(meterRegistry);

        Gauge.builder("bantuops.system.uptime.seconds", this, PerformanceMonitoringService::getUptimeSeconds)
                .description("Application uptime in seconds")
                .register(meterRegistry);

        log.info("Performance monitoring metrics initialized");
    }

    /**
     * Collecte périodique des métriques (toutes les minutes)
     */
    @Scheduled(fixedRate = 60000) // 1 minute
    public void collectMetrics() {
        try {
            collectSystemMetrics();
            collectApplicationMetrics();
            checkPerformanceThresholds();
        } catch (Exception e) {
            log.error("Error collecting performance metrics", e);
        }
    }

    /**
     * Collecte des métriques système
     */
    private void collectSystemMetrics() {
        // Métriques mémoire
        long usedMemory = memoryMXBean.getHeapMemoryUsage().getUsed();
        long maxMemory = memoryMXBean.getHeapMemoryUsage().getMax();
        double memoryRatio = (double) usedMemory / maxMemory;

        updateCustomGauge("system.memory.used.bytes", usedMemory);
        updateCustomGauge("system.memory.max.bytes", maxMemory);
        updateCustomGauge("system.memory.usage.ratio", memoryRatio);

        // Métriques CPU
        double cpuUsage = getCpuUsage();
        updateCustomGauge("system.cpu.usage", cpuUsage);

        // Métriques de temps d'activité
        long uptime = runtimeMXBean.getUptime();
        updateCustomGauge("system.uptime.milliseconds", uptime);

        log.debug("System metrics collected - Memory: {:.2f}%, CPU: {:.2f}%",
                memoryRatio * 100, cpuUsage * 100);
    }

    /**
     * Collecte des métriques applicatives
     */
    private void collectApplicationMetrics() {
        // Ratio de cache hit
        double cacheHits = cacheHitsCounter.count();
        double cacheMisses = cacheMissesCounter.count();
        double totalCacheOperations = cacheHits + cacheMisses;

        if (totalCacheOperations > 0) {
            double cacheHitRatio = cacheHits / totalCacheOperations;
            updateCustomGauge("cache.hit.ratio", cacheHitRatio);
        }

        // Métriques de performance des requêtes
        double avgQueryTime = databaseQueryTimer.mean(TimeUnit.MILLISECONDS);
        updateCustomGauge("database.query.avg.duration.ms", avgQueryTime);

        double avgApiResponseTime = apiResponseTimer.mean(TimeUnit.MILLISECONDS);
        updateCustomGauge("api.response.avg.duration.ms", avgApiResponseTime);

        log.debug("Application metrics collected - Cache hit ratio: {:.2f}%, Avg query time: {:.2f}ms",
                (totalCacheOperations > 0 ? (cacheHits / totalCacheOperations) * 100 : 0), avgQueryTime);
    }

    /**
     * Vérification des seuils de performance
     */
    private void checkPerformanceThresholds() {
        // Vérification mémoire
        double memoryRatio = getMemoryUsageRatio();
        if (memoryRatio > HIGH_MEMORY_THRESHOLD) {
            log.warn("High memory usage detected: {:.2f}%", memoryRatio * 100);
            incrementCustomCounter("alerts.high.memory");
        }

        // Vérification CPU
        double cpuUsage = getCpuUsage();
        if (cpuUsage > HIGH_CPU_THRESHOLD) {
            log.warn("High CPU usage detected: {:.2f}%", cpuUsage * 100);
            incrementCustomCounter("alerts.high.cpu");
        }

        // Vérification des requêtes lentes
        double avgQueryTime = databaseQueryTimer.mean(TimeUnit.MILLISECONDS);
        if (avgQueryTime > SLOW_QUERY_THRESHOLD_MS) {
            log.warn("Slow database queries detected: {:.2f}ms average", avgQueryTime);
            incrementCustomCounter("alerts.slow.queries");
        }

        // Vérification des API lentes
        double avgApiTime = apiResponseTimer.mean(TimeUnit.MILLISECONDS);
        if (avgApiTime > SLOW_API_THRESHOLD_MS) {
            log.warn("Slow API responses detected: {:.2f}ms average", avgApiTime);
            incrementCustomCounter("alerts.slow.api");
        }
    }

    /**
     * Enregistrement d'un calcul de paie
     */
    public void recordPayrollCalculation(long durationMs) {
        payrollCalculationsCounter.increment();
        payrollCalculationTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * Enregistrement d'une génération de facture
     */
    public void recordInvoiceGeneration() {
        invoiceGenerationsCounter.increment();
    }

    /**
     * Enregistrement d'un enregistrement d'assiduité
     */
    public void recordAttendanceRecord() {
        attendanceRecordsCounter.increment();
    }

    /**
     * Enregistrement d'un hit de cache
     */
    public void recordCacheHit() {
        cacheHitsCounter.increment();
    }

    /**
     * Enregistrement d'un miss de cache
     */
    public void recordCacheMiss() {
        cacheMissesCounter.increment();
    }

    /**
     * Enregistrement d'une requête de base de données
     */
    public void recordDatabaseQuery(long durationMs) {
        databaseQueriesCounter.increment();
        databaseQueryTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * Enregistrement d'une requête API
     */
    public void recordApiRequest(long durationMs) {
        apiRequestsCounter.increment();
        apiResponseTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * Enregistrement d'une erreur
     */
    public void recordError(String errorType) {
        errorCounter.increment();
        incrementCustomCounter("errors." + errorType);
    }

    /**
     * Enregistrement d'une opération de cache
     */
    public void recordCacheOperation(long durationMs) {
        cacheOperationTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * Mise à jour d'un compteur personnalisé
     */
    public void incrementCustomCounter(String name) {
        customCounters.computeIfAbsent(name, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Mise à jour d'une gauge personnalisée
     */
    public void updateCustomGauge(String name, double value) {
        customGauges.computeIfAbsent(name, k -> new DoubleAdder()).reset();
        customGauges.get(name).add(value);
    }

    /**
     * Obtention d'un timer personnalisé
     */
    public Timer getCustomTimer(String name) {
        return customTimers.computeIfAbsent(name, k -> Timer.builder("bantuops.custom." + name)
                .description("Custom timer for " + name)
                .register(meterRegistry));
    }

    /**
     * Ratio d'utilisation mémoire
     */
    public double getMemoryUsageRatio() {
        long used = memoryMXBean.getHeapMemoryUsage().getUsed();
        long max = memoryMXBean.getHeapMemoryUsage().getMax();
        return max > 0 ? (double) used / max : 0.0;
    }

    /**
     * Utilisation CPU
     */
    public double getCpuUsage() {
        if (osMXBean instanceof com.sun.management.OperatingSystemMXBean) {
            return ((com.sun.management.OperatingSystemMXBean) osMXBean).getProcessCpuLoad();
        }
        return osMXBean.getSystemLoadAverage();
    }

    /**
     * Temps d'activité en secondes
     */
    public double getUptimeSeconds() {
        return runtimeMXBean.getUptime() / 1000.0;
    }

    /**
     * Rapport de performance complet
     */
    public Map<String, Object> getPerformanceReport() {
        Map<String, Object> report = new HashMap<>();

        // Métriques système
        report.put("system", Map.of(
                "memory_usage_ratio", getMemoryUsageRatio(),
                "cpu_usage", getCpuUsage(),
                "uptime_seconds", getUptimeSeconds(),
                "memory_used_mb", memoryMXBean.getHeapMemoryUsage().getUsed() / (1024 * 1024),
                "memory_max_mb", memoryMXBean.getHeapMemoryUsage().getMax() / (1024 * 1024)));

        // Métriques applicatives
        double cacheHits = cacheHitsCounter.count();
        double cacheMisses = cacheMissesCounter.count();
        double totalCacheOps = cacheHits + cacheMisses;

        report.put("application", Map.of(
                "payroll_calculations_total", payrollCalculationsCounter.count(),
                "invoice_generations_total", invoiceGenerationsCounter.count(),
                "attendance_records_total", attendanceRecordsCounter.count(),
                "database_queries_total", databaseQueriesCounter.count(),
                "api_requests_total", apiRequestsCounter.count(),
                "errors_total", errorCounter.count(),
                "cache_hit_ratio", totalCacheOps > 0 ? cacheHits / totalCacheOps : 0.0,
                "avg_payroll_calculation_ms", payrollCalculationTimer.mean(TimeUnit.MILLISECONDS),
                "avg_database_query_ms", databaseQueryTimer.mean(TimeUnit.MILLISECONDS),
                "avg_api_response_ms", apiResponseTimer.mean(TimeUnit.MILLISECONDS)));

        // Métriques personnalisées
        Map<String, Object> customMetrics = new HashMap<>();
        customCounters.forEach((key, value) -> customMetrics.put(key, value.get()));
        customGauges.forEach((key, value) -> customMetrics.put(key, value.sum()));
        report.put("custom", customMetrics);

        // Alertes actives
        report.put("alerts", Map.of(
                "high_memory", getMemoryUsageRatio() > HIGH_MEMORY_THRESHOLD,
                "high_cpu", getCpuUsage() > HIGH_CPU_THRESHOLD,
                "slow_queries", databaseQueryTimer.mean(TimeUnit.MILLISECONDS) > SLOW_QUERY_THRESHOLD_MS,
                "slow_api", apiResponseTimer.mean(TimeUnit.MILLISECONDS) > SLOW_API_THRESHOLD_MS));

        report.put("timestamp", LocalDateTime.now());

        return report;
    }

    /**
     * Health check pour Spring Boot Actuator
     */
    @Override
    public Health health() {
        try {
            Map<String, Object> details = new HashMap<>();

            double memoryRatio = getMemoryUsageRatio();
            double cpuUsage = getCpuUsage();
            double avgQueryTime = databaseQueryTimer.mean(TimeUnit.MILLISECONDS);
            double avgApiTime = apiResponseTimer.mean(TimeUnit.MILLISECONDS);

            details.put("memory_usage_ratio", memoryRatio);
            details.put("cpu_usage", cpuUsage);
            details.put("avg_query_time_ms", avgQueryTime);
            details.put("avg_api_time_ms", avgApiTime);

            // Déterminer le statut de santé
            if (memoryRatio > HIGH_MEMORY_THRESHOLD) {
                return Health.down()
                        .withDetails(details)
                        .withDetail("issue", "High memory usage")
                        .build();
            }

            if (cpuUsage > HIGH_CPU_THRESHOLD) {
                return Health.down()
                        .withDetails(details)
                        .withDetail("issue", "High CPU usage")
                        .build();
            }

            if (avgQueryTime > SLOW_QUERY_THRESHOLD_MS) {
                return Health.down()
                        .withDetails(details)
                        .withDetail("issue", "Slow database queries")
                        .build();
            }

            if (avgApiTime > SLOW_API_THRESHOLD_MS) {
                return Health.down()
                        .withDetails(details)
                        .withDetail("issue", "Slow API responses")
                        .build();
            }

            return Health.up()
                    .withDetails(details)
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
        customCounters.clear();
        customGauges.clear();
        log.info("Performance metrics reset");
    }

    /**
     * Export des métriques pour monitoring externe
     */
    public String exportMetricsForPrometheus() {
        StringBuilder metrics = new StringBuilder();

        // Format Prometheus
        metrics.append("# HELP bantuops_memory_usage_ratio Memory usage ratio\n");
        metrics.append("# TYPE bantuops_memory_usage_ratio gauge\n");
        metrics.append(String.format("bantuops_memory_usage_ratio %.4f\n", getMemoryUsageRatio()));

        metrics.append("# HELP bantuops_cpu_usage CPU usage\n");
        metrics.append("# TYPE bantuops_cpu_usage gauge\n");
        metrics.append(String.format("bantuops_cpu_usage %.4f\n", getCpuUsage()));

        metrics.append("# HELP bantuops_payroll_calculations_total Total payroll calculations\n");
        metrics.append("# TYPE bantuops_payroll_calculations_total counter\n");
        metrics.append(String.format("bantuops_payroll_calculations_total %.0f\n", payrollCalculationsCounter.count()));

        return metrics.toString();
    }
}