package com.bantuops.backend.service;

import com.bantuops.backend.dto.SecurityAlert;
import com.bantuops.backend.entity.AuditLog;
import com.bantuops.backend.entity.User;
import com.bantuops.backend.repository.AuditLogRepository;
import com.bantuops.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service de gestion des alertes de sécurité en temps réel
 * Gère la détection, l'enregistrement et la notification des événements de sécurité
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityAlertService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SuspiciousActivityDetector suspiciousActivityDetector;
    private final SecurityViolationHandler securityViolationHandler;
    private final AutomaticSecurityResponseSystem automaticResponseSystem;

    // Cache des alertes actives pour éviter les doublons
    private final Map<String, LocalDateTime> activeAlerts = new ConcurrentHashMap<>();

    /**
     * Crée et traite une alerte de sécurité
     */
    @Async
    @Transactional
    public void createSecurityAlert(SecurityAlert.AlertType alertType, String userId, 
                                  String description, Map<String, Object> metadata) {
        try {
            log.warn("Alerte de sécurité détectée: {} pour utilisateur: {}", alertType, userId);

            SecurityAlert alert = SecurityAlert.builder()
                .alertType(alertType)
                .userId(userId)
                .description(description)
                .metadata(metadata)
                .timestamp(LocalDateTime.now())
                .severity(determineSeverity(alertType))
                .ipAddress(extractIpAddress(metadata))
                .userAgent(extractUserAgent(metadata))
                .build();

            // Vérifier si cette alerte n'est pas un doublon récent
            String alertKey = generateAlertKey(alert);
            if (isDuplicateAlert(alertKey)) {
                log.debug("Alerte dupliquée ignorée: {}", alertKey);
                return;
            }

            // Enregistrer l'alerte
            saveSecurityAlert(alert);

            // Analyser l'activité suspecte
            boolean isSuspicious = suspiciousActivityDetector.analyzeSuspiciousActivity(alert);
            if (isSuspicious) {
                alert.setSeverity(SecurityAlert.Severity.HIGH);
                log.error("Activité suspecte confirmée pour l'alerte: {}", alert.getId());
            }

            // Traiter la violation de sécurité
            securityViolationHandler.handleSecurityViolation(alert);

            // Déclencher une réponse automatique si nécessaire
            if (alert.getSeverity() == SecurityAlert.Severity.CRITICAL) {
                automaticResponseSystem.triggerAutomaticResponse(alert);
            }

            // Publier l'événement pour les listeners
            eventPublisher.publishEvent(alert);

            // Marquer l'alerte comme active
            activeAlerts.put(alertKey, LocalDateTime.now());

        } catch (Exception e) {
            log.error("Erreur lors de la création de l'alerte de sécurité", e);
        }
    }

    /**
     * Alerte pour tentative de connexion échouée
     */
    public void alertFailedLogin(String username, String ipAddress, String userAgent) {
        Map<String, Object> metadata = Map.of(
            "username", username,
            "ipAddress", ipAddress,
            "userAgent", userAgent,
            "attemptTime", LocalDateTime.now()
        );

        createSecurityAlert(
            SecurityAlert.AlertType.FAILED_LOGIN_ATTEMPT,
            username,
            "Tentative de connexion échouée pour l'utilisateur: " + username,
            metadata
        );
    }

    /**
     * Alerte pour accès non autorisé
     */
    public void alertUnauthorizedAccess(String userId, String resource, String action) {
        Map<String, Object> metadata = Map.of(
            "resource", resource,
            "action", action,
            "timestamp", LocalDateTime.now()
        );

        createSecurityAlert(
            SecurityAlert.AlertType.UNAUTHORIZED_ACCESS,
            userId,
            String.format("Tentative d'accès non autorisé à %s (action: %s)", resource, action),
            metadata
        );
    }

    /**
     * Alerte pour modification de données sensibles
     */
    public void alertSensitiveDataModification(String userId, String entityType, Long entityId, 
                                             String fieldName, Object oldValue, Object newValue) {
        Map<String, Object> metadata = Map.of(
            "entityType", entityType,
            "entityId", entityId,
            "fieldName", fieldName,
            "oldValue", maskSensitiveValue(oldValue),
            "newValue", maskSensitiveValue(newValue),
            "timestamp", LocalDateTime.now()
        );

        createSecurityAlert(
            SecurityAlert.AlertType.SENSITIVE_DATA_MODIFICATION,
            userId,
            String.format("Modification de données sensibles: %s.%s", entityType, fieldName),
            metadata
        );
    }

    /**
     * Alerte pour activité anormale
     */
    public void alertAbnormalActivity(String userId, String activityType, Map<String, Object> details) {
        Map<String, Object> metadata = Map.of(
            "activityType", activityType,
            "details", details,
            "timestamp", LocalDateTime.now()
        );

        createSecurityAlert(
            SecurityAlert.AlertType.ABNORMAL_ACTIVITY,
            userId,
            "Activité anormale détectée: " + activityType,
            metadata
        );
    }

    /**
     * Alerte pour violation de règles métier
     */
    public void alertBusinessRuleViolation(String userId, String ruleName, String violation) {
        Map<String, Object> metadata = Map.of(
            "ruleName", ruleName,
            "violation", violation,
            "timestamp", LocalDateTime.now()
        );

        createSecurityAlert(
            SecurityAlert.AlertType.BUSINESS_RULE_VIOLATION,
            userId,
            String.format("Violation de règle métier: %s - %s", ruleName, violation),
            metadata
        );
    }

    /**
     * Récupère les alertes de sécurité récentes
     */
    @Transactional(readOnly = true)
    public List<SecurityAlert> getRecentSecurityAlerts(int limit) {
        return auditLogRepository.findRecentSecurityAlerts(limit);
    }

    /**
     * Récupère les alertes par utilisateur
     */
    @Transactional(readOnly = true)
    public List<SecurityAlert> getSecurityAlertsByUser(String userId, LocalDateTime since) {
        return auditLogRepository.findSecurityAlertsByUser(userId, since);
    }

    /**
     * Marque une alerte comme résolue
     */
    @Transactional
    public void resolveSecurityAlert(Long alertId, String resolvedBy, String resolution) {
        auditLogRepository.findById(alertId).ifPresent(auditLog -> {
            auditLog.setResolved(true);
            auditLog.setResolvedBy(resolvedBy);
            auditLog.setResolution(resolution);
            auditLog.setResolvedAt(LocalDateTime.now());
            auditLogRepository.save(auditLog);
            
            log.info("Alerte de sécurité {} résolue par {}: {}", alertId, resolvedBy, resolution);
        });
    }

    /**
     * Nettoie les alertes anciennes du cache
     */
    @Async
    public void cleanupOldAlerts() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
        activeAlerts.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    private SecurityAlert.Severity determineSeverity(SecurityAlert.AlertType alertType) {
        return switch (alertType) {
            case FAILED_LOGIN_ATTEMPT -> SecurityAlert.Severity.LOW;
            case UNAUTHORIZED_ACCESS, BUSINESS_RULE_VIOLATION -> SecurityAlert.Severity.MEDIUM;
            case SENSITIVE_DATA_MODIFICATION, ABNORMAL_ACTIVITY -> SecurityAlert.Severity.HIGH;
            case SECURITY_BREACH, SYSTEM_COMPROMISE -> SecurityAlert.Severity.CRITICAL;
        };
    }

    private void saveSecurityAlert(SecurityAlert alert) {
        AuditLog auditLog = AuditLog.builder()
            .entityType("SECURITY_ALERT")
            .action(alert.getAlertType().name())
            .userId(alert.getUserId())
            .description(alert.getDescription())
            .metadata(alert.getMetadata().toString())
            .ipAddress(alert.getIpAddress())
            .userAgent(alert.getUserAgent())
            .severity(alert.getSeverity().name())
            .timestamp(alert.getTimestamp())
            .build();

        auditLogRepository.save(auditLog);
        alert.setId(auditLog.getId());
    }

    private String generateAlertKey(SecurityAlert alert) {
        return String.format("%s_%s_%s", 
            alert.getAlertType(), 
            alert.getUserId(), 
            alert.getTimestamp().withSecond(0).withNano(0));
    }

    private boolean isDuplicateAlert(String alertKey) {
        LocalDateTime lastAlert = activeAlerts.get(alertKey);
        if (lastAlert == null) {
            return false;
        }
        
        // Considérer comme doublon si moins de 5 minutes
        return lastAlert.isAfter(LocalDateTime.now().minusMinutes(5));
    }

    private String extractIpAddress(Map<String, Object> metadata) {
        return (String) metadata.getOrDefault("ipAddress", "unknown");
    }

    private String extractUserAgent(Map<String, Object> metadata) {
        return (String) metadata.getOrDefault("userAgent", "unknown");
    }

    private Object maskSensitiveValue(Object value) {
        if (value == null) return null;
        String str = value.toString();
        if (str.length() <= 4) return "****";
        return str.substring(0, 2) + "****" + str.substring(str.length() - 2);
    }
}