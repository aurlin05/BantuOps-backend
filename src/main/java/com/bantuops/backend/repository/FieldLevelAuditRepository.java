package com.bantuops.backend.repository;

import com.bantuops.backend.entity.FieldLevelAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository pour l'audit au niveau des champs
 * Conforme aux exigences 7.4, 7.5, 7.6
 */
@Repository
public interface FieldLevelAuditRepository extends JpaRepository<FieldLevelAudit, Long> {

    /**
     * Trouve les accès à un champ spécifique d'une entité
     */
    Page<FieldLevelAudit> findByEntityTypeAndEntityIdAndFieldNameOrderByAccessTimestampDesc(
            String entityType, Long entityId, String fieldName, Pageable pageable);

    /**
     * Trouve les accès par utilisateur
     */
    Page<FieldLevelAudit> findByAccessedByOrderByAccessTimestampDesc(String accessedBy, Pageable pageable);

    /**
     * Trouve les accès par niveau de sensibilité
     */
    Page<FieldLevelAudit> findBySensitiveLevelOrderByAccessTimestampDesc(
            FieldLevelAudit.SensitiveLevel sensitiveLevel, Pageable pageable);

    /**
     * Trouve les accès non autorisés
     */
    Page<FieldLevelAudit> findByAuthorizedFalseOrderByAccessTimestampDesc(Pageable pageable);

    /**
     * Trouve les accès dans une période
     */
    @Query("SELECT f FROM FieldLevelAudit f WHERE f.accessTimestamp BETWEEN :startDate AND :endDate ORDER BY f.accessTimestamp DESC")
    Page<FieldLevelAudit> findByAccessTimestampBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    /**
     * Trouve les accès aux données critiques
     */
    Page<FieldLevelAudit> findBySensitiveLevelAndAccessTypeOrderByAccessTimestampDesc(
            FieldLevelAudit.SensitiveLevel sensitiveLevel,
            FieldLevelAudit.AccessType accessType,
            Pageable pageable);

    /**
     * Compte les accès par utilisateur dans une période
     */
    @Query("SELECT COUNT(f) FROM FieldLevelAudit f WHERE f.accessedBy = :userId AND f.accessTimestamp BETWEEN :startDate AND :endDate")
    Long countByUserAndTimestampBetween(
            @Param("userId") String userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Trouve les accès suspects (nombreux accès rapides)
     */
    @Query("SELECT f.accessedBy, COUNT(f) as accessCount FROM FieldLevelAudit f " +
           "WHERE f.accessTimestamp >= :since AND f.sensitiveLevel IN ('HIGH', 'CRITICAL') " +
           "GROUP BY f.accessedBy HAVING COUNT(f) > :threshold ORDER BY accessCount DESC")
    List<Object[]> findSuspiciousAccess(
            @Param("since") LocalDateTime since,
            @Param("threshold") Long threshold);

    /**
     * Trouve les accès par adresse IP
     */
    @Query("SELECT f FROM FieldLevelAudit f WHERE f.ipAddress = :ipAddress AND f.accessTimestamp >= :since ORDER BY f.accessTimestamp DESC")
    List<FieldLevelAudit> findByIpAddressAndAccessTimestampAfter(
            @Param("ipAddress") String ipAddress,
            @Param("since") LocalDateTime since);

    /**
     * Statistiques d'accès par type de champ
     */
    @Query("SELECT f.fieldName, COUNT(f) FROM FieldLevelAudit f " +
           "WHERE f.accessTimestamp BETWEEN :startDate AND :endDate " +
           "GROUP BY f.fieldName ORDER BY COUNT(f) DESC")
    List<Object[]> getAccessStatsByField(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Statistiques d'accès par niveau de sensibilité
     */
    @Query("SELECT f.sensitiveLevel, COUNT(f) FROM FieldLevelAudit f " +
           "WHERE f.accessTimestamp BETWEEN :startDate AND :endDate " +
           "GROUP BY f.sensitiveLevel")
    List<Object[]> getAccessStatsBySensitiveLevel(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Trouve les tentatives d'accès non autorisées récentes
     */
    @Query("SELECT f FROM FieldLevelAudit f WHERE f.authorized = false AND f.accessTimestamp >= :since ORDER BY f.accessTimestamp DESC")
    List<FieldLevelAudit> findUnauthorizedAccessSince(@Param("since") LocalDateTime since);

    /**
     * Trouve les accès aux données de paie (champs sensibles)
     */
    @Query("SELECT f FROM FieldLevelAudit f WHERE f.entityType = 'PayrollRecord' " +
           "AND f.sensitiveLevel IN ('HIGH', 'CRITICAL') " +
           "AND f.accessTimestamp BETWEEN :startDate AND :endDate " +
           "ORDER BY f.accessTimestamp DESC")
    Page<FieldLevelAudit> findPayrollDataAccess(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    /**
     * Trouve les exports de données sensibles
     */
    @Query("SELECT f FROM FieldLevelAudit f WHERE f.accessType = 'EXPORT' " +
           "AND f.sensitiveLevel IN ('HIGH', 'CRITICAL') " +
           "ORDER BY f.accessTimestamp DESC")
    Page<FieldLevelAudit> findSensitiveDataExports(Pageable pageable);


}