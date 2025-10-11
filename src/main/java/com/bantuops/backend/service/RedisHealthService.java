package com.bantuops.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisHealthService {

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisConnectionFactory connectionFactory;

    private static final String HEALTH_CHECK_KEY = "bantuops:health_check";

    /**
     * Perform Redis health check
     */
    public boolean isRedisHealthy() {
        try {
            // Test basic connectivity
            String testValue = "health_check_" + Instant.now().toEpochMilli();
            
            // Write test
            redisTemplate.opsForValue().set(HEALTH_CHECK_KEY, testValue, Duration.ofSeconds(10));
            
            // Read test
            String retrievedValue = redisTemplate.opsForValue().get(HEALTH_CHECK_KEY);
            
            // Cleanup
            redisTemplate.delete(HEALTH_CHECK_KEY);
            
            boolean isHealthy = testValue.equals(retrievedValue);
            
            if (isHealthy) {
                log.debug("Redis health check passed");
            } else {
                log.warn("Redis health check failed: value mismatch");
            }
            
            return isHealthy;
            
        } catch (Exception e) {
            log.error("Redis health check failed", e);
            return false;
        }
    }

    /**
     * Get Redis connection info
     */
    public RedisConnectionInfo getConnectionInfo() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            
            var info = connection.info();
            var serverInfo = info != null ? info.getProperty("redis_version") : "unknown";
            
            return RedisConnectionInfo.builder()
                    .isConnected(true)
                    .serverVersion(serverInfo)
                    .connectionCount(getConnectionCount())
                    .memoryUsage(getMemoryUsage())
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to get Redis connection info", e);
            return RedisConnectionInfo.builder()
                    .isConnected(false)
                    .error(e.getMessage())
                    .build();
        }
    }

    /**
     * Get Redis memory usage information
     */
    public String getMemoryUsage() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            var info = connection.info("memory");
            return info != null ? info.getProperty("used_memory_human") : "unknown";
        } catch (Exception e) {
            log.debug("Failed to get Redis memory usage", e);
            return "unknown";
        }
    }

    /**
     * Get number of connected clients
     */
    public String getConnectionCount() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            var info = connection.info("clients");
            return info != null ? info.getProperty("connected_clients") : "unknown";
        } catch (Exception e) {
            log.debug("Failed to get Redis connection count", e);
            return "unknown";
        }
    }

    /**
     * Test cache performance
     */
    public CachePerformanceMetrics testCachePerformance() {
        long startTime = System.currentTimeMillis();
        
        try {
            // Test write performance
            long writeStartTime = System.currentTimeMillis();
            for (int i = 0; i < 100; i++) {
                redisTemplate.opsForValue().set("perf_test_" + i, "test_value_" + i, Duration.ofMinutes(1));
            }
            long writeTime = System.currentTimeMillis() - writeStartTime;

            // Test read performance
            long readStartTime = System.currentTimeMillis();
            for (int i = 0; i < 100; i++) {
                redisTemplate.opsForValue().get("perf_test_" + i);
            }
            long readTime = System.currentTimeMillis() - readStartTime;

            // Cleanup
            for (int i = 0; i < 100; i++) {
                redisTemplate.delete("perf_test_" + i);
            }

            long totalTime = System.currentTimeMillis() - startTime;

            return CachePerformanceMetrics.builder()
                    .writeTime(writeTime)
                    .readTime(readTime)
                    .totalTime(totalTime)
                    .operationsPerSecond(200.0 / (totalTime / 1000.0))
                    .build();

        } catch (Exception e) {
            log.error("Cache performance test failed", e);
            return CachePerformanceMetrics.builder()
                    .error(e.getMessage())
                    .build();
        }
    }

    // Data classes for health check results
    public static class RedisConnectionInfo {
        private boolean isConnected;
        private String serverVersion;
        private String connectionCount;
        private String memoryUsage;
        private String error;

        public static RedisConnectionInfoBuilder builder() {
            return new RedisConnectionInfoBuilder();
        }

        public static class RedisConnectionInfoBuilder {
            private boolean isConnected;
            private String serverVersion;
            private String connectionCount;
            private String memoryUsage;
            private String error;

            public RedisConnectionInfoBuilder isConnected(boolean isConnected) {
                this.isConnected = isConnected;
                return this;
            }

            public RedisConnectionInfoBuilder serverVersion(String serverVersion) {
                this.serverVersion = serverVersion;
                return this;
            }

            public RedisConnectionInfoBuilder connectionCount(String connectionCount) {
                this.connectionCount = connectionCount;
                return this;
            }

            public RedisConnectionInfoBuilder memoryUsage(String memoryUsage) {
                this.memoryUsage = memoryUsage;
                return this;
            }

            public RedisConnectionInfoBuilder error(String error) {
                this.error = error;
                return this;
            }

            public RedisConnectionInfo build() {
                RedisConnectionInfo info = new RedisConnectionInfo();
                info.isConnected = this.isConnected;
                info.serverVersion = this.serverVersion;
                info.connectionCount = this.connectionCount;
                info.memoryUsage = this.memoryUsage;
                info.error = this.error;
                return info;
            }
        }

        // Getters
        public boolean isConnected() { return isConnected; }
        public String getServerVersion() { return serverVersion; }
        public String getConnectionCount() { return connectionCount; }
        public String getMemoryUsage() { return memoryUsage; }
        public String getError() { return error; }
    }

    public static class CachePerformanceMetrics {
        private long writeTime;
        private long readTime;
        private long totalTime;
        private double operationsPerSecond;
        private String error;

        public static CachePerformanceMetricsBuilder builder() {
            return new CachePerformanceMetricsBuilder();
        }

        public static class CachePerformanceMetricsBuilder {
            private long writeTime;
            private long readTime;
            private long totalTime;
            private double operationsPerSecond;
            private String error;

            public CachePerformanceMetricsBuilder writeTime(long writeTime) {
                this.writeTime = writeTime;
                return this;
            }

            public CachePerformanceMetricsBuilder readTime(long readTime) {
                this.readTime = readTime;
                return this;
            }

            public CachePerformanceMetricsBuilder totalTime(long totalTime) {
                this.totalTime = totalTime;
                return this;
            }

            public CachePerformanceMetricsBuilder operationsPerSecond(double operationsPerSecond) {
                this.operationsPerSecond = operationsPerSecond;
                return this;
            }

            public CachePerformanceMetricsBuilder error(String error) {
                this.error = error;
                return this;
            }

            public CachePerformanceMetrics build() {
                CachePerformanceMetrics metrics = new CachePerformanceMetrics();
                metrics.writeTime = this.writeTime;
                metrics.readTime = this.readTime;
                metrics.totalTime = this.totalTime;
                metrics.operationsPerSecond = this.operationsPerSecond;
                metrics.error = this.error;
                return metrics;
            }
        }

        // Getters
        public long getWriteTime() { return writeTime; }
        public long getReadTime() { return readTime; }
        public long getTotalTime() { return totalTime; }
        public double getOperationsPerSecond() { return operationsPerSecond; }
        public String getError() { return error; }
    }
}