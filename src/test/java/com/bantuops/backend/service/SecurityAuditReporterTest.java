package com.bantuops.backend.service;

import com.bantuops.backend.dto.SecurityAlert;
import com.bantuops.backend.dto.SecurityAuditReport;
import com.bantuops.backend.dto.ThreatAnalysis;
import com.bantuops.backend.entity.AuditLog;
import com.bantuops.backend.repository.AuditLogRepository;
import com.bantuops.backend.repository.FieldLevelAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour SecurityAuditReporter
 */
@ExtendWith(MockitoExtension.class)
class SecurityAuditReporterTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private FieldLevelAuditRepository fieldLevelAuditRepository;

    @Mock
    private DataEncryptionService dataEncryptionService;

    @InjectMocks
    private SecurityAuditReporter securityAuditReporter;

    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private List<AuditLog> mockAuditLogs;

    @BeforeEach
    void setUp() {
        startDate = LocalDateTime.now().minusDays(7);
        endDate = LocalDateTime.now();

        // Création de logs d'audit de test
        mockAuditLogs = Arrays.asList(
                createMockAuditLog("user1", "LOGIN_SUCCESS", "User", false),
                createMockAuditLog("user2", "LOGIN_FAILURE", "User", false),
                createMockAuditLog("user2", "LOGIN_FAILURE", "User", false),
                createMockAuditLog("user2", "LOGIN_FAILURE", "User", false),
                createMockAuditLog("user2", "LOGIN_FAILURE", "User", false),
                createMockAuditLog("user2", "LOGIN_FAILURE", "User", false),
                createMockAuditLog("user2", "LOGIN_FAILURE", "User", false), // 6 échecs pour déclencher l'alerte
                createMockAuditLog("admin", "UNAUTHORIZED_ACCESS", "Employee", true),
                createMockAuditLog("user3", "ROLE_CHANGE", "User", false)
        );
    }

    @Test
    void shouldGenerateSecurityAuditReport() {
        // Given
        when(auditLogRepository.findByTimestampBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(mockAuditLogs);

        // When
        SecurityAuditReport report = securityAuditReporter.generateSecurityAuditReport(startDate, endDate);

        // Then
        assertThat(report).isNotNull();
        assertThat(report.getReportId()).isNotNull();
        assertThat(report.getStartDate()).isEqualTo(startDate);
        assertThat(report.getEndDate()).isEqualTo(endDate);
        assertThat(report.getSecurityEvents()).hasSize(9);
        assertThat(report.getSecurityAlerts()).isNotEmpty();
        assertThat(report.getThreatAnalysis()).isNotNull();
        assertThat(report.getSecurityMetrics()).isNotNull();
    }

    @Test
    void shouldDetectBruteForceAttack() {
        // Given
        when(auditLogRepository.findByTimestampBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(mockAuditLogs);

        // When
        SecurityAuditReport report = securityAuditReporter.generateSecurityAuditReport(startDate, endDate);

        // Then
        List<SecurityAlert> alerts = report.getSecurityAlerts();
        assertThat(alerts).isNotEmpty();
        
        boolean hasBruteForceAlert = alerts.stream()
                .anyMatch(alert -> alert.getAlertType() == SecurityAlert.AlertType.FAILED_LOGIN_ATTEMPT &&
                                 alert.getSeverity() == SecurityAlert.Severity.HIGH);
        
        assertThat(hasBruteForceAlert).isTrue();
    }

    @Test
    void shouldDetectUnauthorizedAccess() {
        // Given
        when(auditLogRepository.findByTimestampBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(mockAuditLogs);

        // When
        SecurityAuditReport report = securityAuditReporter.generateSecurityAuditReport(startDate, endDate);

        // Then
        List<SecurityAlert> alerts = report.getSecurityAlerts();
        
        boolean hasUnauthorizedAccessAlert = alerts.stream()
                .anyMatch(alert -> alert.getAlertType() == SecurityAlert.AlertType.UNAUTHORIZED_ACCESS &&
                                 alert.getSeverity() == SecurityAlert.Severity.CRITICAL);
        
        assertThat(hasUnauthorizedAccessAlert).isTrue();
    }

    @Test
    void shouldDetectPrivilegeEscalation() {
        // Given
        when(auditLogRepository.findByTimestampBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(mockAuditLogs);

        // When
        SecurityAuditReport report = securityAuditReporter.generateSecurityAuditReport(startDate, endDate);

        // Then
        List<SecurityAlert> alerts = report.getSecurityAlerts();
        
        boolean hasPrivilegeEscalationAlert = alerts.stream()
                .anyMatch(alert -> alert.getAlertType() == SecurityAlert.AlertType.PRIVILEGE_ESCALATION &&
                                 alert.getSeverity() == SecurityAlert.Severity.HIGH);
        
        assertThat(hasPrivilegeEscalationAlert).isTrue();
    }

    @Test
    void shouldCalculateThreatLevel() {
        // Given
        when(auditLogRepository.findByTimestampBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(mockAuditLogs);

        // When
        SecurityAuditReport report = securityAuditReporter.generateSecurityAuditReport(startDate, endDate);

        // Then
        ThreatAnalysis threatAnalysis = report.getThreatAnalysis();
        assertThat(threatAnalysis).isNotNull();
        assertThat(threatAnalysis.getOverallThreatLevel()).isNotNull();
        
        // Avec les alertes critiques et élevées, le niveau devrait être au moins MEDIUM
        assertThat(threatAnalysis.getOverallThreatLevel().getLevel()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void shouldGenerateSecurityMetrics() {
        // Given
        when(auditLogRepository.findByTimestampBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(mockAuditLogs);

        // When
        SecurityAuditReport report = securityAuditReporter.generateSecurityAuditReport(startDate, endDate);

        // Then
        assertThat(report.getSecurityMetrics()).isNotNull();
        assertThat(report.getSecurityMetrics()).containsKey("totalSecurityEvents");
        assertThat(report.getSecurityMetrics()).containsKey("totalSecurityAlerts");
        assertThat(report.getSecurityMetrics()).containsKey("alertsBySeverity");
        assertThat(report.getSecurityMetrics()).containsKey("alertsByType");
        assertThat(report.getSecurityMetrics()).containsKey("securityScore");
    }

    @Test
    void shouldGenerateSecurityRecommendations() {
        // Given
        when(auditLogRepository.findByTimestampBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(mockAuditLogs);

        // When
        SecurityAuditReport report = securityAuditReporter.generateSecurityAuditReport(startDate, endDate);

        // Then
        assertThat(report.getSecurityRecommendations()).isNotNull();
        assertThat(report.getSecurityRecommendations()).isNotEmpty();
        
        // Devrait contenir des recommandations pour les attaques par force brute
        boolean hasRecommendationForBruteForce = report.getSecurityRecommendations().stream()
                .anyMatch(rec -> rec.contains("verrouillage de compte") || rec.contains("authentification"));
        
        assertThat(hasRecommendationForBruteForce).isTrue();
    }

    @Test
    void shouldHandleEmptyAuditLogs() {
        // Given
        when(auditLogRepository.findByTimestampBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList());

        // When
        SecurityAuditReport report = securityAuditReporter.generateSecurityAuditReport(startDate, endDate);

        // Then
        assertThat(report).isNotNull();
        assertThat(report.getSecurityEvents()).isEmpty();
        assertThat(report.getSecurityAlerts()).isEmpty();
        assertThat(report.getThreatAnalysis().getOverallThreatLevel()).isEqualTo(ThreatAnalysis.ThreatLevel.LOW);
    }

    // ==================== MÉTHODES UTILITAIRES ====================

    private AuditLog createMockAuditLog(String userId, String action, String entityType, boolean sensitiveData) {
        AuditLog log = new AuditLog();
        log.setId(System.currentTimeMillis());
        log.setUserId(userId);
        log.setAction(AuditLog.AuditAction.valueOf(action));
        log.setEntityType(entityType);
        log.setEntityId(1L);
        log.setTimestamp(LocalDateTime.now().minusHours(1));
        log.setIpAddress("192.168.1.100");
        log.setUserAgent("Test User Agent");
        log.setSensitiveData(sensitiveData);
        
        if (sensitiveData) {
            log.setOldValues("sensitive_old_value");
            log.setNewValues("sensitive_new_value");
        }
        
        return log;
    }
}