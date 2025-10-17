package com.bantuops.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO pour les demandes de rapport d'audit
 * Conforme aux exigences 7.6, 2.4, 2.5 pour la génération sécurisée de rapports
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditReportRequest {

    /**
     * Type de rapport d'audit demandé
     */
    @NotNull(message = "Le type de rapport est requis")
    private ReportType reportType;

    /**
     * Date de début de la période d'audit
     */
    @NotNull(message = "La date de début est requise")
    private LocalDateTime startDate;

    /**
     * Date de fin de la période d'audit
     */
    @NotNull(message = "La date de fin est requise")
    private LocalDateTime endDate;

    /**
     * ID utilisateur pour les rapports d'activité utilisateur (optionnel)
     */
    private String userId;

    /**
     * Type d'entité à filtrer (optionnel)
     */
    private String entityType;

    /**
     * Action à filtrer (optionnel)
     */
    private String action;

    /**
     * Niveau de sévérité minimum (optionnel)
     */
    private String minSeverity;

    /**
     * Inclure uniquement les données sensibles
     */
    @Builder.Default
    private boolean sensitiveDataOnly = false;

    /**
     * Inclure les données personnelles dans le rapport
     */
    @Builder.Default
    private boolean includePersonalData = false;

    /**
     * Chiffrer les données sensibles dans le rapport
     */
    @Builder.Default
    private boolean encryptSensitiveData = true;

    /**
     * Nombre maximum de résultats à retourner
     */
    @Positive(message = "Le nombre maximum de résultats doit être positif")
    @Builder.Default
    private int maxResults = 1000;

    /**
     * Format de sortie du rapport
     */
    @Builder.Default
    private OutputFormat outputFormat = OutputFormat.JSON;

    /**
     * Inclure les statistiques détaillées
     */
    @Builder.Default
    private boolean includeStatistics = true;

    /**
     * Inclure les graphiques et visualisations
     */
    @Builder.Default
    private boolean includeCharts = false;

    /**
     * Langue du rapport
     */
    @Builder.Default
    private String language = "fr";

    /**
     * Types de rapport d'audit disponibles
     */
    public enum ReportType {
        COMPREHENSIVE_AUDIT("Audit complet"),
        SECURITY_EVENTS("Événements de sécurité"),
        DATA_CHANGES("Changements de données"),
        COMPLIANCE_CHECK("Vérification de conformité"),
        USER_ACTIVITY("Activité utilisateur"),
        SYSTEM_PERFORMANCE("Performance système"),
        LOGIN_ATTEMPTS("Tentatives de connexion"),
        FAILED_OPERATIONS("Opérations échouées"),
        SENSITIVE_DATA_ACCESS("Accès aux données sensibles"),
        EXPORT_OPERATIONS("Opérations d'export"),
        ADMIN_ACTIONS("Actions administrateur"),
        BUSINESS_RULE_VIOLATIONS("Violations de règles métier");

        private final String description;

        ReportType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Formats de sortie disponibles
     */
    public enum OutputFormat {
        JSON("JSON"),
        PDF("PDF"),
        EXCEL("Excel"),
        CSV("CSV");

        private final String description;

        OutputFormat(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}