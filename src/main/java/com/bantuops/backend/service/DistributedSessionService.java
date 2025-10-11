package com.bantuops.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing distributed sessions across multiple application instances
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedSessionService {

    private final RedisTemplate<String, String> distributedLockTemplate;
    private final RedisTemplate<String, Object> sessionRedisTemplate;

    private static final String DISTRIBUTED_LOCK_PREFIX = "bantuops:lock:";
    private static final String SESSION_REGISTRY_KEY = "bantuops:session_registry";
    private static final String USER_SESSION_MAPPING_PREFIX = "bantuops:user_session_mapping:";
    private static final String SESSION_NODE_MAPPING_PREFIX = "bantuops:session_node:";
    private static final Duration DEFAULT_LOCK_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Register a session in the distributed registry
     */
    public void registerDistributedSession(String sessionId, Long userId, String nodeId) {
        try {
            String lockKey = DISTRIBUTED_LOCK_PREFIX + "session_registry";
            
            if (acquireDistributedLock(lockKey, DEFAULT_LOCK_TIMEOUT)) {
                try {
                    // Add session to global registry
                    sessionRedisTemplate.opsForSet().add(SESSION_REGISTRY_KEY, sessionId);
                    
                    // Map user to session
                    String userSessionKey = USER_SESSION_MAPPING_PREFIX + userId;
                    sessionRedisTemplate.opsForSet().add(userSessionKey, sessionId);
                    sessionRedisTemplate.expire(userSessionKey, Duration.ofHours(25));
                    
                    // Map session to node
                    String sessionNodeKey = SESSION_NODE_MAPPING_PREFIX + sessionId;
                    sessionRedisTemplate.opsForValue().set(sessionNodeKey, nodeId, Duration.ofHours(25));
                    
                    log.debug("Registered distributed session {} for user {} on node {}", sessionId, userId, nodeId);
                } finally {
                    releaseDistributedLock(lockKey);
                }
            } else {
                log.warn("Failed to acquire lock for session registration: {}", sessionId);
            }
        } catch (Exception e) {
            log.error("Failed to register distributed session: {}", sessionId, e);
        }
    }

    /**
     * Unregister a session from the distributed registry
     */
    public void unregisterDistributedSession(String sessionId, Long userId) {
        try {
            String lockKey = DISTRIBUTED_LOCK_PREFIX + "session_registry";
            
            if (acquireDistributedLock(lockKey, DEFAULT_LOCK_TIMEOUT)) {
                try {
                    // Remove from global registry
                    sessionRedisTemplate.opsForSet().remove(SESSION_REGISTRY_KEY, sessionId);
                    
                    // Remove from user session mapping
                    String userSessionKey = USER_SESSION_MAPPING_PREFIX + userId;
                    sessionRedisTemplate.opsForSet().remove(userSessionKey, sessionId);
                    
                    // Remove session node mapping
                    String sessionNodeKey = SESSION_NODE_MAPPING_PREFIX + sessionId;
                    sessionRedisTemplate.delete(sessionNodeKey);
                    
                    log.debug("Unregistered distributed session {} for user {}", sessionId, userId);
                } finally {
                    releaseDistributedLock(lockKey);
                }
            } else {
                log.warn("Failed to acquire lock for session unregistration: {}", sessionId);
            }
        } catch (Exception e) {
            log.error("Failed to unregister distributed session: {}", sessionId, e);
        }
    }

    /**
     * Get all active sessions across all nodes
     */
    public Set<String> getAllDistributedSessions() {
        try {
            return sessionRedisTemplate.opsForSet().members(SESSION_REGISTRY_KEY);
        } catch (Exception e) {
            log.error("Failed to get all distributed sessions", e);
            return Set.of();
        }
    }

    /**
     * Get all sessions for a specific user across all nodes
     */
    public Set<String> getUserDistributedSessions(Long userId) {
        try {
            String userSessionKey = USER_SESSION_MAPPING_PREFIX + userId;
            return sessionRedisTemplate.opsForSet().members(userSessionKey);
        } catch (Exception e) {
            log.error("Failed to get distributed sessions for user: {}", userId, e);
            return Set.of();
        }
    }

    /**
     * Get the node ID where a session is active
     */
    public String getSessionNode(String sessionId) {
        try {
            String sessionNodeKey = SESSION_NODE_MAPPING_PREFIX + sessionId;
            return (String) sessionRedisTemplate.opsForValue().get(sessionNodeKey);
        } catch (Exception e) {
            log.error("Failed to get session node for session: {}", sessionId, e);
            return null;
        }
    }

    /**
     * Check if a session exists in the distributed registry
     */
    public boolean isSessionRegistered(String sessionId) {
        try {
            return Boolean.TRUE.equals(sessionRedisTemplate.opsForSet().isMember(SESSION_REGISTRY_KEY, sessionId));
        } catch (Exception e) {
            log.error("Failed to check session registration: {}", sessionId, e);
            return false;
        }
    }

    /**
     * Invalidate all sessions for a user across all nodes
     */
    public void invalidateUserSessionsDistributed(Long userId) {
        try {
            Set<String> userSessions = getUserDistributedSessions(userId);
            
            for (String sessionId : userSessions) {
                unregisterDistributedSession(sessionId, userId);
            }
            
            log.info("Invalidated {} distributed sessions for user {}", userSessions.size(), userId);
        } catch (Exception e) {
            log.error("Failed to invalidate distributed sessions for user: {}", userId, e);
        }
    }

    /**
     * Cleanup orphaned sessions (sessions without corresponding node)
     */
    public void cleanupOrphanedSessions() {
        try {
            Set<String> allSessions = getAllDistributedSessions();
            int cleanedCount = 0;
            
            for (String sessionId : allSessions) {
                String nodeId = getSessionNode(sessionId);
                if (nodeId == null) {
                    // Session exists in registry but no node mapping - cleanup
                    sessionRedisTemplate.opsForSet().remove(SESSION_REGISTRY_KEY, sessionId);
                    cleanedCount++;
                    log.debug("Cleaned orphaned session: {}", sessionId);
                }
            }
            
            if (cleanedCount > 0) {
                log.info("Cleaned up {} orphaned distributed sessions", cleanedCount);
            }
        } catch (Exception e) {
            log.error("Failed to cleanup orphaned sessions", e);
        }
    }

    /**
     * Get session statistics across all nodes
     */
    public Map<String, Object> getDistributedSessionStatistics() {
        try {
            Set<String> allSessions = getAllDistributedSessions();
            
            return Map.of(
                "totalSessions", allSessions.size(),
                "timestamp", Instant.now().toString(),
                "registryKey", SESSION_REGISTRY_KEY
            );
        } catch (Exception e) {
            log.error("Failed to get distributed session statistics", e);
            return Map.of("error", "Failed to retrieve statistics");
        }
    }

    /**
     * Acquire a distributed lock
     */
    private boolean acquireDistributedLock(String lockKey, Duration timeout) {
        try {
            String lockValue = UUID.randomUUID().toString();
            Boolean acquired = distributedLockTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, timeout.toMillis(), TimeUnit.MILLISECONDS);
            
            if (Boolean.TRUE.equals(acquired)) {
                log.debug("Acquired distributed lock: {}", lockKey);
                return true;
            } else {
                log.debug("Failed to acquire distributed lock: {}", lockKey);
                return false;
            }
        } catch (Exception e) {
            log.error("Error acquiring distributed lock: {}", lockKey, e);
            return false;
        }
    }

    /**
     * Release a distributed lock
     */
    private void releaseDistributedLock(String lockKey) {
        try {
            distributedLockTemplate.delete(lockKey);
            log.debug("Released distributed lock: {}", lockKey);
        } catch (Exception e) {
            log.error("Error releasing distributed lock: {}", lockKey, e);
        }
    }

    /**
     * Broadcast session event to all nodes (for future implementation with messaging)
     */
    public void broadcastSessionEvent(String eventType, String sessionId, Long userId) {
        try {
            String eventKey = "bantuops:session_events";
            Map<String, Object> event = Map.of(
                "type", eventType,
                "sessionId", sessionId,
                "userId", userId.toString(),
                "timestamp", Instant.now().toString(),
                "nodeId", getNodeId()
            );
            
            sessionRedisTemplate.opsForList().leftPush(eventKey, event);
            sessionRedisTemplate.expire(eventKey, Duration.ofMinutes(10)); // Keep events for 10 minutes
            
            log.debug("Broadcasted session event: {} for session: {}", eventType, sessionId);
        } catch (Exception e) {
            log.error("Failed to broadcast session event: {}", eventType, e);
        }
    }

    /**
     * Get current node ID (can be enhanced with actual node identification)
     */
    private String getNodeId() {
        // For now, use a simple approach. In production, this could be:
        // - Container ID
        // - Server hostname
        // - Kubernetes pod name
        // - Custom node identifier
        return System.getProperty("bantuops.node.id", "node-" + System.currentTimeMillis() % 1000);
    }
}