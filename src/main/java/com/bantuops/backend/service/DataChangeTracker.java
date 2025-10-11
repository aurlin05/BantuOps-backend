package com.bantuops.backend.service;

import com.bantuops.backend.entity.AuditLog;
import com.bantuops.backend.entity.DataChangeHistory;
import com.bantuops.backend.entity.FieldLevelAudit;
import com.bantuops.backend.repository.AuditLogRepository;
import com.bantuops.backend.repository.DataChangeHistoryRepository;
import com.bantuops.backend.repository.FieldLevelAuditRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service de suivi des modifications de données avec historique des versions
 * Conforme aux exigences 7.4, 7.5, 7.6 pour la traçabilité complète
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataChangeTracker {

    private final AuditLogRepository auditLogRepository;
    private final DataChangeHistoryRepository dataChangeHistoryRepository;
    private final FieldLevelAuditRepository fieldLevelAuditRepository;
    private final ObjectMapper objectMapper;

    // Cache des versions d'entités pour éviter les requêtes répétées
    private final Map<String, Long> entityVersionCache = new ConcurrentHashMap<>();

    // Configuration des champs sensibles par type d'entité
    private static final Map<String, Set<String>> SENSITIVE_FIELDS = Map.of(
        "Employee", Set.of("personalInfo.firstName", "personalInfo.lastName", "personalInfo.email", 
                          "personalInfo.phoneNumber", "personalInfo.nationalId", "employmentInfo.baseSalary"),
        "PayrollRecord", Set.of("grossSalary", "netSalary", "incomeTax", "socialContributions"),
        "Invoice", Set.of("totalAmount", "vatAmount"),
        "Transaction", Set.of("amount", "accountNumber")
    );

    /**
     * Enregistre une modification d'entité avec historique complet
     */
    @Async
    @Transactional
    public void trackEntityChange(Object oldEntity, Object newEntity, AuditLog.AuditAction action, String reason) {
        try {
            String entityType = getEntityType(newEntity != null ? newEntity : oldEntity);
            Long entityId = getEntityId(newEntity != null ? newEntity : oldEntity);
            
            if (entityId == null) {
                log.warn("Impossible de déterminer l'ID de l'entité pour le suivi: {}", entityType);
                return;
            }

            String userId = getCurrentUserId();
            String userRole = getCurrentUserRole();
            Long version = getNextVersion(entityType, entityId);

            // Créer le log d'audit principal
            AuditLog auditLog = createAuditLog(oldEntity, newEntity, action, entityType, entityId, userId, userRole, reason);
            auditLogRepository.save(auditLog);

            // Créer l'historique des versions
            if (newEntity != null) {
                DataChangeHistory changeHistory = createChangeHistory(newEntity, entityType, entityId, version, userId, reason);
                dataChangeHistoryRepository.save(changeHistory);
            }

            // Analyser les changements au niveau des champs
            if (oldEntity != null && newEntity != null && action == AuditLog.AuditAction.UPDATE) {
                trackFieldLevelChanges(oldEntity, newEntity, entityType, entityId, userId, userRole);
            }

            // Mettre à jour le cache des versions
            updateVersionCache(entityType, entityId, version);

            log.debug("Modification trackée: {} {} version {}", entityType, entityId, version);

        } catch (Exception e) {
            log.error("Erreur lors du suivi de modification: {}", e.getMessage(), e);
        }
    }

    /**
     * Enregistre l'accès à un champ sensible
     */
    @Async
    @Transactional
    public void trackFieldAccess(String entityType, Long entityId, String fieldName, 
                                FieldLevelAudit.AccessType accessType, Object value, String context) {
        try {
            String userId = getCurrentUserId();
            String userRole = getCurrentUserRole();
            
            FieldLevelAudit.SensitiveLevel sensitiveLevel = determineSensitiveLevel(entityType, fieldName);
            
            FieldLevelAudit fieldAudit = FieldLevelAudit.builder()
                .entityType(entityType)
                .entityId(entityId)
                .fieldName(fieldName)
                .accessType(accessType)
                .accessedBy(userId)
                .userRole(userRole)
                .sensitiveLevel(sensitiveLevel)
                .accessContext(context)
                .ipAddress(getCurrentIpAddress())
                .sessionId(getCurrentSessionId())
                .authorized(true) // TODO: Implémenter la vérification d'autorisation
                .build();

            // Hasher la valeur si c'est un champ sensible
            if (sensitiveLevel == FieldLevelAudit.SensitiveLevel.HIGH || 
                sensitiveLevel == FieldLevelAudit.SensitiveLevel.CRITICAL) {
                if (accessType == FieldLevelAudit.AccessType.WRITE) {
                    fieldAudit.setNewValueHash(hashValue(value));
                } else {
                    fieldAudit.setOldValueHash(hashValue(value));
                }
            }

            fieldLevelAuditRepository.save(fieldAudit);

        } catch (Exception e) {
            log.error("Erreur lors du suivi d'accès au champ: {}", e.getMessage(), e);
        }
    }

    /**
     * Récupère l'historique des versions d'une entité
     */
    public List<DataChangeHistory> getEntityHistory(String entityType, Long entityId) {
        return dataChangeHistoryRepository.findByEntityTypeAndEntityIdOrderByVersionDesc(
            entityType, entityId, org.springframework.data.domain.Pageable.unpaged()).getContent();
    }

    /**
     * Récupère une version spécifique d'une entité
     */
    public Optional<DataChangeHistory> getEntityVersion(String entityType, Long entityId, Long version) {
        return dataChangeHistoryRepository.findByEntityTypeAndEntityIdAndVersion(entityType, entityId, version);
    }

    /**
     * Restaure une entité à une version antérieure
     */
    @Transactional
    public boolean restoreEntityVersion(String entityType, Long entityId, Long version, String reason) {
        try {
            Optional<DataChangeHistory> historyOpt = getEntityVersion(entityType, entityId, version);
            if (historyOpt.isEmpty()) {
                log.warn("Version {} non trouvée pour {} {}", version, entityType, entityId);
                return false;
            }

            DataChangeHistory history = historyOpt.get();
            
            // Créer un nouvel enregistrement d'historique pour la restauration
            Long newVersion = getNextVersion(entityType, entityId);
            DataChangeHistory restoreHistory = DataChangeHistory.builder()
                .entityType(entityType)
                .entityId(entityId)
                .version(newVersion)
                .entitySnapshot(history.getEntitySnapshot())
                .changeType(DataChangeHistory.ChangeType.SYSTEM_UPDATE)
                .changedBy(getCurrentUserId())
                .changeReason("Restauration vers version " + version + ": " + reason)
                .sensitiveField(false)
                .integrityHash(calculateIntegrityHash(history.getEntitySnapshot()))
                .build();

            dataChangeHistoryRepository.save(restoreHistory);

            // Créer un log d'audit pour la restauration
            AuditLog auditLog = AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(AuditLog.AuditAction.UPDATE)
                .userId(getCurrentUserId())
                .userRole(getCurrentUserRole())
                .reason("Restauration vers version " + version + ": " + reason)
                .ipAddress(getCurrentIpAddress())
                .sessionId(getCurrentSessionId())
                .sensitiveData(containsSensitiveData(entityType))
                .entityVersion(newVersion)
                .build();

            auditLogRepository.save(auditLog);

            updateVersionCache(entityType, entityId, newVersion);

            log.info("Entité {} {} restaurée vers version {}", entityType, entityId, version);
            return true;

        } catch (Exception e) {
            log.error("Erreur lors de la restauration: {}", e.getMessage(), e);
            return false;
        }
    }

    // ==================== MÉTHODES PRIVÉES ====================

    private AuditLog createAuditLog(Object oldEntity, Object newEntity, AuditLog.AuditAction action,
                                   String entityType, Long entityId, String userId, String userRole, String reason) {
        try {
            Map<String, Object> oldValues = oldEntity != null ? objectToMap(oldEntity) : null;
            Map<String, Object> newValues = newEntity != null ? objectToMap(newEntity) : null;
            
            Set<String> changedFields = getChangedFields(oldValues, newValues);

            return AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .userId(userId)
                .userRole(userRole)
                .oldValues(oldValues != null ? objectMapper.writeValueAsString(oldValues) : null)
                .newValues(newValues != null ? objectMapper.writeValueAsString(newValues) : null)
                .changedFields(String.join(",", changedFields))
                .ipAddress(getCurrentIpAddress())
                .userAgent(getCurrentUserAgent())
                .sessionId(getCurrentSessionId())
                .reason(reason)
                .sensitiveData(containsSensitiveData(entityType))
                .entityVersion(getNextVersion(entityType, entityId))
                .build();

        } catch (JsonProcessingException e) {
            log.error("Erreur lors de la sérialisation pour l'audit: {}", e.getMessage());
            throw new RuntimeException("Erreur lors de la création du log d'audit", e);
        }
    }

    private DataChangeHistory createChangeHistory(Object entity, String entityType, Long entityId, 
                                                 Long version, String userId, String reason) {
        try {
            String entitySnapshot = objectMapper.writeValueAsString(entity);
            
            return DataChangeHistory.builder()
                .entityType(entityType)
                .entityId(entityId)
                .version(version)
                .entitySnapshot(entitySnapshot)
                .changeType(DataChangeHistory.ChangeType.ENTITY_CREATE)
                .changedBy(userId)
                .changeReason(reason)
                .sensitiveField(containsSensitiveData(entityType))
                .integrityHash(calculateIntegrityHash(entitySnapshot))
                .build();

        } catch (JsonProcessingException e) {
            log.error("Erreur lors de la création de l'historique: {}", e.getMessage());
            throw new RuntimeException("Erreur lors de la création de l'historique", e);
        }
    }

    private void trackFieldLevelChanges(Object oldEntity, Object newEntity, String entityType, 
                                       Long entityId, String userId, String userRole) {
        try {
            Map<String, Object> oldValues = objectToMap(oldEntity);
            Map<String, Object> newValues = objectToMap(newEntity);

            for (String fieldName : newValues.keySet()) {
                Object oldValue = oldValues.get(fieldName);
                Object newValue = newValues.get(fieldName);

                if (!Objects.equals(oldValue, newValue)) {
                    // Créer un enregistrement d'historique pour ce champ
                    DataChangeHistory fieldHistory = DataChangeHistory.builder()
                        .entityType(entityType)
                        .entityId(entityId)
                        .version(getNextVersion(entityType, entityId))
                        .entitySnapshot(null) // Pas de snapshot complet pour les changements de champ
                        .fieldName(fieldName)
                        .oldValue(oldValue != null ? oldValue.toString() : null)
                        .newValue(newValue != null ? newValue.toString() : null)
                        .changeType(DataChangeHistory.ChangeType.FIELD_UPDATE)
                        .changedBy(userId)
                        .sensitiveField(isSensitiveField(entityType, fieldName))
                        .build();

                    dataChangeHistoryRepository.save(fieldHistory);

                    // Enregistrer l'accès au niveau du champ si c'est sensible
                    if (isSensitiveField(entityType, fieldName)) {
                        trackFieldAccess(entityType, entityId, fieldName, 
                                       FieldLevelAudit.AccessType.WRITE, newValue, "Field Update");
                    }
                }
            }

        } catch (Exception e) {
            log.error("Erreur lors du suivi des changements de champs: {}", e.getMessage(), e);
        }
    }

    private Long getNextVersion(String entityType, Long entityId) {
        String cacheKey = entityType + ":" + entityId;
        return entityVersionCache.compute(cacheKey, (key, currentVersion) -> {
            if (currentVersion == null) {
                // Récupérer la dernière version depuis la base de données
                Optional<DataChangeHistory> latest = dataChangeHistoryRepository.findLatestVersion(entityType, entityId);
                return latest.map(h -> h.getVersion() + 1).orElse(1L);
            }
            return currentVersion + 1;
        });
    }

    private void updateVersionCache(String entityType, Long entityId, Long version) {
        String cacheKey = entityType + ":" + entityId;
        entityVersionCache.put(cacheKey, version);
    }

    private String getEntityType(Object entity) {
        return entity.getClass().getSimpleName();
    }

    private Long getEntityId(Object entity) {
        try {
            Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            return (Long) idField.get(entity);
        } catch (Exception e) {
            log.warn("Impossible de récupérer l'ID de l'entité: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> objectToMap(Object obj) {
        return objectMapper.convertValue(obj, Map.class);
    }

    private Set<String> getChangedFields(Map<String, Object> oldValues, Map<String, Object> newValues) {
        Set<String> changedFields = new HashSet<>();
        
        if (oldValues == null && newValues != null) {
            changedFields.addAll(newValues.keySet());
        } else if (oldValues != null && newValues != null) {
            for (String key : newValues.keySet()) {
                if (!Objects.equals(oldValues.get(key), newValues.get(key))) {
                    changedFields.add(key);
                }
            }
        }
        
        return changedFields;
    }

    private boolean isSensitiveField(String entityType, String fieldName) {
        Set<String> sensitiveFields = SENSITIVE_FIELDS.get(entityType);
        return sensitiveFields != null && sensitiveFields.contains(fieldName);
    }

    private boolean containsSensitiveData(String entityType) {
        return SENSITIVE_FIELDS.containsKey(entityType);
    }

    private FieldLevelAudit.SensitiveLevel determineSensitiveLevel(String entityType, String fieldName) {
        if (!isSensitiveField(entityType, fieldName)) {
            return FieldLevelAudit.SensitiveLevel.LOW;
        }

        // Données ultra-sensibles (salaires, données personnelles)
        if (fieldName.contains("salary") || fieldName.contains("nationalId") || 
            fieldName.contains("amount") || fieldName.contains("tax")) {
            return FieldLevelAudit.SensitiveLevel.CRITICAL;
        }

        // Données confidentielles
        if (fieldName.contains("email") || fieldName.contains("phone") || fieldName.contains("name")) {
            return FieldLevelAudit.SensitiveLevel.HIGH;
        }

        return FieldLevelAudit.SensitiveLevel.MEDIUM;
    }

    private String hashValue(Object value) {
        if (value == null) return null;
        
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(value.toString().getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("Erreur lors du hashage: {}", e.getMessage());
            return "HASH_ERROR";
        }
    }

    private String calculateIntegrityHash(String data) {
        return hashValue(data);
    }

    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "SYSTEM";
    }

    private String getCurrentUserRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities() != null && !auth.getAuthorities().isEmpty()) {
            return auth.getAuthorities().iterator().next().getAuthority();
        }
        return "UNKNOWN";
    }

    private String getCurrentIpAddress() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String xForwardedFor = request.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                    return xForwardedFor.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            log.debug("Impossible de récupérer l'adresse IP: {}", e.getMessage());
        }
        return "UNKNOWN";
    }

    private String getCurrentUserAgent() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                return attributes.getRequest().getHeader("User-Agent");
            }
        } catch (Exception e) {
            log.debug("Impossible de récupérer le User-Agent: {}", e.getMessage());
        }
        return "UNKNOWN";
    }

    private String getCurrentSessionId() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                return attributes.getRequest().getSession().getId();
            }
        } catch (Exception e) {
            log.debug("Impossible de récupérer l'ID de session: {}", e.getMessage());
        }
        return "UNKNOWN";
    }
}