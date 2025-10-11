package com.bantuops.backend.service;

import com.bantuops.backend.dto.SecurityAlert;
import com.bantuops.backend.repository.AuditLogRepository;
import com.bantuops.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour SecurityAlertService
 */
@ExtendWith(MockitoExtension.class)
class SecurityAlertServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private SuspiciousActivityDetector suspiciousActivityDetector;

    @Mock
    private SecurityViolationHandler securityViolationHandler;

    @Mock
    private AutomaticSecurityResponseSystem automaticResponseSystem;

    @InjectMocks
    private SecurityAlertService securityAlertService;

    @BeforeEach
    void setUp() {
        // Configuration des mocks par défaut
        when(suspiciousActivityDetector.analyzeSuspiciousActivity(any(SecurityAlert.class)))
            .thenReturn(false);
    }

    @Test
    void shouldCreateSecurityAlert() {
        // Given
        String userId = "testuser";
        String description = "Test security alert";
        Map<String, Object> metadata = Map.of("test", "value");

        // When
        securityAlertService.createSecurityAlert(
            SecurityAlert.AlertType.FAILED_LOGIN_ATTEMPT,
            userId,
            description,
            metadata
        );

        // Then
        verify(auditLogRepository).save(any());
        verify(suspiciousActivityDetector).analyzeSuspiciousActivity(any(SecurityAlert.class));
        verify(securityViolationHandler).handleSecurityViolation(any(SecurityAlert.class));
        verify(eventPublisher).publishEvent(any(SecurityAlert.class));
    }

    @Test
    void shouldAlertFailedLogin() {
        // Given
        String username = "testuser";
        String ipAddress = "192.168.1.1";
        String userAgent = "Mozilla/5.0";

        // When
        securityAlertService.alertFailedLogin(username, ipAddress, userAgent);

        // Then
        verify(auditLogRepository).save(any());
        verify(suspiciousActivityDetector).analyzeSuspiciousActivity(any(SecurityAlert.class));
        verify(securityViolationHandler).handleSecurityViolation(any(SecurityAlert.class));
    }

    @Test
    void shouldAlertUnauthorizedAccess() {
        // Given
        String userId = "testuser";
        String resource = "/api/admin/users";
        String action = "READ";

        // When
        securityAlertService.alertUnauthorizedAccess(userId, resource, action);

        // Then
        verify(auditLogRepository).save(any());
        verify(suspiciousActivityDetector).analyzeSuspiciousActivity(any(SecurityAlert.class));
        verify(securityViolationHandler).handleSecurityViolation(any(SecurityAlert.class));
    }

    @Test
    void shouldTriggerAutomaticResponseForCriticalAlert() {
        // Given
        when(suspiciousActivityDetector.analyzeSuspiciousActivity(any(SecurityAlert.class)))
            .thenReturn(true);

        String userId = "testuser";
        String description = "Critical security breach";
        Map<String, Object> metadata = Map.of("severity", "critical");

        // When
        securityAlertService.createSecurityAlert(
            SecurityAlert.AlertType.SECURITY_BREACH,
            userId,
            description,
            metadata
        );

        // Then
        verify(automaticResponseSystem).triggerAutomaticResponse(any(SecurityAlert.class));
    }

    @Test
    void shouldAlertSensitiveDataModification() {
        // Given
        String userId = "testuser";
        String entityType = "Employee";
        Long entityId = 1L;
        String fieldName = "salary";
        Object oldValue = "50000";
        Object newValue = "60000";

        // When
        securityAlertService.alertSensitiveDataModification(
            userId, entityType, entityId, fieldName, oldValue, newValue);

        // Then
        verify(auditLogRepository).save(any());
        verify(suspiciousActivityDetector).analyzeSuspiciousActivity(any(SecurityAlert.class));
        verify(securityViolationHandler).handleSecurityViolation(any(SecurityAlert.class));
    }

    @Test
    void shouldAlertBusinessRuleViolation() {
        // Given
        String userId = "testuser";
        String ruleName = "MaxSalaryRule";
        String violation = "Salary exceeds maximum allowed";

        // When
        securityAlertService.alertBusinessRuleViolation(userId, ruleName, violation);

        // Then
        verify(auditLogRepository).save(any());
        verify(suspiciousActivityDetector).analyzeSuspiciousActivity(any(SecurityAlert.class));
        verify(securityViolationHandler).handleSecurityViolation(any(SecurityAlert.class));
    }

    @Test
    void shouldResolveSecurityAlert() {
        // Given
        Long alertId = 1L;
        String resolvedBy = "admin";
        String resolution = "False positive - user authorized";

        // When
        securityAlertService.resolveSecurityAlert(alertId, resolvedBy, resolution);

        // Then
        verify(auditLogRepository).findById(alertId);
    }
}