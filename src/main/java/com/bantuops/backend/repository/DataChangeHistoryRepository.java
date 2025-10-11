package com.bantuops.backend.repository;

import com.bantuops.backend.entity.DataChangeHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository pour l'historique des modifications de données
 * Conforme aux exigences 7.4, 7.5, 7.6
 */
@Repository
public interface DataChangeHistoryRepository extends JpaRepository<DataChangeHistory, Long> {

    /**
     * Trouve l'historique complet d'une entité
     */
    Page<DataChangeHistory> findByEntityTypeAndEntityIdOrderByVersionDesc(
            String entityType, Long entityId, Pageable pageable);

    /**
     * Trouve une version spécifique d'une entité
     */
    Optional<DataChangeHistory> findByEntityTypeAndEntityIdAndVersion(
            String entityType, Long entityId, Long version);

    /**
     * Trouve la dernière version d'une entité
     */
    @Query("SELECT d FROM DataChangeHistory d WHERE d.entityType = :entityType AND d.entityId = :entityId ORDER BY d.version DESC LIMIT 1")
    Optional<DataChangeHistory> findLatestVersion(
            @Param("entityType") String entityType,
            @Param("entityId") Long entityId);

    /**
     * Trouve les modifications d'un champ spécifique
     */
    Page<DataChangeHistory> findByEntityTypeAndEntityIdAndFieldNameOrderByVersionDesc(
            String entityType, Long entityId, String fieldName, Pageable pageable);

    /**
     * Trouve les modifications par utilisateur
     */
    Page<DataChangeHistory> findByChangedByOrderByChangeTimestampDesc(String changedBy, Pageable pageable);

    /**
     * Trouve les modifications dans une période
     */
    @Query("SELECT d FROM DataChangeHistory d WHERE d.changeTimestamp BETWEEN :startDate AND :endDate ORDER BY d.changeTimestamp DESC")
    Page<DataChangeHistory> findByChangeTimestampBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    /**
     * Trouve les modifications de champs sensibles
     */
    Page<DataChangeHistory> findBySensitiveFieldTrueOrderByChangeTimestampDesc(Pageable pageable);

    /**
     * Trouve les modifications par type de changement
     */
    Page<DataChangeHistory> findByChangeTypeOrderByChangeTimestampDesc(
            DataChangeHistory.ChangeType changeType, Pageable pageable);

    /**
     * Compte les versions d'une entité
     */
    @Query("SELECT COUNT(d) FROM DataChangeHistory d WHERE d.entityType = :entityType AND d.entityId = :entityId")
    Long countVersionsByEntity(
            @Param("entityType") String entityType,
            @Param("entityId") Long entityId);

    /**
     * Trouve les entités modifiées récemment
     */
    @Query("SELECT DISTINCT d.entityType, d.entityId FROM DataChangeHistory d WHERE d.changeTimestamp >= :since")
    List<Object[]> findRecentlyModifiedEntities(@Param("since") LocalDateTime since);

    /**
     * Trouve les modifications suspectes (nombreuses modifications rapides)
     */
    @Query("SELECT d.entityType, d.entityId, COUNT(d) as changeCount FROM DataChangeHistory d " +
           "WHERE d.changeTimestamp >= :since GROUP BY d.entityType, d.entityId " +
           "HAVING COUNT(d) > :threshold ORDER BY changeCount DESC")
    List<Object[]> findSuspiciousModifications(
            @Param("since") LocalDateTime since,
            @Param("threshold") Long threshold);

    /**
     * Trouve l'historique des modifications d'un champ sensible
     */
    @Query("SELECT d FROM DataChangeHistory d WHERE d.entityType = :entityType AND d.fieldName = :fieldName " +
           "AND d.sensitiveField = true ORDER BY d.changeTimestamp DESC")
    Page<DataChangeHistory> findSensitiveFieldHistory(
            @Param("entityType") String entityType,
            @Param("fieldName") String fieldName,
            Pageable pageable);

    /**
     * Statistiques des modifications par type d'entité
     */
    @Query("SELECT d.entityType, COUNT(d) FROM DataChangeHistory d " +
           "WHERE d.changeTimestamp BETWEEN :startDate AND :endDate GROUP BY d.entityType")
    List<Object[]> getChangeStatsByEntityType(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Trouve les modifications dans une période (pour ComplianceReportGenerator)
     */
    List<DataChangeHistory> findByChangeTimestampBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Trouve les modifications dans une période avec pagination et tri
     */
    Page<DataChangeHistory> findByChangeTimestampBetweenOrderByChangeTimestampDesc(
            LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    /**
     * Trouve les modifications de données sensibles non justifiées
     */
    @Query("SELECT d FROM DataChangeHistory d WHERE d.changeTimestamp BETWEEN :startDate AND :endDate " +
           "AND d.sensitiveField = true AND (d.changeReason IS NULL OR d.changeReason = '') " +
           "ORDER BY d.changeTimestamp DESC")
    List<DataChangeHistory> findUnjustifiedSensitiveDataChanges(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}