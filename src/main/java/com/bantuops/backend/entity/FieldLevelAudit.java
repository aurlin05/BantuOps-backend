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
 * Entité pour l'audit au niveau des champs sensibles
 * Permet un suivi granulaire des modifications sur les données critiques
 * Conforme aux exigences 7.4, 7.5, 7.6
 */
@Entity
@Table(name = "field_level_audit", indexes = {
    @Index(name = "idx_field_audit_entity", columnList = "entityType,entityId,fieldName"),
    @Index(name = "idx_field_audit_user", columnList = "accessedBy"),
    @Index(name = "idx_field_audit_timestamp", columnList = "accessTimestamp"),
    @Index(name = "idx_field_audit_sensitive", columnList = "sensitiveLevel")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldLevelAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Type d'entité
     */
    @Column(nullable = false, length = 100)
    private String entityType;

    /**
     * ID de l'entité
     */
    @Column(nullable = false)
    private Long entityId;

    /**
     * Nom du champ accédé/modifié
     */
    @Column(nullable = false, length = 100)
    private String fieldName;

    /**
     * Type d'accès (READ, WRITE, DECRYPT, ENCRYPT)
     */
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AccessType accessType;

    /**
     * Utilisateur qui a accédé au champ
     */
    @Column(nullable = false, length = 100)
    private String accessedBy;

    /**
     * Rôle de l'utilisateur
     */
    @Column(length = 50)
    private String userRole;

    /**
     * Timestamp de l'accès
     */
    @CreatedDate
    @Column(nullable = false)
    private LocalDateTime accessTimestamp;

    /**
     * Ancienne valeur (hashée pour les données sensibles)
     */
    @Column(columnDefinition = "TEXT")
    private String oldValueHash;

    /**
     * Nouvelle valeur (hashée pour les données sensibles)
     */
    @Column(columnDefinition = "TEXT")
    private String newValueHash;

    /**
     * Niveau de sensibilité du champ
     */
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private SensitiveLevel sensitiveLevel;

    /**
     * Contexte de l'accès (API endpoint, méthode, etc.)
     */
    @Column(length = 200)
    private String accessContext;

    /**
     * Adresse IP
     */
    @Column(length = 45)
    private String ipAddress;

    /**
     * ID de session
     */
    @Column(length = 100)
    private String sessionId;

    /**
     * Justification de l'accès (pour les données très sensibles)
     */
    @Column(length = 500)
    private String accessJustification;

    /**
     * Indicateur si l'accès était autorisé
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean authorized = true;

    /**
     * Métadonnées supplémentaires
     */
    @Column(columnDefinition = "TEXT")
    private String metadata;

    public enum AccessType {
        READ,
        WRITE,
        DECRYPT,
        ENCRYPT,
        DELETE,
        EXPORT
    }

    public enum SensitiveLevel {
        LOW,        // Données publiques
        MEDIUM,     // Données internes
        HIGH,       // Données confidentielles
        CRITICAL    // Données ultra-sensibles (salaires, données personnelles)
    }
}