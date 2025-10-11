package com.bantuops.backend.audit;

import com.bantuops.backend.aspect.ApiAuditInterceptor;
import com.bantuops.backend.config.RequestResponseLoggingFilter;
import com.bantuops.backend.security.SecurityAuditEventListener;
import com.bantuops.backend.service.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test d'intégration pour vérifier que tous les composants d'audit sont correctement configurés
 * Conforme aux exigences 7.4, 7.5, 7.6, 6.1, 6.2 pour l'audit des APIs
 */
@SpringBootTest
@ActiveProfiles("test")
class ApiAuditIntegrationTest {

    @Autowired
    private AuditService auditService;

    @Autowired
    private ApiAuditInterceptor apiAuditInterceptor;

    @Autowired
    private RequestResponseLoggingFilter requestResponseLoggingFilter;

    @Autowired
    private SecurityAuditEventListener securityAuditEventListener;

    @Test
    void shouldLoadAllAuditComponents() {
        // Vérifier que tous les composants d'audit sont correctement chargés
        assertThat(auditService).isNotNull();
        assertThat(apiAuditInterceptor).isNotNull();
        assertThat(requestResponseLoggingFilter).isNotNull();
        assertThat(securityAuditEventListener).isNotNull();
    }

    @Test
    void shouldLogApiCallSuccessfully() {
        // Test de base pour vérifier que l'audit fonctionne
        ApiAuditInterceptor.ApiAuditInfo auditInfo = ApiAuditInterceptor.ApiAuditInfo.builder()
            .traceId("test-trace-123")
            .method("GET")
            .uri("/api/test")
            .username("testuser")
            .responseStatus(200)
            .duration(150L)
            .success(true)
            .build();

        // Cette méthode ne devrait pas lever d'exception
        auditService.logApiCall(auditInfo);
    }

    @Test
    void shouldLogSecurityEventSuccessfully() {
        // Test de base pour vérifier que l'audit de sécurité fonctionne
        auditService.logSuccessfulLogin("testuser", "192.168.1.1", null);
        auditService.logFailedLogin("testuser", "192.168.1.1", "Bad credentials", null);
    }

    @Test
    void shouldLogPerformanceMetricsSuccessfully() {
        // Test de base pour vérifier que l'audit de performance fonctionne
        auditService.logPerformanceMetrics("TestService.testMethod", null);
        auditService.logPerformanceAlert("SLOW_METHOD", "Method is slow", null);
    }

    @Test
    void shouldHandleIpBlocking() {
        // Test de la logique de blocage d'IP
        String testIp = "192.168.1.100";
        
        // Initialement, l'IP ne devrait pas être bloquée
        assertThat(auditService.shouldBlockIpAddress(testIp)).isFalse();
        
        // Simuler plusieurs échecs de connexion
        for (int i = 0; i < 6; i++) {
            auditService.logBadCredentialsAttempt("testuser", testIp, null);
        }
        
        // Maintenant l'IP devrait être bloquée
        assertThat(auditService.shouldBlockIpAddress(testIp)).isTrue();
    }
}