package com.bantuops.backend.dto;

import com.bantuops.backend.entity.AuditLog;
import com.bantuops.backend.entity.DataChangeHistory;
import com.bantuops.backend.entity.FieldLevelAudit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO pour les réponses de rapport d'audit
 * Conforme aux exigences 7.6, 2.4, 2.5 pour la génération sécurisée de rapports
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditReportResponse {

    /**
     * Identifiant unique du rapport
     */
    private String reportId;

    /**
     * Type de rapport généré
     */
    private AuditReportRequest.ReportType reportType;

    /**
     * Date de début de la période couverte
     */
    private LocalDateTime startDate;

    /**
     * Date de fin de la période couverte
     */
    private LocalDateTime endDate;

    /**
     * Date et heure de génération du rapport
     */
    private LocalDateTime generatedAt;

    /**
     * Utilisateur qui a généré le rapport
     */
    private String generatedBy;

    /**
     * Nombre total d'événements dans le rapport
     */
    private int totalEvents;

    /**
     * Indique si les données ont été chiffrées
     */
    @Builder.Default
    private boolean dataEncrypted = false;

    /**
     * Indique si les données personnelles ont été masquées
     */
    @Builder.Default
    private boolean personalDataMasked = false;

    /**
     * Score de conformité (0-100)
     */
    private Double complianceScore;

    /**
     * Logs d'audit inclus dans le rapport
     */
    private List<AuditLog> auditLogs;

    /**
     * Changements de données inclus dans le rapport
     */
    private List<DataChangeHistory> dataChanges;

    /**
     * Audits de champs inclus dans le rapport
     */
    private List<FieldLevelAudit> fieldAudits;

    /**
     * Événements de sécurité
     */
    private List<SecurityEvent> securityEvents;

    /**
     * Alertes de sécurité
     */
    private List<SecurityAlert> securityAlerts;

    /**
     * Analyse des menaces
     */
    private ThreatAnalysis threatAnalysis;

    /**
     * Violations de conformité
     */
    private List<ComplianceViolation> complianceViolations;

    /**
     * Métriques de performance
     */
    private List<Map<String, Object>> performanceMetrics;

    /**
     * Statistiques du rapport
     */
    private Statistics statistics;

    /**
     * Résumé exécutif du rapport
     */
    private ExecutiveSummary executiveSummary;

    /**
     * Recommandations basées sur l'analyse
     */
    private List<Recommendation> recommendations;

    /**
     * Métadonnées du rapport
     */
    private ReportMetadata metadata;

    /**
     * Classe pour les statistiques du rapport
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Statistics {
        private Map<String, Long> actionCounts;
        private Map<String, Long> entityCounts;
        private Map<String, Long> userCounts;
        private Map<String, Long> changeTypeCounts;
        private Map<String, Object> activityPeriod;
        private Long sensitiveDataChanges;
        private Double complianceScore;
        private Map<String, Long> severityCounts;
        private Map<String, Double> performanceMetrics;
        private Map<String, Long> ipAddressCounts;
        private Map<String, Long> userAgentCounts;
    }

    /**
     * Classe pour le résumé exécutif
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutiveSummary {
        private String overview;
        private List<String> keyFindings;
        private List<String> criticalIssues;
        private List<String> improvements;
        private String riskAssessment;
        private String complianceStatus;
    }

    /**
     * Classe pour les recommandations
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Recommendation {
        private String category;
        private String priority;
        private String description;
        private String action;
        private String timeline;
        private String impact;
    }

    /**
     * Classe pour les métadonnées du rapport
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportMetadata {
        private String version;
        private String generatorVersion;
        private Map<String, Object> parameters;
        private List<String> appliedFilters;
        private String dataSource;
        private String encryptionMethod;
        private String checksumMD5;
        private String checksumSHA256;
    }

    /**
     * Classe pour les événements de sécurité
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecurityEvent {
        private String eventId;
        private LocalDateTime timestamp;
        private String eventType;
        private String severity;
        private String description;
        private String userId;
        private String ipAddress;
        private String userAgent;
        private Map<String, Object> details;
        private boolean resolved;
        private String resolution;
    }

    /**
     * Classe pour les violations de conformité
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceViolation {
        private String violationId;
        private LocalDateTime timestamp;
        private String ruleType;
        private String severity;
        private String description;
        private String entityType;
        private Long entityId;
        private String userId;
        private Map<String, Object> context;
        private boolean resolved;
        private String correctionAction;
    }
}