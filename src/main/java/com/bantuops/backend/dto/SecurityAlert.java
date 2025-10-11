package com.bantuops.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO représentant une alerte de sécurité
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityAlert {

    private Long id;
    private AlertType alertType;
    private String userId;
    private String description;
    private Map<String, Object> metadata;
    private LocalDateTime timestamp;
    private Severity severity;
    private String ipAddress;
    private String userAgent;
    private boolean resolved;
    private String resolvedBy;
    private String resolution;
    private LocalDateTime resolvedAt;

    /**
     * Types d'alertes de sécurité
     */
    public enum AlertType {
        FAILED_LOGIN_ATTEMPT("Tentative de connexion échouée"),
        UNAUTHORIZED_ACCESS("Accès non autorisé"),
        SENSITIVE_DATA_MODIFICATION("Modification de données sensibles"),
        ABNORMAL_ACTIVITY("Activité anormale"),
        BUSINESS_RULE_VIOLATION("Violation de règle métier"),
        SECURITY_BREACH("Violation de sécurité"),
        SYSTEM_COMPROMISE("Compromission système"),
        SUSPICIOUS_TRANSACTION("Transaction suspecte"),
        DATA_EXPORT_VIOLATION("Violation d'export de données"),
        PRIVILEGE_ESCALATION("Escalade de privilèges");

        private final String description;

        AlertType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Niveaux de sévérité des alertes
     */
    public enum Severity {
        LOW("Faible", 1),
        MEDIUM("Moyen", 2),
        HIGH("Élevé", 3),
        CRITICAL("Critique", 4);

        private final String label;
        private final int level;

        Severity(String label, int level) {
            this.label = label;
            this.level = level;
        }

        public String getLabel() {
            return label;
        }

        public int getLevel() {
            return level;
        }

        public boolean isHigherThan(Severity other) {
            return this.level > other.level;
        }
    }

    /**
     * Statut de traitement de l'alerte
     */
    public enum Status {
        NEW("Nouvelle"),
        IN_PROGRESS("En cours"),
        RESOLVED("Résolue"),
        FALSE_POSITIVE("Faux positif"),
        ESCALATED("Escaladée");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /**
     * Vérifie si l'alerte nécessite une action immédiate
     */
    public boolean requiresImmediateAction() {
        return severity == Severity.CRITICAL || severity == Severity.HIGH;
    }

    /**
     * Vérifie si l'alerte est récente (moins de 24h)
     */
    public boolean isRecent() {
        return timestamp != null && timestamp.isAfter(LocalDateTime.now().minusDays(1));
    }

    /**
     * Retourne une description formatée de l'alerte
     */
    public String getFormattedDescription() {
        return String.format("[%s] %s - %s (Utilisateur: %s)", 
            severity.getLabel(), 
            alertType.getDescription(), 
            description, 
            userId != null ? userId : "Inconnu");
    }
}