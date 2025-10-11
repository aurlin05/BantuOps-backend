package com.bantuops.backend.service;

import com.bantuops.backend.dto.SecurityAlert;
import com.bantuops.backend.entity.AuditLog;
import com.bantuops.backend.entity.User;
import com.bantuops.backend.repository.AuditLogRepository;
import com.bantuops.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service de détection d'activités suspectes
 * Analyse les patterns d'utilisation pour identifier les comportements anormaux
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SuspiciousActivityDetector {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    // Cache des patterns d'activité par utilisateur
    private final Map<String, UserActivityPattern> userPatterns = new ConcurrentHashMap<>();

    // Seuils de détection
    private static final int MAX_FAILED_LOGINS_PER_HOUR = 5;
    private static final int MAX_REQUESTS_PER_MINUTE = 100;
    private static final int MAX_DATA_EXPORTS_PER_DAY = 10;
    private static final LocalTime NORMAL_WORK_START = LocalTime.of(7, 0);
    private static final LocalTime NORMAL_WORK_END = LocalTime.of(19, 0);

    /**
     * Analyse une alerte de sécurité pour détecter une activité suspecte
     */
    @Transactional(readOnly = true)
    public boolean analyzeSuspiciousActivity(SecurityAlert alert) {
        try {
            log.debug("Analyse d'activité suspecte pour l'alerte: {}", alert.getId());

            boolean isSuspicious = false;

            // Analyser selon le type d'alerte
            switch (alert.getAlertType()) {
                case FAILED_LOGIN_ATTEMPT:
                    isSuspicious = analyzeFailedLoginPattern(alert);
                    break;
                case UNAUTHORIZED_ACCESS:
                    isSuspicious = analyzeUnauthorizedAccessPattern(alert);
                    break;
                case SENSITIVE_DATA_MODIFICATION:
                    isSuspicious = analyzeSensitiveDataPattern(alert);
                    break;
                case ABNORMAL_ACTIVITY:
                    isSuspicious = analyzeAbnormalActivityPattern(alert);
                    break;
                default:
                    isSuspicious = analyzeGeneralPattern(alert);
            }

            // Analyser les patterns temporels
            if (!isSuspicious) {
                isSuspicious = analyzeTemporalPattern(alert);
            }

            // Analyser les patterns géographiques
            if (!isSuspicious) {
                isSuspicious = analyzeGeographicalPattern(alert);
            }

            // Analyser les patterns comportementaux
            if (!isSuspicious) {
                isSuspicious = analyzeBehavioralPattern(alert);
            }

            if (isSuspicious) {
                log.warn("Activité suspecte détectée pour l'utilisateur: {} - Type: {}", 
                    alert.getUserId(), alert.getAlertType());
                updateSuspiciousActivityCounter(alert.getUserId());
            }

            return isSuspicious;

        } catch (Exception e) {
            log.error("Erreur lors de l'analyse d'activité suspecte", e);
            return false;
        }
    }

    /**
     * Analyse les patterns de tentatives de connexion échouées
     */
    private boolean analyzeFailedLoginPattern(SecurityAlert alert) {
        String userId = alert.getUserId();
        LocalDateTime since = LocalDateTime.now().minusHours(1);

        // Compter les tentatives échouées dans la dernière heure
        long failedAttempts = auditLogRepository.countFailedLoginAttempts(userId, since);

        if (failedAttempts >= MAX_FAILED_LOGINS_PER_HOUR) {
            log.warn("Trop de tentatives de connexion échouées: {} pour l'utilisateur: {}", 
                failedAttempts, userId);
            return true;
        }

        // Vérifier si les tentatives viennent de différentes IP
        List<String> ipAddresses = auditLogRepository.getFailedLoginIpAddresses(userId, since);
        if (ipAddresses.size() > 3) {
            log.warn("Tentatives de connexion depuis {} IP différentes pour l'utilisateur: {}", 
                ipAddresses.size(), userId);
            return true;
        }

        return false;
    }

    /**
     * Analyse les patterns d'accès non autorisé
     */
    private boolean analyzeUnauthorizedAccessPattern(SecurityAlert alert) {
        String userId = alert.getUserId();
        LocalDateTime since = LocalDateTime.now().minusHours(1);

        // Compter les tentatives d'accès non autorisé
        long unauthorizedAttempts = auditLogRepository.countUnauthorizedAccess(userId, since);

        if (unauthorizedAttempts >= 10) {
            log.warn("Trop de tentatives d'accès non autorisé: {} pour l'utilisateur: {}", 
                unauthorizedAttempts, userId);
            return true;
        }

        // Vérifier si l'utilisateur tente d'accéder à des ressources sensibles
        String resource = (String) alert.getMetadata().get("resource");
        if (isSensitiveResource(resource)) {
            log.warn("Tentative d'accès à une ressource sensible: {} par l'utilisateur: {}", 
                resource, userId);
            return true;
        }

        return false;
    }

    /**
     * Analyse les patterns de modification de données sensibles
     */
    private boolean analyzeSensitiveDataPattern(SecurityAlert alert) {
        String userId = alert.getUserId();
        LocalDateTime since = LocalDateTime.now().minusHours(1);

        // Compter les modifications de données sensibles
        long modifications = auditLogRepository.countSensitiveDataModifications(userId, since);

        if (modifications >= 50) {
            log.warn("Trop de modifications de données sensibles: {} par l'utilisateur: {}", 
                modifications, userId);
            return true;
        }

        // Vérifier si les modifications sont faites en dehors des heures normales
        LocalTime currentTime = LocalTime.now();
        if (currentTime.isBefore(NORMAL_WORK_START) || currentTime.isAfter(NORMAL_WORK_END)) {
            log.warn("Modification de données sensibles en dehors des heures normales par: {}", userId);
            return true;
        }

        return false;
    }

    /**
     * Analyse les patterns d'activité anormale
     */
    private boolean analyzeAbnormalActivityPattern(SecurityAlert alert) {
        String userId = alert.getUserId();
        String activityType = (String) alert.getMetadata().get("activityType");

        // Analyser selon le type d'activité
        switch (activityType) {
            case "BULK_DATA_EXPORT":
                return analyzeBulkDataExport(userId, alert);
            case "RAPID_API_CALLS":
                return analyzeRapidApiCalls(userId, alert);
            case "UNUSUAL_DATA_ACCESS":
                return analyzeUnusualDataAccess(userId, alert);
            default:
                return false;
        }
    }

    /**
     * Analyse les patterns temporels
     */
    private boolean analyzeTemporalPattern(SecurityAlert alert) {
        String userId = alert.getUserId();
        LocalDateTime alertTime = alert.getTimestamp();

        // Récupérer le pattern d'activité habituel de l'utilisateur
        UserActivityPattern pattern = getUserActivityPattern(userId);

        // Vérifier si l'activité est en dehors des heures habituelles
        if (!pattern.isNormalActivityTime(alertTime)) {
            log.warn("Activité en dehors des heures habituelles pour l'utilisateur: {} à {}", 
                userId, alertTime);
            return true;
        }

        return false;
    }

    /**
     * Analyse les patterns géographiques
     */
    private boolean analyzeGeographicalPattern(SecurityAlert alert) {
        String userId = alert.getUserId();
        String currentIp = alert.getIpAddress();

        if (currentIp == null || "unknown".equals(currentIp)) {
            return false;
        }

        // Récupérer les IP habituelles de l'utilisateur
        List<String> usualIps = auditLogRepository.getUserUsualIpAddresses(userId, 
            LocalDateTime.now().minusDays(30));

        // Vérifier si l'IP est nouvelle
        if (!usualIps.contains(currentIp)) {
            log.warn("Connexion depuis une nouvelle IP: {} pour l'utilisateur: {}", 
                currentIp, userId);
            
            // Vérifier si c'est une IP suspecte (exemple: TOR, VPN connus, etc.)
            if (isSuspiciousIp(currentIp)) {
                log.warn("Connexion depuis une IP suspecte: {} pour l'utilisateur: {}", 
                    currentIp, userId);
                return true;
            }
        }

        return false;
    }

    /**
     * Analyse les patterns comportementaux
     */
    private boolean analyzeBehavioralPattern(SecurityAlert alert) {
        String userId = alert.getUserId();
        
        // Récupérer le pattern comportemental de l'utilisateur
        UserActivityPattern pattern = getUserActivityPattern(userId);
        
        // Analyser la fréquence d'activité
        if (pattern.isActivityFrequencyAbnormal(alert)) {
            log.warn("Fréquence d'activité anormale pour l'utilisateur: {}", userId);
            return true;
        }

        return false;
    }

    /**
     * Analyse l'export de données en masse
     */
    private boolean analyzeBulkDataExport(String userId, SecurityAlert alert) {
        LocalDateTime since = LocalDateTime.now().minusDays(1);
        long exports = auditLogRepository.countDataExports(userId, since);

        if (exports >= MAX_DATA_EXPORTS_PER_DAY) {
            log.warn("Trop d'exports de données: {} par l'utilisateur: {}", exports, userId);
            return true;
        }

        return false;
    }

    /**
     * Analyse les appels API rapides
     */
    private boolean analyzeRapidApiCalls(String userId, SecurityAlert alert) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(1);
        long apiCalls = auditLogRepository.countApiCalls(userId, since);

        if (apiCalls >= MAX_REQUESTS_PER_MINUTE) {
            log.warn("Trop d'appels API: {} par l'utilisateur: {}", apiCalls, userId);
            return true;
        }

        return false;
    }

    /**
     * Analyse l'accès inhabituel aux données
     */
    private boolean analyzeUnusualDataAccess(String userId, SecurityAlert alert) {
        // Vérifier si l'utilisateur accède à des données qu'il ne consulte pas habituellement
        List<String> usualResources = auditLogRepository.getUserUsualResources(userId, 
            LocalDateTime.now().minusDays(30));

        String resource = (String) alert.getMetadata().get("resource");
        if (resource != null && !usualResources.contains(resource)) {
            log.warn("Accès à une ressource inhabituelle: {} par l'utilisateur: {}", 
                resource, userId);
            return true;
        }

        return false;
    }

    /**
     * Récupère ou crée le pattern d'activité d'un utilisateur
     */
    private UserActivityPattern getUserActivityPattern(String userId) {
        return userPatterns.computeIfAbsent(userId, id -> {
            // Analyser l'historique d'activité pour créer le pattern
            List<AuditLog> recentActivity = auditLogRepository.getUserRecentActivity(id, 
                LocalDateTime.now().minusDays(30));
            return new UserActivityPattern(id, recentActivity);
        });
    }

    /**
     * Met à jour le compteur d'activités suspectes
     */
    private void updateSuspiciousActivityCounter(String userId) {
        UserActivityPattern pattern = getUserActivityPattern(userId);
        pattern.incrementSuspiciousActivityCount();
    }

    /**
     * Vérifie si une ressource est sensible
     */
    private boolean isSensitiveResource(String resource) {
        if (resource == null) return false;
        
        Set<String> sensitiveResources = Set.of(
            "/api/payroll", "/api/financial", "/api/admin", 
            "/api/reports", "/api/export", "/api/users"
        );
        
        return sensitiveResources.stream().anyMatch(resource::startsWith);
    }

    /**
     * Vérifie si une IP est suspecte
     */
    private boolean isSuspiciousIp(String ip) {
        // Implémentation basique - dans un vrai système, utiliser des services de géolocalisation
        // et des listes d'IP suspectes
        return ip.startsWith("10.0.0.") || ip.startsWith("192.168.") || ip.equals("127.0.0.1");
    }

    /**
     * Classe interne pour représenter le pattern d'activité d'un utilisateur
     */
    private static class UserActivityPattern {
        private final String userId;
        private final List<LocalTime> usualActivityTimes;
        private final Map<String, Integer> resourceAccessFrequency;
        private int suspiciousActivityCount = 0;

        public UserActivityPattern(String userId, List<AuditLog> recentActivity) {
            this.userId = userId;
            this.usualActivityTimes = recentActivity.stream()
                .map(log -> log.getTimestamp().toLocalTime())
                .toList();
            this.resourceAccessFrequency = new ConcurrentHashMap<>();
            
            // Analyser la fréquence d'accès aux ressources
            recentActivity.forEach(log -> {
                String resource = log.getEntityType();
                resourceAccessFrequency.merge(resource, 1, Integer::sum);
            });
        }

        public boolean isNormalActivityTime(LocalDateTime time) {
            LocalTime timeOfDay = time.toLocalTime();
            
            // Vérifier si l'heure est dans la plage normale d'activité
            return timeOfDay.isAfter(NORMAL_WORK_START) && timeOfDay.isBefore(NORMAL_WORK_END);
        }

        public boolean isActivityFrequencyAbnormal(SecurityAlert alert) {
            // Logique simplifiée - dans un vrai système, utiliser des algorithmes ML
            return suspiciousActivityCount > 10;
        }

        public void incrementSuspiciousActivityCount() {
            this.suspiciousActivityCount++;
        }
    }
}