package com.bantuops.backend.listener;

import com.bantuops.backend.entity.AuditLog;
import com.bantuops.backend.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Listener pour les événements d'audit JPA
 * Conforme aux exigences 7.4, 7.5, 7.6 pour l'enregistrement asynchrone des événements
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditEventListener {

    private final AuditService auditService;

    /**
     * Événement d'audit générique
     */
    public static class AuditEvent {
        private final String entityType;
        private final Long entityId;
        private final AuditLog.AuditAction action;
        private final String description;
        private final Object oldValue;
        private final Object newValue;
        private final String reason;
        private final boolean sensitiveData;
        private final LocalDateTime timestamp;

        public AuditEvent(String entityType, Long entityId, AuditLog.AuditAction action, 
                         String description, Object oldValue, Object newValue, 
                         String reason, boolean sensitiveData) {
            this.entityType = entityType;
            this.entityId = entityId;
            this.action = action;
            this.description = description;
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.reason = reason;
            this.sensitiveData = sensitiveData;
            this.timestamp = LocalDateTime.now();
        }

        // Getters
        public String getEntityType() { return entityType; }
        public Long getEntityId() { return entityId; }
        public AuditLog.AuditAction getAction() { return action; }
        public String getDescription() { return description; }
        public Object getOldValue() { return oldValue; }
        public Object getNewValue() { return newValue; }
        public String getReason() { return reason; }
        public boolean isSensitiveData() { return sensitiveData; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    /**
     * Événement de sécurité
     */
    public static class SecurityEvent {
        private final String eventType;
        private final String principal;
        private final Map<String, Object> data;
        private final LocalDateTime timestamp;

        public SecurityEvent(String eventType, String principal, Map<String, Object> data) {
            this.eventType = eventType;
            this.principal = principal;
            this.data = data;
            this.timestamp = LocalDateTime.now();
        }

        // Getters
        public String getEventType() { return eventType; }
        public String getPrincipal() { return principal; }
        public Map<String, Object> getData() { return data; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    /**
     * Événement de performance
     */
    public static class PerformanceEvent {
        private final String methodName;
        private final Map<String, Object> metrics;
        private final LocalDateTime timestamp;

        public PerformanceEvent(String methodName, Map<String, Object> metrics) {
            this.methodName = methodName;
            this.metrics = metrics;
            this.timestamp = LocalDateTime.now();
        }

        // Getters
        public String getMethodName() { return methodName; }
        public Map<String, Object> getMetrics() { return metrics; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    /**
     * Événement de violation de règle métier
     */
    public static class BusinessRuleViolationEvent {
        private final String ruleName;
        private final String entityType;
        private final Long entityId;
        private final String violationDetails;
        private final Map<String, Object> context;
        private final LocalDateTime timestamp;

        public BusinessRuleViolationEvent(String ruleName, String entityType, Long entityId, 
                                        String violationDetails, Map<String, Object> context) {
            this.ruleName = ruleName;
            this.entityType = entityType;
            this.entityId = entityId;
            this.violationDetails = violationDetails;
            this.context = context;
            this.timestamp = LocalDateTime.now();
        }

        // Getters
        public String getRuleName() { return ruleName; }
        public String getEntityType() { return entityType; }
        public Long getEntityId() { return entityId; }
        public String getViolationDetails() { return violationDetails; }
        public Map<String, Object> getContext() { return context; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    /**
     * Traite les événements d'audit de manière asynchrone
     */
    @Async
    @EventListener
    public void handleAuditEvent(AuditEvent event) {
        try {
            log.debug("Traitement de l'événement d'audit: {} - {}", event.getEntityType(), event.getAction());
            
            auditService.logAuditEvent(
                event.getEntityType(),
                event.getEntityId(),
                event.getAction(),
                event.getDescription(),
                event.getOldValue(),
                event.getNewValue(),
                event.getReason(),
                event.isSensitiveData()
            );
            
        } catch (Exception e) {
            log.error("Erreur lors du traitement de l'événement d'audit: {}", e.getMessage());
        }
    }

    /**
     * Traite les événements de sécurité de manière asynchrone
     */
    @Async
    @EventListener
    public void handleSecurityEvent(SecurityEvent event) {
        try {
            log.debug("Traitement de l'événement de sécurité: {} - {}", event.getEventType(), event.getPrincipal());
            
            auditService.logSecurityEvent(
                event.getEventType(),
                event.getPrincipal(),
                event.getData(),
                event.getTimestamp()
            );
            
        } catch (Exception e) {
            log.error("Erreur lors du traitement de l'événement de sécurité: {}", e.getMessage());
        }
    }

    /**
     * Traite les événements de performance de manière asynchrone
     */
    @Async
    @EventListener
    public void handlePerformanceEvent(PerformanceEvent event) {
        try {
            log.debug("Traitement de l'événement de performance: {}", event.getMethodName());
            
            auditService.logPerformanceMetrics(event.getMethodName(), event.getMetrics());
            
        } catch (Exception e) {
            log.error("Erreur lors du traitement de l'événement de performance: {}", e.getMessage());
        }
    }

    /**
     * Traite les événements de violation de règle métier de manière asynchrone
     */
    @Async
    @EventListener
    public void handleBusinessRuleViolationEvent(BusinessRuleViolationEvent event) {
        try {
            log.debug("Traitement de la violation de règle métier: {} - {}", event.getRuleName(), event.getEntityType());
            
            auditService.logBusinessRuleViolation(
                event.getRuleName(),
                event.getEntityType(),
                event.getEntityId(),
                event.getViolationDetails(),
                event.getContext()
            );
            
        } catch (Exception e) {
            log.error("Erreur lors du traitement de la violation de règle métier: {}", e.getMessage());
        }
    }
}