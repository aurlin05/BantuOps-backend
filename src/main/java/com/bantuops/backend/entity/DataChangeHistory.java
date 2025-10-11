package com.bantuops.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Entité pour l'historique des versions des données
 * Permet de conserver un historique complet des modifications
 * Conforme aux exigences 7.4, 7.5, 7.6
 */
@Entity
@Table(name = "data_change_history", indexes = {
    @Index(name = "idx_change_entity", columnList = "entityType,entityId"),
    @Index(name = "idx_change_version", columnList = "entityType,entityId,version"),
    @Index(name = "idx_change_timestamp", columnList = "changeTimestamp")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataChangeHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Type d'entité (Employee, PayrollRecord, etc.)
     */
    @Column(nullable = false, length = 100)
    private String entityType;

    /**
     * ID de l'entité
     */
    @Column(nullable = false)
    private Long entityId;

    /**
     * Version de l'entité
     */
    @Column(nullable = false)
    private Long version;

    /**
     * Snapshot complet de l'entité au moment de la modification (JSON)
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String entitySnapshot;

    /**
     * Champ spécifique modifié
     */
    @Column(length = 100)
    private String fieldName;

    /**
     * Ancienne valeur du champ
     */
    @Column(columnDefinition = "TEXT")
    private String oldValue;

    /**
     * Nouvelle valeur du champ
     */
    @Column(columnDefinition = "TEXT")
    private String newValue;

    /**
     * Type de modification
     */
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ChangeType changeType;

    /**
     * Utilisateur qui a effectué la modification
     */
    @Column(length = 100)
    private String changedBy;

    /**
     * Timestamp de la modification
     */
    @CreatedDate
    @Column(nullable = false)
    private LocalDateTime changeTimestamp;

    /**
     * Raison de la modification
     */
    @Column(length = 500)
    private String changeReason;

    /**
     * Indicateur si le champ est sensible
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean sensitiveField = false;

    /**
     * Hash de vérification d'intégrité
     */
    @Column(length = 64)
    private String integrityHash;

    public enum ChangeType {
        FIELD_UPDATE,
        ENTITY_CREATE,
        ENTITY_DELETE,
        BULK_UPDATE,
        SYSTEM_UPDATE
    }
}