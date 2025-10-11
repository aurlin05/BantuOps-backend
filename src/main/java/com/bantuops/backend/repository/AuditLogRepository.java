package com.bantuops.backend.repository;

import com.bantuops.backend.dto.SecurityAlert;
import com.bantuops.backend.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository pour les logs d'audit
 * Conforme aux exigences 7.4, 7.5, 7.6
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Trouve les logs d'audit pour une entité spécifique
     */
    Page<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampDesc(
            String entityType, Long entityId, Pageable pageable);

    /**
     * Trouve les logs d'audit pour un utilisateur
     */
    Page<AuditLog> findByUserIdOrderByTimestampDesc(String userId, Pageable pageable);

    /**
     * Trouve les logs d'audit par action
     */
    Page<AuditLog> findByActionOrderByTimestampDesc(AuditLog.AuditAction action, Pageable pageable);

    /**
     * Trouve les logs d'audit dans une période
     */
    @Query("SELECT a FROM AuditLog a WHERE a.timestamp BETWEEN :startDate AND :endDate ORDER BY a.timestamp DESC")
    Page<AuditLog> findByTimestampBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    /**
     * Trouve les logs d'audit pour des données sensibles
     */
    Page<AuditLog> findBySensitiveDataTrueOrderByTimestampDesc(Pageable pageable);

    /**
     * Trouve les logs d'audit par type d'entité et action
     */
    Page<AuditLog> findByEntityTypeAndActionOrderByTimestampDesc(
            String entityType, AuditLog.AuditAction action, Pageable pageable);

    /**
     * Compte les actions par utilisateur dans une période
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.userId = :userId AND a.timestamp BETWEEN :startDate AND :endDate")
    Long countByUserIdAndTimestampBetween(
            @Param("userId") String userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Trouve les logs d'audit par adresse IP (pour détecter les activités suspectes)
     */
    @Query("SELECT a FROM AuditLog a WHERE a.ipAddress = :ipAddress AND a.timestamp >= :since ORDER BY a.timestamp DESC")
    List<AuditLog> findByIpAddressAndTimestampAfter(
            @Param("ipAddress") String ipAddress,
            @Param("since") LocalDateTime since);

    /**
     * Trouve les logs d'audit avec des échecs d'authentification
     */
    @Query("SELECT a FROM AuditLog a WHERE a.action = 'VIEW' AND a.userId IS NULL AND a.timestamp >= :since")
    List<AuditLog> findFailedAccessAttemptsSince(@Param("since") LocalDateTime since);

    /**
     * Statistiques d'audit par type d'entité
     */
    @Query("SELECT a.entityType, COUNT(a) FROM AuditLog a WHERE a.timestamp BETWEEN :startDate AND :endDate GROUP BY a.entityType")
    List<Object[]> getAuditStatsByEntityType(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Statistiques d'audit par action
     */
    @Query("SELECT a.action, COUNT(a) FROM AuditLog a WHERE a.timestamp BETWEEN :startDate AND :endDate GROUP BY a.action")
    List<Object[]> getAuditStatsByAction(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Trouve les logs d'audit dans une période (pour ComplianceReportGenerator)
     */
    List<AuditLog> findByTimestampBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Trouve les événements de sécurité dans une période
     */
    @Query("SELECT a FROM AuditLog a WHERE a.timestamp BETWEEN :startDate AND :endDate " +
           "AND (a.action = 'DELETE' OR a.sensitiveData = true OR a.userId IS NULL) " +
           "ORDER BY a.timestamp DESC")
    List<AuditLog> findSecurityEventsByTimestampBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Trouve les tentatives d'accès non autorisées
     */
    @Query("SELECT a FROM AuditLog a WHERE a.timestamp BETWEEN :startDate AND :endDate " +
           "AND (a.userId IS NULL OR a.reason LIKE '%unauthorized%' OR a.reason LIKE '%denied%') " +
           "ORDER BY a.timestamp DESC")
    List<AuditLog> findUnauthorizedAccessAttempts(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Trouve les tentatives de suppression de logs d'audit
     */
    @Query("SELECT a FROM AuditLog a WHERE a.timestamp BETWEEN :startDate AND :endDate " +
           "AND a.entityType = 'AuditLog' AND a.action = 'DELETE' " +
           "ORDER BY a.timestamp DESC")
    List<AuditLog> findAuditDeletionAttempts(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    // Méthodes pour le système d'alertes de sécurité

    /**
     * Récupère les alertes de sécurité récentes
     */
    @Query("SELECT a FROM AuditLog a WHERE a.entityType = 'SECURITY_ALERT' " +
           "ORDER BY a.timestamp DESC")
    List<SecurityAlert> findRecentSecurityAlerts(@Param("limit") int limit);

    /**
     * Récupère les alertes de sécurité par utilisateur
     */
    @Query("SELECT a FROM AuditLog a WHERE a.entityType = 'SECURITY_ALERT' " +
           "AND a.userId = :userId AND a.timestamp >= :since " +
           "ORDER BY a.timestamp DESC")
    List<SecurityAlert> findSecurityAlertsByUser(@Param("userId") String userId, 
                                                @Param("since") LocalDateTime since);

    /**
     * Compte les tentatives de connexion échouées
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.userId = :userId " +
           "AND a.action = 'FAILED_LOGIN_ATTEMPT' AND a.timestamp >= :since")
    long countFailedLoginAttempts(@Param("userId") String userId, 
                                 @Param("since") LocalDateTime since);

    /**
     * Récupère les adresses IP des tentatives de connexion échouées
     */
    @Query("SELECT DISTINCT a.ipAddress FROM AuditLog a WHERE a.userId = :userId " +
           "AND a.action = 'FAILED_LOGIN_ATTEMPT' AND a.timestamp >= :since")
    List<String> getFailedLoginIpAddresses(@Param("userId") String userId, 
                                          @Param("since") LocalDateTime since);

    /**
     * Compte les tentatives d'accès non autorisé
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.userId = :userId " +
           "AND a.action = 'UNAUTHORIZED_ACCESS' AND a.timestamp >= :since")
    long countUnauthorizedAccess(@Param("userId") String userId, 
                                @Param("since") LocalDateTime since);

    /**
     * Compte les modifications de données sensibles
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.userId = :userId " +
           "AND a.action = 'SENSITIVE_DATA_MODIFICATION' AND a.timestamp >= :since")
    long countSensitiveDataModifications(@Param("userId") String userId, 
                                        @Param("since") LocalDateTime since);

    /**
     * Compte les exports de données
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.userId = :userId " +
           "AND a.action LIKE '%EXPORT%' AND a.timestamp >= :since")
    long countDataExports(@Param("userId") String userId, 
                         @Param("since") LocalDateTime since);

    /**
     * Compte les appels API
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.userId = :userId " +
           "AND a.entityType = 'API_CALL' AND a.timestamp >= :since")
    long countApiCalls(@Param("userId") String userId, 
                      @Param("since") LocalDateTime since);

    /**
     * Récupère les adresses IP habituelles d'un utilisateur
     */
    @Query("SELECT DISTINCT a.ipAddress FROM AuditLog a WHERE a.userId = :userId " +
           "AND a.timestamp >= :since GROUP BY a.ipAddress " +
           "HAVING COUNT(a.ipAddress) > 5 ORDER BY COUNT(a.ipAddress) DESC")
    List<String> getUserUsualIpAddresses(@Param("userId") String userId, 
                                        @Param("since") LocalDateTime since);

    /**
     * Récupère les ressources habituellement consultées par un utilisateur
     */
    @Query("SELECT DISTINCT a.entityType FROM AuditLog a WHERE a.userId = :userId " +
           "AND a.timestamp >= :since GROUP BY a.entityType " +
           "HAVING COUNT(a.entityType) > 10 ORDER BY COUNT(a.entityType) DESC")
    List<String> getUserUsualResources(@Param("userId") String userId, 
                                      @Param("since") LocalDateTime since);

    /**
     * Récupère l'activité récente d'un utilisateur
     */
    @Query("SELECT a FROM AuditLog a WHERE a.userId = :userId " +
           "AND a.timestamp >= :since ORDER BY a.timestamp DESC")
    List<AuditLog> getUserRecentActivity(@Param("userId") String userId, 
                                        @Param("since") LocalDateTime since);
}