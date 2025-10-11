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
 * Entité pour l'audit des modifications de données
 * Conforme aux exigences 7.4, 7.5, 7.6 pour la traçabilité complète
 */
@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_entity", columnList = "entityType,entityId"),
    @Index(name = "idx_audit_user", columnList = "userId"),
    @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_action", columnList = "action")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Type d'entité modifiée (Employee, PayrollRecord, etc.)
     */
    @Column(nullable = false, length = 100)
    private String entityType;

    /**
     * ID de l'entité modifiée
     */
    @Column(nullable = false)
    private Long entityId;

    /**
     * Action effectuée (CREATE, UPDATE, DELETE, VIEW)
     */
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AuditAction action;

    /**
     * ID de l'utilisateur qui a effectué l'action
     */
    @Column(length = 100)
    private String userId;

    /**
     * Rôle de l'utilisateur au moment de l'action
     */
    @Column(length = 50)
    private String userRole;

    /**
     * Valeurs avant modification (JSON)
     */
    @Column(columnDefinition = "TEXT")
    private String oldValues;

    /**
     * Nouvelles valeurs après modification (JSON)
     */
    @Column(columnDefinition = "TEXT")
    private String newValues;

    /**
     * Champs modifiés (liste séparée par virgules)
     */
    @Column(columnDefinition = "TEXT")
    private String changedFields;

    /**
     * Adresse IP de l'utilisateur
     */
    @Column(length = 45) // IPv6 compatible
    private String ipAddress;

    /**
     * User-Agent du navigateur
     */
    @Column(length = 500)
    private String userAgent;

    /**
     * ID de session
     */
    @Column(length = 100)
    private String sessionId;

    /**
     * Timestamp de l'action
     */
    @CreatedDate
    @Column(nullable = false)
    private LocalDateTime timestamp;

    /**
     * Raison de la modification (optionnel)
     */
    @Column(length = 500)
    private String reason;

    /**
     * Métadonnées supplémentaires (JSON)
     */
    @Column(columnDefinition = "TEXT")
    private String metadata;

    /**
     * Indicateur si l'action concerne des données sensibles
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean sensitiveData = false;

    /**
     * Version de l'entité après modification
     */
    private Long entityVersion;

    /**
     * Description de l'événement d'audit
     */
    @Column(length = 1000)
    private String description;

    /**
     * Sévérité de l'événement (pour les alertes de sécurité)
     */
    @Column(length = 20)
    private String severity;

    /**
     * Indicateur si l'alerte est résolue
     */
    @Builder.Default
    private Boolean resolved = false;

    /**
     * Utilisateur qui a résolu l'alerte
     */
    @Column(length = 100)
    private String resolvedBy;

    /**
     * Description de la résolution
     */
    @Column(length = 1000)
    private String resolution;

    /**
     * Timestamp de résolution
     */
    private LocalDateTime resolvedAt;

    public enum AuditAction {
        CREATE,
        UPDATE,
        DELETE,
        VIEW,
        EXPORT,
        IMPORT,
        CALCULATE,
        GENERATE,
        FAILED_LOGIN_ATTEMPT,
        UNAUTHORIZED_ACCESS,
        SENSITIVE_DATA_MODIFICATION,
        ABNORMAL_ACTIVITY,
        BUSINESS_RULE_VIOLATION,
        SECURITY_BREACH,
        SYSTEM_COMPROMISE
    }
}