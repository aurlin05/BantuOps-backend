package com.bantuops.backend.service;

import com.bantuops.backend.dto.SecurityAlert;
import com.bantuops.backend.entity.User;
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
 * Service de gestion des violations de sécurité
 * Traite les violations détectées et applique les mesures correctives appropriées
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityViolationHandler {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SecurityNotificationService notificationService;
    private final SessionManagementService sessionManagementService;

    // Cache des violations par utilisateur pour le suivi
    private final Map<String, ViolationTracker> userViolations = new ConcurrentHashMap<>();

    // Seuils de violation
    private static final int MAX_LOW_VIOLATIONS = 10;
    private static final int MAX_MEDIUM_VIOLATIONS = 5;
    private static final int MAX_HIGH_VIOLATIONS = 3;
    private static final int MAX_CRITICAL_VIOLATIONS = 1;

    /**
     * Traite une violation de sécurité
     */
    @Async
    @Transactional
    public void handleSecurityViolation(SecurityAlert alert) {
        try {
            log.warn("Traitement de la violation de sécurité: {} pour l'utilisateur: {}", 
                alert.getAlertType(), alert.getUserId());

            // Enregistrer la violation
            recordViolation(alert);

            // Appliquer les mesures selon la sévérité
            switch (alert.getSeverity()) {
                case LOW:
                    handleLowSeverityViolation(alert);
                    break;
                case MEDIUM:
                    handleMediumSeverityViolation(alert);
                    break;
                case HIGH:
                    handleHighSeverityViolation(alert);
                    break;
                case CRITICAL:
                    handleCriticalSeverityViolation(alert);
                    break;
            }

            // Vérifier si l'utilisateur dépasse les seuils
            checkViolationThresholds(alert.getUserId());

            // Notifier les administrateurs si nécessaire
            if (alert.getSeverity().isHigherThan(SecurityAlert.Severity.MEDIUM)) {
                notifySecurityTeam(alert);
            }

        } catch (Exception e) {
            log.error("Erreur lors du traitement de la violation de sécurité", e);
        }
    }

    /**
     * Traite les violations de faible sévérité
     */
    private void handleLowSeverityViolation(SecurityAlert alert) {
        log.info("Traitement violation faible sévérité: {}", alert.getDescription());

        // Enregistrer dans les logs d'audit
        recordAuditLog(alert, "LOW_SEVERITY_VIOLATION_RECORDED");

        // Envoyer une notification à l'utilisateur
        notificationService.sendUserNotification(
            alert.getUserId(),
            "Alerte de sécurité",
            "Une activité inhabituelle a été détectée sur votre compte. " +
            "Si ce n'était pas vous, veuillez contacter l'administrateur."
        );
    }

    /**
     * Traite les violations de sévérité moyenne
     */
    private void handleMediumSeverityViolation(SecurityAlert alert) {
        log.warn("Traitement violation sévérité moyenne: {}", alert.getDescription());

        // Enregistrer dans les logs d'audit
        recordAuditLog(alert, "MEDIUM_SEVERITY_VIOLATION_RECORDED");

        // Forcer la réauthentification de l'utilisateur
        if (alert.getUserId() != null) {
            sessionManagementService.invalidateUserSessions(alert.getUserId());
            log.info("Sessions invalidées pour l'utilisateur: {}", alert.getUserId());
        }

        // Notifier l'utilisateur et son superviseur
        notificationService.sendUserNotification(
            alert.getUserId(),
            "Alerte de sécurité - Action requise",
            "Une violation de sécurité a été détectée. Vos sessions ont été fermées par sécurité. " +
            "Veuillez vous reconnecter et contacter l'administrateur."
        );

        notificationService.sendSupervisorNotification(alert.getUserId(), alert);
    }

    /**
     * Traite les violations de haute sévérité
     */
    private void handleHighSeverityViolation(SecurityAlert alert) {
        log.error("Traitement violation haute sévérité: {}", alert.getDescription());

        // Enregistrer dans les logs d'audit
        recordAuditLog(alert, "HIGH_SEVERITY_VIOLATION_RECORDED");

        String userId = alert.getUserId();
        if (userId != null) {
            // Suspendre temporairement l'utilisateur
            suspendUserTemporarily(userId, "Violation de sécurité haute sévérité", 24);

            // Invalider toutes les sessions
            sessionManagementService.invalidateUserSessions(userId);

            // Révoquer les tokens d'accès
            sessionManagementService.revokeUserTokens(userId);

            log.warn("Utilisateur {} suspendu temporairement pour violation haute sévérité", userId);
        }

        // Notification immédiate à l'équipe de sécurité
        notificationService.sendSecurityTeamAlert(alert);

        // Déclencher une enquête automatique
        triggerSecurityInvestigation(alert);
    }

    /**
     * Traite les violations critiques
     */
    private void handleCriticalSeverityViolation(SecurityAlert alert) {
        log.error("VIOLATION CRITIQUE DÉTECTÉE: {}", alert.getDescription());

        // Enregistrer dans les logs d'audit
        recordAuditLog(alert, "CRITICAL_VIOLATION_RECORDED");

        String userId = alert.getUserId();
        if (userId != null) {
            // Bloquer immédiatement l'utilisateur
            blockUserImmediately(userId, "Violation de sécurité critique");

            // Invalider toutes les sessions et tokens
            sessionManagementService.invalidateUserSessions(userId);
            sessionManagementService.revokeUserTokens(userId);

            log.error("Utilisateur {} bloqué immédiatement pour violation critique", userId);
        }

        // Alerte immédiate à tous les administrateurs
        notificationService.sendCriticalSecurityAlert(alert);

        // Déclencher les procédures d'urgence
        triggerEmergencyProcedures(alert);

        // Lancer une enquête de sécurité complète
        triggerFullSecurityInvestigation(alert);
    }

    /**
     * Vérifie si l'utilisateur dépasse les seuils de violation
     */
    private void checkViolationThresholds(String userId) {
        ViolationTracker tracker = userViolations.get(userId);
        if (tracker == null) return;

        boolean thresholdExceeded = false;
        String reason = "";

        if (tracker.getCriticalCount() >= MAX_CRITICAL_VIOLATIONS) {
            thresholdExceeded = true;
            reason = "Seuil de violations critiques dépassé";
        } else if (tracker.getHighCount() >= MAX_HIGH_VIOLATIONS) {
            thresholdExceeded = true;
            reason = "Seuil de violations hautes dépassé";
        } else if (tracker.getMediumCount() >= MAX_MEDIUM_VIOLATIONS) {
            thresholdExceeded = true;
            reason = "Seuil de violations moyennes dépassé";
        } else if (tracker.getLowCount() >= MAX_LOW_VIOLATIONS) {
            thresholdExceeded = true;
            reason = "Seuil de violations faibles dépassé";
        }

        if (thresholdExceeded) {
            log.error("Seuil de violations dépassé pour l'utilisateur {}: {}", userId, reason);
            
            // Escalader automatiquement
            escalateUserSecurity(userId, reason);
        }
    }

    /**
     * Enregistre une violation dans le tracker
     */
    private void recordViolation(SecurityAlert alert) {
        String userId = alert.getUserId();
        if (userId == null) return;

        ViolationTracker tracker = userViolations.computeIfAbsent(userId, 
            id -> new ViolationTracker(id));

        tracker.recordViolation(alert.getSeverity());
    }

    /**
     * Suspend temporairement un utilisateur
     */
    @Transactional
    private void suspendUserTemporarily(String userId, String reason, int hours) {
        userRepository.findByUsername(userId).ifPresent(user -> {
            user.setSuspended(true);
            user.setSuspensionReason(reason);
            user.setSuspendedUntil(LocalDateTime.now().plusHours(hours));
            userRepository.save(user);
            
            log.warn("Utilisateur {} suspendu jusqu'à {}", userId, user.getSuspendedUntil());
        });
    }

    /**
     * Bloque immédiatement un utilisateur
     */
    @Transactional
    private void blockUserImmediately(String userId, String reason) {
        userRepository.findByUsername(userId).ifPresent(user -> {
            user.setBlocked(true);
            user.setBlockReason(reason);
            user.setBlockedAt(LocalDateTime.now());
            userRepository.save(user);
            
            log.error("Utilisateur {} bloqué définitivement", userId);
        });
    }

    /**
     * Escalade la sécurité d'un utilisateur
     */
    private void escalateUserSecurity(String userId, String reason) {
        // Créer une alerte d'escalade
        SecurityAlert escalationAlert = SecurityAlert.builder()
            .alertType(SecurityAlert.AlertType.PRIVILEGE_ESCALATION)
            .userId(userId)
            .description("Escalade automatique: " + reason)
            .severity(SecurityAlert.Severity.HIGH)
            .timestamp(LocalDateTime.now())
            .build();

        // Traiter comme une violation haute sévérité
        handleHighSeverityViolation(escalationAlert);
    }

    /**
     * Déclenche une enquête de sécurité
     */
    private void triggerSecurityInvestigation(SecurityAlert alert) {
        log.info("Déclenchement d'une enquête de sécurité pour l'alerte: {}", alert.getId());
        
        // Créer un ticket d'enquête
        Map<String, Object> investigationData = Map.of(
            "alertId", alert.getId(),
            "userId", alert.getUserId(),
            "alertType", alert.getAlertType(),
            "severity", alert.getSeverity(),
            "timestamp", alert.getTimestamp(),
            "metadata", alert.getMetadata()
        );

        // Publier un événement d'enquête
        eventPublisher.publishEvent(new SecurityInvestigationEvent(investigationData));
    }

    /**
     * Déclenche les procédures d'urgence
     */
    private void triggerEmergencyProcedures(SecurityAlert alert) {
        log.error("DÉCLENCHEMENT DES PROCÉDURES D'URGENCE pour l'alerte: {}", alert.getId());
        
        // Publier un événement d'urgence
        eventPublisher.publishEvent(new SecurityEmergencyEvent(alert));
        
        // Notifier tous les administrateurs système
        notificationService.sendEmergencyNotificationToAllAdmins(alert);
    }

    /**
     * Déclenche une enquête de sécurité complète
     */
    private void triggerFullSecurityInvestigation(SecurityAlert alert) {
        log.error("Déclenchement d'une enquête de sécurité complète pour l'alerte: {}", alert.getId());
        
        // Créer un événement d'enquête complète
        eventPublisher.publishEvent(new FullSecurityInvestigationEvent(alert));
    }

    /**
     * Notifie l'équipe de sécurité
     */
    private void notifySecurityTeam(SecurityAlert alert) {
        notificationService.sendSecurityTeamNotification(
            "Violation de sécurité détectée",
            String.format("Une violation de sévérité %s a été détectée: %s", 
                alert.getSeverity().getLabel(), alert.getDescription())
        );
    }

    /**
     * Enregistre un log d'audit
     */
    private void recordAuditLog(SecurityAlert alert, String action) {
        // Implémentation de l'enregistrement d'audit
        log.info("Audit: {} - {}", action, alert.getDescription());
    }

    /**
     * Classe interne pour suivre les violations par utilisateur
     */
    private static class ViolationTracker {
        private final String userId;
        private int lowCount = 0;
        private int mediumCount = 0;
        private int highCount = 0;
        private int criticalCount = 0;
        private LocalDateTime lastViolation;

        public ViolationTracker(String userId) {
            this.userId = userId;
        }

        public void recordViolation(SecurityAlert.Severity severity) {
            this.lastViolation = LocalDateTime.now();
            
            switch (severity) {
                case LOW -> lowCount++;
                case MEDIUM -> mediumCount++;
                case HIGH -> highCount++;
                case CRITICAL -> criticalCount++;
            }
        }

        // Getters
        public int getLowCount() { return lowCount; }
        public int getMediumCount() { return mediumCount; }
        public int getHighCount() { return highCount; }
        public int getCriticalCount() { return criticalCount; }
        public LocalDateTime getLastViolation() { return lastViolation; }
    }

    /**
     * Événement d'enquête de sécurité
     */
    public static class SecurityInvestigationEvent {
        private final Map<String, Object> investigationData;

        public SecurityInvestigationEvent(Map<String, Object> investigationData) {
            this.investigationData = investigationData;
        }

        public Map<String, Object> getInvestigationData() {
            return investigationData;
        }
    }

    /**
     * Événement d'urgence de sécurité
     */
    public static class SecurityEmergencyEvent {
        private final SecurityAlert alert;

        public SecurityEmergencyEvent(SecurityAlert alert) {
            this.alert = alert;
        }

        public SecurityAlert getAlert() {
            return alert;
        }
    }

    /**
     * Événement d'enquête complète de sécurité
     */
    public static class FullSecurityInvestigationEvent {
        private final SecurityAlert alert;

        public FullSecurityInvestigationEvent(SecurityAlert alert) {
            this.alert = alert;
        }

        public SecurityAlert getAlert() {
            return alert;
        }
    }
}