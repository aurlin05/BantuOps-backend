package com.bantuops.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Service de monitoring des temps de réponse des APIs
 * Surveille et analyse les performances des endpoints REST
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiResponseTimeMonitor implements HandlerInterceptor {

    private final PerformanceMonitoringService performanceMonitoringService;
    
    // Stockage des métriques par endpoint
    private final Map<String, EndpointStats> endpointStatistics = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> slowEndpoints = new ConcurrentHashMap<>();
    private final List<SlowApiRecord> recentSlowApis = Collections.synchronizedList(new ArrayList<>());
    
    // Seuils de performance
    private static final long SLOW_API_THRESHOLD_MS = 2000;    // 2 secondes
    private static final long VERY_SLOW_API_THRESHOLD_MS = 5000; // 5 secondes
    private static final int MAX_SLOW_API_RECORDS = 100;
    
    // Attribut pour stocker le temps de début
    private static final String START_TIME_ATTRIBUTE = "api.start.time";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTRIBUTE, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                               Object handler, Exception ex) {
        Long startTime = (Long) request.getAttribute(START_TIME_ATTRIBUTE);
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            String endpoint = getEndpointKey(request);
            int statusCode = response.getStatus();
            
            recordApiCall(endpoint, duration, statusCode, request.getMethod());
        }
    }

    /**
     * Enregistrement d'un appel API
     */
    public void recordApiCall(String endpoint, long durationMs, int statusCode, String method) {
        // Mise à jour des statistiques de l'endpoint
        EndpointStats stats = endpointStatistics.computeIfAbsent(endpoint, k -> new EndpointStats());
        stats.recordCall(durationMs, statusCode, method);
        
        // Enregistrement dans le service de monitoring global
        performanceMonitoringService.recordApiRequest(durationMs);
        
        // Suivi des APIs lentes
        if (durationMs > SLOW_API_THRESHOLD_MS) {
            recordSlowApi(endpoint, durationMs, statusCode, method);
        }
        
        log.debug("API call recorded - Endpoint: {}, Duration: {}ms, Status: {}", 
                 endpoint, durationMs, statusCode);
    }

    /**
     * Enregistrement d'une API lente
     */
    private void recordSlowApi(String endpoint, long durationMs, int statusCode, String method) {
        // Incrémenter le compteur d'APIs lentes
        slowEndpoints.computeIfAbsent(endpoint, k -> new AtomicLong(0)).incrementAndGet();
        
        // Enregistrer les détails de l'API lente
        SlowApiRecord record = new SlowApiRecord(
            endpoint, 
            method,
            durationMs, 
            statusCode,
            LocalDateTime.now()
        );
        
        synchronized (recentSlowApis) {
            recentSlowApis.add(record);
            
            // Limiter le nombre d'enregistrements
            if (recentSlowApis.size() > MAX_SLOW_API_RECORDS) {
                recentSlowApis.remove(0);
            }
        }
        
        // Log d'alerte pour les APIs très lentes
        if (durationMs > VERY_SLOW_API_THRESHOLD_MS) {
            log.warn("Very slow API detected - Endpoint: {}, Method: {}, Duration: {}ms, Status: {}", 
                    endpoint, method, durationMs, statusCode);
        }
    }

    /**
     * Surveillance périodique des performances API (toutes les 5 minutes)
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void monitorApiPerformance() {
        try {
            analyzeEndpointPerformance();
            identifyProblematicEndpoints();
            cleanupOldRecords();
        } catch (Exception e) {
            log.error("Error monitoring API performance", e);
        }
    }

    /**
     * Analyse des performances par endpoint
     */
    private void analyzeEndpointPerformance() {
        for (Map.Entry<String, EndpointStats> entry : endpointStatistics.entrySet()) {
            String endpoint = entry.getKey();
            EndpointStats stats = entry.getValue();
            
            double avgResponseTime = stats.getAverageResponseTime();
            double errorRate = stats.getErrorRate();
            long totalCalls = stats.getTotalCalls();
            
            // Mise à jour des métriques de monitoring
            performanceMonitoringService.updateCustomGauge(
                "api.endpoint." + sanitizeEndpointName(endpoint) + ".avg.response.time", avgResponseTime);
            performanceMonitoringService.updateCustomGauge(
                "api.endpoint." + sanitizeEndpointName(endpoint) + ".error.rate", errorRate);
            performanceMonitoringService.updateCustomGauge(
                "api.endpoint." + sanitizeEndpointName(endpoint) + ".total.calls", totalCalls);
            
            // Alertes pour les endpoints problématiques
            if (avgResponseTime > SLOW_API_THRESHOLD_MS && totalCalls > 10) {
                log.warn("Slow endpoint detected - {}: avg {}ms over {} calls", 
                        endpoint, avgResponseTime, totalCalls);
                performanceMonitoringService.incrementCustomCounter("api.slow.endpoints");
            }
            
            if (errorRate > 0.1 && totalCalls > 10) { // Plus de 10% d'erreurs
                log.warn("High error rate endpoint - {}: {:.2f}% errors over {} calls", 
                        endpoint, errorRate * 100, totalCalls);
                performanceMonitoringService.incrementCustomCounter("api.high.error.endpoints");
            }
        }
    }

    /**
     * Identification des endpoints problématiques
     */
    private void identifyProblematicEndpoints() {
        // Top 5 des endpoints les plus lents
        List<Map.Entry<String, EndpointStats>> slowestEndpoints = endpointStatistics.entrySet().stream()
            .filter(entry -> entry.getValue().getTotalCalls() > 5)
            .sorted((e1, e2) -> Double.compare(
                e2.getValue().getAverageResponseTime(), 
                e1.getValue().getAverageResponseTime()))
            .limit(5)
            .collect(Collectors.toList());
        
        if (!slowestEndpoints.isEmpty()) {
            log.info("Top 5 slowest endpoints:");
            for (int i = 0; i < slowestEndpoints.size(); i++) {
                Map.Entry<String, EndpointStats> entry = slowestEndpoints.get(i);
                log.info("{}. {} - {:.2f}ms avg ({} calls)", 
                        i + 1, entry.getKey(), entry.getValue().getAverageResponseTime(), 
                        entry.getValue().getTotalCalls());
            }
        }
        
        // Top 5 des endpoints avec le plus d'erreurs
        List<Map.Entry<String, EndpointStats>> errorProneEndpoints = endpointStatistics.entrySet().stream()
            .filter(entry -> entry.getValue().getTotalCalls() > 5)
            .sorted((e1, e2) -> Double.compare(
                e2.getValue().getErrorRate(), 
                e1.getValue().getErrorRate()))
            .limit(5)
            .collect(Collectors.toList());
        
        if (!errorProneEndpoints.isEmpty() && errorProneEndpoints.get(0).getValue().getErrorRate() > 0) {
            log.info("Top 5 error-prone endpoints:");
            for (int i = 0; i < errorProneEndpoints.size(); i++) {
                Map.Entry<String, EndpointStats> entry = errorProneEndpoints.get(i);
                if (entry.getValue().getErrorRate() > 0) {
                    log.info("{}. {} - {:.2f}% errors ({} calls)", 
                            i + 1, entry.getKey(), entry.getValue().getErrorRate() * 100, 
                            entry.getValue().getTotalCalls());
                }
            }
        }
    }

    /**
     * Nettoyage des anciens enregistrements
     */
    private void cleanupOldRecords() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        
        synchronized (recentSlowApis) {
            recentSlowApis.removeIf(record -> record.getTimestamp().isBefore(cutoff));
        }
        
        log.debug("Cleaned up old slow API records, remaining: {}", recentSlowApis.size());
    }

    /**
     * Génération de la clé d'endpoint
     */
    private String getEndpointKey(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        
        // Normaliser le chemin en remplaçant les IDs par des placeholders
        path = path.replaceAll("/\\d+", "/{id}");
        path = path.replaceAll("/[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}", "/{uuid}");
        
        return method + " " + path;
    }

    /**
     * Sanitisation du nom d'endpoint pour les métriques
     */
    private String sanitizeEndpointName(String endpoint) {
        return endpoint.replaceAll("[^a-zA-Z0-9._-]", "_").toLowerCase();
    }

    /**
     * Obtention des statistiques d'endpoint
     */
    public Map<String, EndpointStats> getEndpointStatistics() {
        return new HashMap<>(endpointStatistics);
    }

    /**
     * Obtention des APIs lentes récentes
     */
    public List<SlowApiRecord> getRecentSlowApis() {
        synchronized (recentSlowApis) {
            return new ArrayList<>(recentSlowApis);
        }
    }

    /**
     * Rapport de performance des APIs
     */
    public Map<String, Object> getApiPerformanceReport() {
        Map<String, Object> report = new HashMap<>();
        
        // Statistiques générales
        long totalCalls = endpointStatistics.values().stream()
            .mapToLong(EndpointStats::getTotalCalls)
            .sum();
        
        double avgResponseTime = endpointStatistics.values().stream()
            .mapToDouble(EndpointStats::getAverageResponseTime)
            .average()
            .orElse(0.0);
        
        double overallErrorRate = endpointStatistics.values().stream()
            .mapToDouble(stats -> stats.getErrorCount())
            .sum() / Math.max(totalCalls, 1);
        
        report.put("total_api_calls", totalCalls);
        report.put("avg_response_time_ms", avgResponseTime);
        report.put("overall_error_rate", overallErrorRate);
        report.put("slow_apis_count", recentSlowApis.size());
        
        // Top endpoints par volume
        Map<String, Long> topEndpointsByVolume = endpointStatistics.entrySet().stream()
            .sorted((e1, e2) -> Long.compare(e2.getValue().getTotalCalls(), e1.getValue().getTotalCalls()))
            .limit(10)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().getTotalCalls(),
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));
        report.put("top_endpoints_by_volume", topEndpointsByVolume);
        
        // Top endpoints par temps de réponse
        Map<String, Double> topEndpointsByResponseTime = endpointStatistics.entrySet().stream()
            .filter(entry -> entry.getValue().getTotalCalls() > 5)
            .sorted((e1, e2) -> Double.compare(e2.getValue().getAverageResponseTime(), e1.getValue().getAverageResponseTime()))
            .limit(10)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().getAverageResponseTime(),
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));
        report.put("top_endpoints_by_response_time", topEndpointsByResponseTime);
        
        // APIs lentes par endpoint
        Map<String, Long> slowApisByEndpoint = new HashMap<>();
        slowEndpoints.forEach((endpoint, count) -> slowApisByEndpoint.put(endpoint, count.get()));
        report.put("slow_apis_by_endpoint", slowApisByEndpoint);
        
        report.put("timestamp", LocalDateTime.now());
        
        return report;
    }

    /**
     * Recommandations d'optimisation pour les APIs
     */
    public List<String> getApiOptimizationRecommendations() {
        List<String> recommendations = new ArrayList<>();
        
        // Analyser les endpoints lents
        endpointStatistics.entrySet().stream()
            .filter(entry -> entry.getValue().getAverageResponseTime() > SLOW_API_THRESHOLD_MS)
            .filter(entry -> entry.getValue().getTotalCalls() > 10)
            .forEach(entry -> recommendations.add(
                String.format("Optimize endpoint %s - average response time: %.2fms", 
                             entry.getKey(), entry.getValue().getAverageResponseTime())));
        
        // Analyser les endpoints avec beaucoup d'erreurs
        endpointStatistics.entrySet().stream()
            .filter(entry -> entry.getValue().getErrorRate() > 0.05) // Plus de 5% d'erreurs
            .filter(entry -> entry.getValue().getTotalCalls() > 10)
            .forEach(entry -> recommendations.add(
                String.format("Fix errors in endpoint %s - error rate: %.2f%%", 
                             entry.getKey(), entry.getValue().getErrorRate() * 100)));
        
        // Analyser les endpoints très utilisés
        endpointStatistics.entrySet().stream()
            .filter(entry -> entry.getValue().getTotalCalls() > 1000)
            .filter(entry -> entry.getValue().getAverageResponseTime() > 500) // Plus de 500ms
            .forEach(entry -> recommendations.add(
                String.format("Consider caching for high-volume endpoint %s - %d calls, %.2fms avg", 
                             entry.getKey(), entry.getValue().getTotalCalls(), 
                             entry.getValue().getAverageResponseTime())));
        
        if (recommendations.isEmpty()) {
            recommendations.add("No specific API optimization recommendations at this time");
        }
        
        return recommendations;
    }

    /**
     * Réinitialisation des statistiques
     */
    public void resetStatistics() {
        endpointStatistics.clear();
        slowEndpoints.clear();
        recentSlowApis.clear();
        log.info("API performance statistics reset");
    }

    /**
     * Classe pour les statistiques d'endpoint
     */
    public static class EndpointStats {
        private final AtomicLong totalCalls = new AtomicLong(0);
        private final AtomicLong totalResponseTime = new AtomicLong(0);
        private final AtomicLong errorCount = new AtomicLong(0);
        private final Map<String, AtomicLong> methodCounts = new ConcurrentHashMap<>();
        private final Map<Integer, AtomicLong> statusCounts = new ConcurrentHashMap<>();
        private volatile long minResponseTime = Long.MAX_VALUE;
        private volatile long maxResponseTime = Long.MIN_VALUE;

        public void recordCall(long responseTimeMs, int statusCode, String method) {
            totalCalls.incrementAndGet();
            totalResponseTime.addAndGet(responseTimeMs);
            
            if (statusCode >= 400) {
                errorCount.incrementAndGet();
            }
            
            methodCounts.computeIfAbsent(method, k -> new AtomicLong(0)).incrementAndGet();
            statusCounts.computeIfAbsent(statusCode, k -> new AtomicLong(0)).incrementAndGet();
            
            // Mise à jour thread-safe des min/max
            synchronized (this) {
                if (responseTimeMs < minResponseTime) {
                    minResponseTime = responseTimeMs;
                }
                if (responseTimeMs > maxResponseTime) {
                    maxResponseTime = responseTimeMs;
                }
            }
        }

        public long getTotalCalls() { return totalCalls.get(); }
        public long getErrorCount() { return errorCount.get(); }
        public double getAverageResponseTime() { 
            long calls = totalCalls.get();
            return calls > 0 ? (double) totalResponseTime.get() / calls : 0.0;
        }
        public double getErrorRate() {
            long calls = totalCalls.get();
            return calls > 0 ? (double) errorCount.get() / calls : 0.0;
        }
        public long getMinResponseTime() { return minResponseTime == Long.MAX_VALUE ? 0 : minResponseTime; }
        public long getMaxResponseTime() { return maxResponseTime == Long.MIN_VALUE ? 0 : maxResponseTime; }
        public Map<String, Long> getMethodCounts() {
            Map<String, Long> result = new HashMap<>();
            methodCounts.forEach((method, count) -> result.put(method, count.get()));
            return result;
        }
        public Map<Integer, Long> getStatusCounts() {
            Map<Integer, Long> result = new HashMap<>();
            statusCounts.forEach((status, count) -> result.put(status, count.get()));
            return result;
        }
    }

    /**
     * Classe pour les enregistrements d'APIs lentes
     */
    public static class SlowApiRecord {
        private final String endpoint;
        private final String method;
        private final long responseTimeMs;
        private final int statusCode;
        private final LocalDateTime timestamp;

        public SlowApiRecord(String endpoint, String method, long responseTimeMs, int statusCode, LocalDateTime timestamp) {
            this.endpoint = endpoint;
            this.method = method;
            this.responseTimeMs = responseTimeMs;
            this.statusCode = statusCode;
            this.timestamp = timestamp;
        }

        public String getEndpoint() { return endpoint; }
        public String getMethod() { return method; }
        public long getResponseTimeMs() { return responseTimeMs; }
        public int getStatusCode() { return statusCode; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
}