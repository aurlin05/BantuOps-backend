package com.bantuops.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionManagementService {

    private final RedisTemplate<String, String> redisTemplate;
    private final SessionRepository<? extends Session> sessionRepository;
    private final DistributedSessionService distributedSessionService;

    private static final String ACTIVE_SESSIONS_KEY = "bantuops:active_sessions";
    private static final String USER_SESSIONS_PREFIX = "bantuops:user_sessions:";

    /**
     * Track active session for a user
     */
    public void trackUserSession(Long userId, String sessionId) {
        trackUserSession(userId, sessionId, null, null);
    }

    /**
     * Track active session for a user with metadata
     */
    public void trackUserSession(Long userId, String sessionId, String userAgent, String ipAddress) {
        try {
            // Add to global active sessions set
            redisTemplate.opsForSet().add(ACTIVE_SESSIONS_KEY, sessionId);
            
            // Track user-specific sessions
            String userSessionsKey = USER_SESSIONS_PREFIX + userId;
            redisTemplate.opsForSet().add(userSessionsKey, sessionId);
            
            // Set expiration for user sessions key (cleanup)
            redisTemplate.expire(userSessionsKey, Duration.ofHours(25));
            
            // Register in distributed session registry
            String nodeId = getNodeId();
            distributedSessionService.registerDistributedSession(sessionId, userId, nodeId);
            
            // Store session metadata if provided
            if (userAgent != null || ipAddress != null) {
                storeSessionMetadata(sessionId, userId, userAgent, ipAddress);
            }
            
            // Broadcast session creation event
            distributedSessionService.broadcastSessionEvent("SESSION_CREATED", sessionId, userId);
            
            log.debug("Session tracked for user {}: {} on node {}", userId, sessionId, nodeId);
        } catch (Exception e) {
            log.error("Failed to track session for user {}", userId, e);
        }
    }

    /**
     * Remove session tracking when user logs out
     */
    public void untrackUserSession(Long userId, String sessionId) {
        try {
            // Remove from global active sessions
            redisTemplate.opsForSet().remove(ACTIVE_SESSIONS_KEY, sessionId);
            
            // Remove from user-specific sessions
            String userSessionsKey = USER_SESSIONS_PREFIX + userId;
            redisTemplate.opsForSet().remove(userSessionsKey, sessionId);
            
            // Unregister from distributed session registry
            distributedSessionService.unregisterDistributedSession(sessionId, userId);
            
            // Broadcast session destruction event
            distributedSessionService.broadcastSessionEvent("SESSION_DESTROYED", sessionId, userId);
            
            log.debug("Session untracked for user {}: {}", userId, sessionId);
        } catch (Exception e) {
            log.error("Failed to untrack session for user {}", userId, e);
        }
    }

    /**
     * Get all active sessions for a user
     */
    public Set<String> getUserActiveSessions(Long userId) {
        try {
            String userSessionsKey = USER_SESSIONS_PREFIX + userId;
            return redisTemplate.opsForSet().members(userSessionsKey);
        } catch (Exception e) {
            log.error("Failed to get active sessions for user {}", userId, e);
            return Set.of();
        }
    }

    /**
     * Invalidate all sessions for a user (useful for security purposes)
     */
    public void invalidateAllUserSessions(Long userId) {
        try {
            Set<String> userSessions = getUserActiveSessions(userId);
            
            for (String sessionId : userSessions) {
                try {
                    sessionRepository.deleteById(sessionId);
                    untrackUserSession(userId, sessionId);
                    log.debug("Invalidated session: {}", sessionId);
                } catch (Exception e) {
                    log.warn("Failed to invalidate session: {}", sessionId, e);
                }
            }
            
            // Clear user sessions tracking
            String userSessionsKey = USER_SESSIONS_PREFIX + userId;
            redisTemplate.delete(userSessionsKey);
            
            // Invalidate distributed sessions
            distributedSessionService.invalidateUserSessionsDistributed(userId);
            
            log.info("Invalidated {} sessions for user {}", userSessions.size(), userId);
        } catch (Exception e) {
            log.error("Failed to invalidate sessions for user {}", userId, e);
        }
    }

    /**
     * Check if a session is active
     */
    public boolean isSessionActive(String sessionId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(ACTIVE_SESSIONS_KEY, sessionId));
        } catch (Exception e) {
            log.error("Failed to check session status: {}", sessionId, e);
            return false;
        }
    }

    /**
     * Get total number of active sessions
     */
    public long getActiveSessionCount() {
        try {
            Long count = redisTemplate.opsForSet().size(ACTIVE_SESSIONS_KEY);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("Failed to get active session count", e);
            return 0;
        }
    }

    /**
     * Cleanup expired sessions (scheduled task)
     */
    public void cleanupExpiredSessions() {
        try {
            Set<String> activeSessions = redisTemplate.opsForSet().members(ACTIVE_SESSIONS_KEY);
            if (activeSessions == null) return;

            int cleanedCount = 0;
            for (String sessionId : activeSessions) {
                try {
                    Session session = sessionRepository.findById(sessionId);
                    if (session == null || session.isExpired()) {
                        redisTemplate.opsForSet().remove(ACTIVE_SESSIONS_KEY, sessionId);
                        cleanedCount++;
                    }
                } catch (Exception e) {
                    log.debug("Error checking session {}, removing from tracking", sessionId);
                    redisTemplate.opsForSet().remove(ACTIVE_SESSIONS_KEY, sessionId);
                    cleanedCount++;
                }
            }

            // Also cleanup orphaned distributed sessions
            distributedSessionService.cleanupOrphanedSessions();

            if (cleanedCount > 0) {
                log.info("Cleaned up {} expired sessions", cleanedCount);
            }
        } catch (Exception e) {
            log.error("Failed to cleanup expired sessions", e);
        }
    }

    /**
     * Store session metadata for audit purposes
     */
    public void storeSessionMetadata(String sessionId, Long userId, String userAgent, String ipAddress) {
        try {
            String metadataKey = "bantuops:session_metadata:" + sessionId;
            
            redisTemplate.opsForHash().putAll(metadataKey, Map.of(
                "userId", userId.toString(),
                "userAgent", userAgent != null ? userAgent : "unknown",
                "ipAddress", ipAddress != null ? ipAddress : "unknown",
                "createdAt", Instant.now().toString()
            ));
            
            // Set expiration for metadata (25 hours to outlive session)
            redisTemplate.expire(metadataKey, Duration.ofHours(25));
            
            log.debug("Session metadata stored for session: {}", sessionId);
        } catch (Exception e) {
            log.error("Failed to store session metadata for session: {}", sessionId, e);
        }
    }

    /**
     * Get session metadata
     */
    public Map<String, String> getSessionMetadata(String sessionId) {
        try {
            String metadataKey = "bantuops:session_metadata:" + sessionId;
            return redisTemplate.opsForHash().entries(metadataKey);
        } catch (Exception e) {
            log.error("Failed to get session metadata for session: {}", sessionId, e);
            return Map.of();
        }
    }

    /**
     * Set session timeout for a specific session
     */
    public void setSessionTimeout(String sessionId, Duration timeout) {
        try {
            Session session = sessionRepository.findById(sessionId);
            if (session != null) {
                session.setMaxInactiveInterval(timeout);
                sessionRepository.save(session);
                log.debug("Session timeout set to {} for session: {}", timeout, sessionId);
            }
        } catch (Exception e) {
            log.error("Failed to set session timeout for session: {}", sessionId, e);
        }
    }

    /**
     * Get distributed session statistics
     */
    public Map<String, Object> getDistributedSessionStatistics() {
        try {
            Map<String, Object> localStats = Map.of(
                "localActiveSessions", getActiveSessionCount(),
                "nodeId", getNodeId()
            );
            
            Map<String, Object> distributedStats = distributedSessionService.getDistributedSessionStatistics();
            
            return Map.of(
                "local", localStats,
                "distributed", distributedStats,
                "timestamp", Instant.now().toString()
            );
        } catch (Exception e) {
            log.error("Failed to get distributed session statistics", e);
            return Map.of("error", "Failed to retrieve statistics");
        }
    }

    /**
     * Check if session exists in distributed registry
     */
    public boolean isSessionDistributed(String sessionId) {
        return distributedSessionService.isSessionRegistered(sessionId);
    }

    /**
     * Get the node where a session is active
     */
    public String getSessionNode(String sessionId) {
        return distributedSessionService.getSessionNode(sessionId);
    }

    /**
     * Get current node ID
     */
    private String getNodeId() {
        return System.getProperty("bantuops.node.id", "node-" + System.currentTimeMillis() % 1000);
    }

    // Méthodes pour le système d'alertes de sécurité

    /**
     * Invalide les sessions utilisateur (alias pour compatibilité)
     */
    public void invalidateUserSessions(String userId) {
        try {
            Long userIdLong = Long.parseLong(userId);
            invalidateAllUserSessions(userIdLong);
        } catch (NumberFormatException e) {
            log.error("Invalid user ID format: {}", userId, e);
        }
    }

    /**
     * Révoque les tokens d'accès d'un utilisateur
     */
    public void revokeUserTokens(String userId) {
        try {
            String tokenKey = "bantuops:user_tokens:" + userId;
            redisTemplate.delete(tokenKey);
            log.info("Tokens révoqués pour l'utilisateur: {}", userId);
        } catch (Exception e) {
            log.error("Erreur lors de la révocation des tokens pour l'utilisateur: {}", userId, e);
        }
    }

    /**
     * Applique une limitation de débit
     */
    public void applyRateLimit(String userId, String ipAddress, int maxRequests, int windowSeconds) {
        try {
            String rateLimitKey = "bantuops:rate_limit:" + userId + ":" + ipAddress;
            redisTemplate.opsForValue().set(rateLimitKey, String.valueOf(maxRequests), 
                Duration.ofSeconds(windowSeconds));
            log.info("Limitation de débit appliquée: {} requêtes/{} secondes pour {}@{}", 
                maxRequests, windowSeconds, userId, ipAddress);
        } catch (Exception e) {
            log.error("Erreur lors de l'application de la limitation de débit", e);
        }
    }

    /**
     * Bloque une adresse IP
     */
    public void blockIpAddress(String ipAddress, String reason, int hours) {
        try {
            String blockKey = "bantuops:blocked_ips:" + ipAddress;
            redisTemplate.opsForValue().set(blockKey, reason, Duration.ofHours(hours));
            log.warn("IP bloquée: {} pour {} heures - Raison: {}", ipAddress, hours, reason);
        } catch (Exception e) {
            log.error("Erreur lors du blocage de l'IP: {}", ipAddress, e);
        }
    }

    /**
     * Invalide les sessions par adresse IP
     */
    public void invalidateSessionsByIp(String ipAddress) {
        try {
            Set<String> activeSessions = redisTemplate.opsForSet().members(ACTIVE_SESSIONS_KEY);
            if (activeSessions == null) return;

            int invalidatedCount = 0;
            for (String sessionId : activeSessions) {
                Map<String, String> metadata = getSessionMetadata(sessionId);
                if (ipAddress.equals(metadata.get("ipAddress"))) {
                    sessionRepository.deleteById(sessionId);
                    redisTemplate.opsForSet().remove(ACTIVE_SESSIONS_KEY, sessionId);
                    invalidatedCount++;
                }
            }
            log.info("Invalidé {} sessions pour l'IP: {}", invalidatedCount, ipAddress);
        } catch (Exception e) {
            log.error("Erreur lors de l'invalidation des sessions pour l'IP: {}", ipAddress, e);
        }
    }

    /**
     * Bloque toutes les nouvelles sessions
     */
    public void blockAllNewSessions() {
        try {
            redisTemplate.opsForValue().set("bantuops:block_new_sessions", "true", Duration.ofHours(1));
            log.error("Blocage de toutes les nouvelles sessions activé");
        } catch (Exception e) {
            log.error("Erreur lors du blocage des nouvelles sessions", e);
        }
    }

    /**
     * Invalide toutes les sessions non-admin
     */
    public void invalidateAllNonAdminSessions() {
        try {
            Set<String> activeSessions = redisTemplate.opsForSet().members(ACTIVE_SESSIONS_KEY);
            if (activeSessions == null) return;

            int invalidatedCount = 0;
            for (String sessionId : activeSessions) {
                Map<String, String> metadata = getSessionMetadata(sessionId);
                String userId = metadata.get("userId");
                
                // Vérifier si l'utilisateur n'est pas admin (logique simplifiée)
                if (userId != null && !isAdminUser(userId)) {
                    sessionRepository.deleteById(sessionId);
                    redisTemplate.opsForSet().remove(ACTIVE_SESSIONS_KEY, sessionId);
                    invalidatedCount++;
                }
            }
            log.error("Invalidé {} sessions non-admin", invalidatedCount);
        } catch (Exception e) {
            log.error("Erreur lors de l'invalidation des sessions non-admin", e);
        }
    }

    /**
     * Active le mode maintenance
     */
    public void enableMaintenanceMode() {
        try {
            redisTemplate.opsForValue().set("bantuops:maintenance_mode", "true", Duration.ofHours(24));
            log.error("Mode maintenance activé");
        } catch (Exception e) {
            log.error("Erreur lors de l'activation du mode maintenance", e);
        }
    }

    /**
     * Arrête les services non essentiels
     */
    public void stopNonEssentialServices() {
        try {
            redisTemplate.opsForValue().set("bantuops:stop_non_essential", "true", Duration.ofHours(1));
            log.error("Arrêt des services non essentiels demandé");
        } catch (Exception e) {
            log.error("Erreur lors de l'arrêt des services non essentiels", e);
        }
    }

    /**
     * Initie une sauvegarde d'urgence
     */
    public void initiateEmergencyBackup() {
        try {
            redisTemplate.opsForValue().set("bantuops:emergency_backup", "requested", Duration.ofMinutes(30));
            log.error("Sauvegarde d'urgence initiée");
        } catch (Exception e) {
            log.error("Erreur lors de l'initiation de la sauvegarde d'urgence", e);
        }
    }

    /**
     * Isole le système
     */
    public void isolateSystem() {
        try {
            redisTemplate.opsForValue().set("bantuops:system_isolated", "true", Duration.ofHours(1));
            log.error("Système isolé");
        } catch (Exception e) {
            log.error("Erreur lors de l'isolation du système", e);
        }
    }

    /**
     * Active l'audit détaillé pour un utilisateur
     */
    public void enableDetailedAudit(String userId, int hours) {
        try {
            String auditKey = "bantuops:detailed_audit:" + userId;
            redisTemplate.opsForValue().set(auditKey, "enabled", Duration.ofHours(hours));
            log.info("Audit détaillé activé pour l'utilisateur: {} pendant {} heures", userId, hours);
        } catch (Exception e) {
            log.error("Erreur lors de l'activation de l'audit détaillé pour: {}", userId, e);
        }
    }

    /**
     * Active l'enregistrement complet des actions
     */
    public void enableFullActionLogging(String userId) {
        try {
            String loggingKey = "bantuops:full_logging:" + userId;
            redisTemplate.opsForValue().set(loggingKey, "enabled", Duration.ofHours(48));
            log.info("Enregistrement complet des actions activé pour: {}", userId);
        } catch (Exception e) {
            log.error("Erreur lors de l'activation de l'enregistrement complet pour: {}", userId, e);
        }
    }

    /**
     * Restreint l'accès aux données sensibles
     */
    public void restrictSensitiveDataAccess(String userId) {
        try {
            String restrictKey = "bantuops:restrict_sensitive:" + userId;
            redisTemplate.opsForValue().set(restrictKey, "restricted", Duration.ofHours(24));
            log.warn("Accès aux données sensibles restreint pour: {}", userId);
        } catch (Exception e) {
            log.error("Erreur lors de la restriction d'accès pour: {}", userId, e);
        }
    }

    /**
     * Exige une double authentification
     */
    public void requireTwoFactorAuth(String userId) {
        try {
            String twoFactorKey = "bantuops:require_2fa:" + userId;
            redisTemplate.opsForValue().set(twoFactorKey, "required", Duration.ofHours(24));
            log.info("Double authentification requise pour: {}", userId);
        } catch (Exception e) {
            log.error("Erreur lors de l'activation de la 2FA pour: {}", userId, e);
        }
    }

    /**
     * Active la surveillance en temps réel
     */
    public void enableRealTimeMonitoring(String userId) {
        try {
            String monitorKey = "bantuops:realtime_monitor:" + userId;
            redisTemplate.opsForValue().set(monitorKey, "enabled", Duration.ofHours(48));
            log.info("Surveillance temps réel activée pour: {}", userId);
        } catch (Exception e) {
            log.error("Erreur lors de l'activation de la surveillance pour: {}", userId, e);
        }
    }

    /**
     * Définit des seuils d'alerte bas
     */
    public void setLowAlertThresholds(String userId) {
        try {
            String thresholdKey = "bantuops:low_thresholds:" + userId;
            redisTemplate.opsForValue().set(thresholdKey, "enabled", Duration.ofHours(48));
            log.info("Seuils d'alerte bas définis pour: {}", userId);
        } catch (Exception e) {
            log.error("Erreur lors de la définition des seuils pour: {}", userId, e);
        }
    }

    /**
     * Active la surveillance standard
     */
    public void enableStandardMonitoring(String userId) {
        try {
            String monitorKey = "bantuops:standard_monitor:" + userId;
            redisTemplate.opsForValue().set(monitorKey, "enabled", Duration.ofHours(24));
            log.info("Surveillance standard activée pour: {}", userId);
        } catch (Exception e) {
            log.error("Erreur lors de l'activation de la surveillance standard pour: {}", userId, e);
        }
    }

    /**
     * Vérifie si un utilisateur est administrateur
     */
    private boolean isAdminUser(String userId) {
        // Logique simplifiée - dans un vrai système, vérifier les rôles en base
        try {
            String adminKey = "bantuops:admin_users:" + userId;
            return Boolean.TRUE.equals(redisTemplate.hasKey(adminKey));
        } catch (Exception e) {
            log.error("Erreur lors de la vérification du statut admin pour: {}", userId, e);
            return false;
        }
    }
}