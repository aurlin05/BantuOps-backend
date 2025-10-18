package com.bantuops.backend.config;

import com.bantuops.backend.service.CachedCalculationService;
import com.bantuops.backend.service.DistributedSessionService;
import com.bantuops.backend.service.SessionManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        "bantuops.node.id=test-node-1"
})
class RedisConfigTest {

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private CachedCalculationService cachedCalculationService;

    @Autowired
    private DistributedSessionService distributedSessionService;

    @Autowired
    private SessionManagementService sessionManagementService;

    @Test
    void shouldConfigureCacheManager() {
        assertThat(cacheManager).isNotNull();

        // Verify expected caches are configured
        assertThat(cacheManager.getCacheNames()).contains(
                "employees",
                "payroll-calculations",
                "tax-rates",
                "attendance-rules",
                "user-permissions",
                "financial-reports",
                "invoices",
                "system-config",
                "frequent-calculations",
                "session-metadata",
                "business-rules",
                "audit-cache");
    }

    @Test
    void shouldConfigureRedisTemplate() {
        assertThat(redisTemplate).isNotNull();
        assertThat(redisTemplate.getConnectionFactory()).isNotNull();
    }

    @Test
    void shouldCacheSystemConfiguration() {
        // When - First call should calculate and cache
        Object vatRate1 = cachedCalculationService.getSystemConfigCached("default.vat.rate");

        // Then - Second call should return cached value
        Object vatRate2 = cachedCalculationService.getSystemConfigCached("default.vat.rate");

        assertThat(vatRate1).isEqualTo(vatRate2);
        assertThat(vatRate1).isEqualTo(new BigDecimal("0.18")); // 18% VAT rate for Senegal
    }

    @Test
    void shouldCacheBusinessRules() {
        // When
        Object vatRate = cachedCalculationService.getSystemConfigCached("default.vat.rate");
        Object minimumWage = cachedCalculationService.getSystemConfigCached("minimum.wage");
        Object maxOvertimeHours = cachedCalculationService.getSystemConfigCached("max.overtime.hours");

        // Then
        assertThat(vatRate).isNotNull();
        assertThat(minimumWage).isNotNull();
        assertThat(maxOvertimeHours).isNotNull();

        assertThat(vatRate).isEqualTo(new BigDecimal("0.18"));
        assertThat(minimumWage).isEqualTo(new BigDecimal("60000"));
        assertThat(maxOvertimeHours).isEqualTo(8);
    }

    @Test
    void shouldManageDistributedSessions() {
        // Given
        String sessionId = "test-session-123";
        Long userId = 1L;
        String nodeId = "test-node-1";

        // When
        distributedSessionService.registerDistributedSession(sessionId, userId, nodeId);

        // Then
        assertThat(distributedSessionService.isSessionRegistered(sessionId)).isTrue();
        assertThat(distributedSessionService.getSessionNode(sessionId)).isEqualTo(nodeId);

        // Cleanup
        distributedSessionService.unregisterDistributedSession(sessionId, userId);
        assertThat(distributedSessionService.isSessionRegistered(sessionId)).isFalse();
    }

    @Test
    void shouldTrackUserSessions() {
        // Given
        String sessionId = "user-session-456";
        Long userId = 2L;

        // When
        sessionManagementService.trackUserSession(userId, sessionId, "test-agent", "127.0.0.1");

        // Then
        assertThat(sessionManagementService.isSessionActive(sessionId)).isTrue();
        assertThat(sessionManagementService.getUserActiveSessions(userId)).contains(sessionId);

        // Cleanup
        sessionManagementService.untrackUserSession(userId, sessionId);
        assertThat(sessionManagementService.isSessionActive(sessionId)).isFalse();
    }
}