package com.bantuops.backend.dto;

import com.bantuops.backend.entity.AuditLog;
import com.bantuops.backend.entity.FieldLevelAudit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO représentant un rapport d'audit de sécurité complet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityAuditReport {

    private String reportId;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private LocalDateTime generatedAt;
    private String generatedBy;
    
    // Événements de sécurité
    private List<AuditLog> securityEvents;
    
    // Alertes de sécurité
    private List<SecurityAlert> securityAlerts;
    
    // Analyse des menaces
    private ThreatAnalysis threatAnalysis;
    
    // Métriques de sécurité
    private Map<String, Object> securityMetrics;
    
    // Recommandations de sécurité
    private List<String> securityRecommendations;
    
    // Audits de champs sensibles
    private List<FieldLevelAudit> sensitiveFieldAccess;
    
    // Statistiques du rapport
    private ReportStatistics statistics;
    
    // Indicateurs de conformité
    private ComplianceIndicators complianceIndicators;

    /**
     * Statistiques du rapport d'audit de sécurité
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportStatistics {
        private int totalSecurityEvents;
        private int totalSecurityAlerts;
        private int criticalAlerts;
        private int highSeverityAlerts;
        private int mediumSeverityAlerts;
        private int lowSeverityAlerts;
        private int resolvedAlerts;
        private int pendingAlerts;
        private double averageResponseTime;
        private Map<String, Integer> alertsByType;
        private Map<String, Integer> eventsByUser;
        private Map<String, Integer> eventsByHour;
    }

    /**
     * Indicateurs de conformité de sécurité
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceIndicators {
        private double overallComplianceScore;
        private boolean dataProtectionCompliant;
        private boolean accessControlCompliant;
        private boolean auditTrailCompliant;
        private boolean encryptionCompliant;
        private List<String> complianceViolations;
        private List<String> complianceRecommendations;
    }

    /**
     * Retourne le niveau de risque global du rapport
     */
    public SecurityRiskLevel getOverallRiskLevel() {
        if (threatAnalysis != null) {
            switch (threatAnalysis.getOverallThreatLevel()) {
                case CRITICAL:
                    return SecurityRiskLevel.CRITICAL;
                case HIGH:
                    return SecurityRiskLevel.HIGH;
                case MEDIUM:
                    return SecurityRiskLevel.MEDIUM;
                case LOW:
                default:
                    return SecurityRiskLevel.LOW;
            }
        }
        return SecurityRiskLevel.LOW;
    }

    /**
     * Vérifie si le rapport contient des alertes critiques
     */
    public boolean hasCriticalAlerts() {
        return securityAlerts != null && 
               securityAlerts.stream().anyMatch(alert -> 
                   alert.getSeverity() == SecurityAlert.Severity.CRITICAL);
    }

    /**
     * Retourne le nombre total d'événements dans le rapport
     */
    public int getTotalEvents() {
        int total = 0;
        if (securityEvents != null) total += securityEvents.size();
        if (securityAlerts != null) total += securityAlerts.size();
        if (sensitiveFieldAccess != null) total += sensitiveFieldAccess.size();
        return total;
    }

    /**
     * Niveaux de risque de sécurité
     */
    public enum SecurityRiskLevel {
        LOW("Faible", "#28a745"),
        MEDIUM("Moyen", "#ffc107"),
        HIGH("Élevé", "#fd7e14"),
        CRITICAL("Critique", "#dc3545");

        private final String label;
        private final String color;

        SecurityRiskLevel(String label, String color) {
            this.label = label;
            this.color = color;
        }

        public String getLabel() {
            return label;
        }

        public String getColor() {
            return color;
        }
    }
}