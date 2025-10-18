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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Système de réponse automatique aux incidents de sécurité
 * Applique des mesures correctives automatiques en fonction du type et de la
 * sévérité des alertes
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutomaticSecurityResponseSystem {

    private final UserRepository userRepository;
    private final SessionManagementService sessionManagementService;
    private final SecurityNotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final DataEncryptionService encryptionService;

    // Cache des réponses automatiques actives
    private final Map<String, AutomaticResponse> activeResponses = new ConcurrentHashMap<>();

    // Configuration des réponses automatiques
    private static final Map<SecurityAlert.AlertType, ResponseAction> RESPONSE_ACTIONS = Map.of(
            SecurityAlert.AlertType.FAILED_LOGIN_ATTEMPT, ResponseAction.RATE_LIMIT,
            SecurityAlert.AlertType.UNAUTHORIZED_ACCESS, ResponseAction.SESSION_INVALIDATION,
            SecurityAlert.AlertType.SENSITIVE_DATA_MODIFICATION, ResponseAction.AUDIT_ENHANCEMENT,
            SecurityAlert.AlertType.ABNORMAL_ACTIVITY, ResponseAction.USER_MONITORING,
            SecurityAlert.AlertType.SECURITY_BREACH, ResponseAction.EMERGENCY_LOCKDOWN,
            SecurityAlert.AlertType.SYSTEM_COMPROMISE, ResponseAction.FULL_SYSTEM_PROTECTION);

    /**
     * Déclenche une réponse automatique pour une alerte critique
     */
    @Async
    @Transactional
    public void triggerAutomaticResponse(SecurityAlert alert) {
        try {
            log.error("Déclenchement de la réponse automatique pour l'alerte critique: {}",
                    alert.getId());

            // Déterminer l'action de réponse appropriée
            ResponseAction action = determineResponseAction(alert);

            // Créer et enregistrer la réponse automatique
            AutomaticResponse response = createAutomaticResponse(alert, action);
            activeResponses.put(alert.getUserId() + "_" + alert.getAlertType(), response);

            // Exécuter l'action de réponse
            executeResponseAction(alert, action);

            // Enregistrer la réponse dans les logs
            logAutomaticResponse(alert, action);

            // Notifier les administrateurs
            notifyAdministrators(alert, action);

        } catch (Exception e) {
            log.error("Erreur lors du déclenchement de la réponse automatique", e);
        }
    }

    /**
     * Détermine l'action de réponse appropriée
     */
    private ResponseAction determineResponseAction(SecurityAlert alert) {
        ResponseAction baseAction = RESPONSE_ACTIONS.getOrDefault(
                alert.getAlertType(), ResponseAction.STANDARD_MONITORING);

        // Escalader l'action selon la sévérité
        if (alert.getSeverity() == SecurityAlert.Severity.CRITICAL) {
            return escalateResponseAction(baseAction);
        }

        return baseAction;
    }

    /**
     * Escalade l'action de réponse pour les alertes critiques
     */
    private ResponseAction escalateResponseAction(ResponseAction baseAction) {
        return switch (baseAction) {
            case RATE_LIMIT -> ResponseAction.IP_BLOCKING;
            case SESSION_INVALIDATION -> ResponseAction.ACCOUNT_SUSPENSION;
            case AUDIT_ENHANCEMENT -> ResponseAction.DATA_PROTECTION_MODE;
            case USER_MONITORING -> ResponseAction.ACCOUNT_LOCKDOWN;
            case EMERGENCY_LOCKDOWN -> ResponseAction.FULL_SYSTEM_PROTECTION;
            default -> ResponseAction.EMERGENCY_LOCKDOWN;
        };
    }

    /**
     * Exécute l'action de réponse automatique
     */
    private void executeResponseAction(SecurityAlert alert, ResponseAction action) {
        log.info("Exécution de l'action de réponse: {} pour l'alerte: {}", action, alert.getId());

        switch (action) {
            case RATE_LIMIT:
                applyRateLimit(alert);
                break;
            case IP_BLOCKING:
                blockSuspiciousIp(alert);
                break;
            case SESSION_INVALIDATION:
                invalidateUserSessions(alert);
                break;
            case ACCOUNT_SUSPENSION:
                suspendUserAccount(alert);
                break;
            case ACCOUNT_LOCKDOWN:
                lockdownUserAccount(alert);
                break;
            case AUDIT_ENHANCEMENT:
                enhanceAuditLogging(alert);
                break;
            case DATA_PROTECTION_MODE:
                activateDataProtectionMode(alert);
                break;
            case USER_MONITORING:
                activateUserMonitoring(alert);
                break;
            case EMERGENCY_LOCKDOWN:
                initiateEmergencyLockdown(alert);
                break;
            case FULL_SYSTEM_PROTECTION:
                activateFullSystemProtection(alert);
                break;
            case STANDARD_MONITORING:
                activateStandardMonitoring(alert);
                break;
        }
    }

    /**
     * Applique une limitation de débit
     */
    private void applyRateLimit(SecurityAlert alert) {
        String userId = alert.getUserId();
        String ipAddress = alert.getIpAddress();

        log.info("Application de la limitation de débit pour l'utilisateur: {} depuis l'IP: {}",
                userId, ipAddress);

        // Implémenter la limitation de débit (exemple avec Redis)
        sessionManagementService.applyRateLimit(userId, ipAddress, 10, 60); // 10 requêtes par minute

        // Notifier l'utilisateur
        notificationService.sendUserNotification(userId,
                "Limitation de débit appliquée",
                "Votre compte a été temporairement limité en raison d'une activité suspecte.");
    }

    /**
     * Bloque une IP suspecte
     */
    private void blockSuspiciousIp(SecurityAlert alert) {
        String ipAddress = alert.getIpAddress();

        log.warn("Blocage de l'IP suspecte: {}", ipAddress);

        // Ajouter l'IP à la liste noire
        sessionManagementService.blockIpAddress(ipAddress, "Activité suspecte détectée", 24);

        // Invalider toutes les sessions depuis cette IP
        sessionManagementService.invalidateSessionsByIp(ipAddress);

        // Créer une alerte pour l'équipe de sécurité
        notificationService.sendSecurityTeamNotification(
                "IP bloquée automatiquement",
                String.format("L'IP %s a été bloquée automatiquement suite à une activité suspecte", ipAddress));
    }

    /**
     * Invalide les sessions utilisateur
     */
    private void invalidateUserSessions(SecurityAlert alert) {
        String userId = alert.getUserId();

        log.info("Invalidation des sessions pour l'utilisateur: {}", userId);

        // Invalider toutes les sessions actives
        sessionManagementService.invalidateUserSessions(userId);

        // Révoquer les tokens d'accès
        sessionManagementService.revokeUserTokens(userId);

        // Notifier l'utilisateur
        notificationService.sendUserNotification(userId,
                "Sessions fermées pour sécurité",
                "Vos sessions ont été fermées automatiquement suite à une activité suspecte. " +
                        "Veuillez vous reconnecter.");
    }

    /**
     * Suspend le compte utilisateur
     */
    @Transactional
    private void suspendUserAccount(SecurityAlert alert) {
        String userId = alert.getUserId();

        log.warn("Suspension automatique du compte utilisateur: {}", userId);

        userRepository.findByUsername(userId).ifPresent(user -> {
            user.setSuspended(true);
            user.setSuspensionReason("Suspension automatique - Alerte de sécurité critique");
            user.setSuspendedUntil(LocalDateTime.now().plusHours(24));
            userRepository.save(user);
        });

        // Invalider toutes les sessions
        sessionManagementService.invalidateUserSessions(userId);

        // Notifier l'utilisateur et les administrateurs
        notificationService.sendUserNotification(userId,
                "Compte suspendu temporairement",
                "Votre compte a été suspendu automatiquement pour 24h suite à une alerte de sécurité. " +
                        "Contactez l'administrateur pour plus d'informations.");

        notificationService.sendAdminNotification(
                "Suspension automatique de compte",
                String.format("Le compte %s a été suspendu automatiquement", userId));
    }

    /**
     * Verrouille complètement le compte utilisateur
     */
    @Transactional
    private void lockdownUserAccount(SecurityAlert alert) {
        String userId = alert.getUserId();

        log.error("Verrouillage complet du compte utilisateur: {}", userId);

        userRepository.findByUsername(userId).ifPresent(user -> {
            user.setBlocked(true);
            user.setBlockReason("Verrouillage automatique - Alerte de sécurité critique");
            user.setBlockedAt(LocalDateTime.now());
            userRepository.save(user);
        });

        // Invalider toutes les sessions et tokens
        sessionManagementService.invalidateUserSessions(userId);
        sessionManagementService.revokeUserTokens(userId);

        // Notification d'urgence
        notificationService.sendCriticalAlert(
                "Compte verrouillé automatiquement",
                String.format("Le compte %s a été verrouillé automatiquement suite à une alerte critique", userId));
    }

    /**
     * Améliore l'audit et la journalisation
     */
    private void enhanceAuditLogging(SecurityAlert alert) {
        String userId = alert.getUserId();

        log.info("Amélioration de l'audit pour l'utilisateur: {}", userId);

        // Activer l'audit détaillé pour cet utilisateur
        sessionManagementService.enableDetailedAudit(userId, 48); // 48 heures

        // Enregistrer toutes les actions de l'utilisateur
        sessionManagementService.enableFullActionLogging(userId);

        // Notifier l'équipe de sécurité
        notificationService.sendSecurityTeamNotification(
                "Audit renforcé activé",
                String.format("L'audit détaillé a été activé pour l'utilisateur %s", userId));
    }

    /**
     * Active le mode de protection des données
     */
    private void activateDataProtectionMode(SecurityAlert alert) {
        String userId = alert.getUserId();

        log.warn("Activation du mode de protection des données pour l'utilisateur: {}", userId);

        // Restreindre l'accès aux données sensibles
        sessionManagementService.restrictSensitiveDataAccess(userId);

        // Exiger une double authentification
        sessionManagementService.requireTwoFactorAuth(userId);

        // TODO: Implement enhanced encryption mode in DataEncryptionService
        // encryptionService.enableEnhancedEncryption();
        log.info("Enhanced encryption would be enabled here for user: {}", userId);

        // Notification
        notificationService.sendSecurityTeamNotification(
                "Mode protection des données activé",
                String.format("Le mode de protection des données a été activé suite à l'alerte pour %s", userId));
    }

    /**
     * Active la surveillance utilisateur renforcée
     */
    private void activateUserMonitoring(SecurityAlert alert) {
        String userId = alert.getUserId();

        log.info("Activation de la surveillance renforcée pour l'utilisateur: {}", userId);

        // Surveiller toutes les actions de l'utilisateur
        sessionManagementService.enableRealTimeMonitoring(userId);

        // Réduire les seuils d'alerte
        sessionManagementService.setLowAlertThresholds(userId);

        // Notification
        notificationService.sendSecurityTeamNotification(
                "Surveillance renforcée activée",
                String.format("La surveillance renforcée a été activée pour l'utilisateur %s", userId));
    }

    /**
     * Initie un verrouillage d'urgence
     */
    private void initiateEmergencyLockdown(SecurityAlert alert) {
        log.error("INITIATION DU VERROUILLAGE D'URGENCE");

        // Bloquer tous les nouveaux accès
        sessionManagementService.blockAllNewSessions();

        // Invalider toutes les sessions actives sauf les administrateurs
        sessionManagementService.invalidateAllNonAdminSessions();

        // Activer le mode maintenance
        sessionManagementService.enableMaintenanceMode();

        // Notification d'urgence à tous les administrateurs
        notificationService.sendEmergencyNotificationToAllAdmins(
                "VERROUILLAGE D'URGENCE ACTIVÉ",
                "Un verrouillage d'urgence a été initié automatiquement suite à une alerte critique");
    }

    /**
     * Active la protection complète du système
     */
    private void activateFullSystemProtection(SecurityAlert alert) {
        log.error("ACTIVATION DE LA PROTECTION COMPLÈTE DU SYSTÈME");

        // Arrêter tous les services non essentiels
        sessionManagementService.stopNonEssentialServices();

        // TODO: Implement maximum encryption mode in DataEncryptionService
        // encryptionService.enableMaximumEncryption();
        log.error("Maximum encryption would be enabled here");

        // Sauvegarder les données critiques
        sessionManagementService.initiateEmergencyBackup();

        // Isoler le système
        sessionManagementService.isolateSystem();

        // Notification d'urgence maximale
        notificationService.sendCriticalSystemAlert(
                "PROTECTION SYSTÈME MAXIMALE ACTIVÉE",
                "La protection complète du système a été activée automatiquement");
    }

    /**
     * Active la surveillance standard
     */
    private void activateStandardMonitoring(SecurityAlert alert) {
        String userId = alert.getUserId();

        log.info("Activation de la surveillance standard pour l'utilisateur: {}", userId);

        // Surveiller les actions de l'utilisateur
        sessionManagementService.enableStandardMonitoring(userId);
    }

    /**
     * Crée une réponse automatique
     */
    private AutomaticResponse createAutomaticResponse(SecurityAlert alert, ResponseAction action) {
        return AutomaticResponse.builder()
                .alertId(alert.getId())
                .userId(alert.getUserId())
                .alertType(alert.getAlertType())
                .responseAction(action)
                .triggeredAt(LocalDateTime.now())
                .status(AutomaticResponse.Status.ACTIVE)
                .build();
    }

    /**
     * Enregistre la réponse automatique dans les logs
     */
    private void logAutomaticResponse(SecurityAlert alert, ResponseAction action) {
        log.info("Réponse automatique enregistrée - Alerte: {}, Action: {}, Utilisateur: {}",
                alert.getId(), action, alert.getUserId());
    }

    /**
     * Notifie les administrateurs de la réponse automatique
     */
    private void notifyAdministrators(SecurityAlert alert, ResponseAction action) {
        notificationService.sendAdminNotification(
                "Réponse automatique déclenchée",
                String.format("Action automatique %s déclenchée pour l'alerte %s (utilisateur: %s)",
                        action, alert.getId(), alert.getUserId()));
    }

    /**
     * Énumération des actions de réponse
     */
    public enum ResponseAction {
        RATE_LIMIT,
        IP_BLOCKING,
        SESSION_INVALIDATION,
        ACCOUNT_SUSPENSION,
        ACCOUNT_LOCKDOWN,
        AUDIT_ENHANCEMENT,
        DATA_PROTECTION_MODE,
        USER_MONITORING,
        EMERGENCY_LOCKDOWN,
        FULL_SYSTEM_PROTECTION,
        STANDARD_MONITORING
    }

    /**
     * Classe représentant une réponse automatique
     */
    @lombok.Data
    @lombok.Builder
    public static class AutomaticResponse {
        private Long alertId;
        private String userId;
        private SecurityAlert.AlertType alertType;
        private ResponseAction responseAction;
        private LocalDateTime triggeredAt;
        private Status status;
        private String details;

        public enum Status {
            ACTIVE,
            COMPLETED,
            FAILED,
            CANCELLED
        }
    }
}