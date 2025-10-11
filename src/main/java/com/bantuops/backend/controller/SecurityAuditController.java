package com.bantuops.backend.controller;

import com.bantuops.backend.dto.SecurityAuditReport;
import com.bantuops.backend.service.SecurityAuditReporter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Contrôleur REST pour les rapports d'audit de sécurité
 * Conforme aux exigences 4.1, 4.2, 4.3, 4.4 pour les APIs sécurisées
 */
@RestController
@RequestMapping("/api/security-audit")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Security Audit", description = "APIs pour les rapports d'audit de sécurité")
@SecurityRequirement(name = "bearerAuth")
public class SecurityAuditController {

    private final SecurityAuditReporter securityAuditReporter;

    /**
     * Génère un rapport d'audit de sécurité pour une période donnée
     */
    @GetMapping("/report")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    @Operation(
        summary = "Générer un rapport d'audit de sécurité",
        description = "Génère un rapport complet d'audit de sécurité pour la période spécifiée"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Rapport généré avec succès"),
        @ApiResponse(responseCode = "400", description = "Paramètres invalides"),
        @ApiResponse(responseCode = "403", description = "Accès non autorisé"),
        @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<SecurityAuditReport> generateSecurityAuditReport(
            @Parameter(description = "Date de début de la période d'audit", required = true)
            @RequestParam @NotNull 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime startDate,
            
            @Parameter(description = "Date de fin de la période d'audit", required = true)
            @RequestParam @NotNull 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime endDate) {

        log.info("Demande de génération de rapport d'audit de sécurité - Période: {} à {}", startDate, endDate);

        try {
            // Validation des paramètres
            if (startDate.isAfter(endDate)) {
                log.warn("Date de début postérieure à la date de fin: {} > {}", startDate, endDate);
                return ResponseEntity.badRequest().build();
            }

            if (startDate.isBefore(LocalDateTime.now().minusYears(2))) {
                log.warn("Période de rapport trop ancienne: {}", startDate);
                return ResponseEntity.badRequest().build();
            }

            // Génération du rapport
            SecurityAuditReport report = securityAuditReporter.generateSecurityAuditReport(startDate, endDate);
            
            log.info("Rapport d'audit de sécurité généré avec succès - ID: {}", report.getReportId());
            
            return ResponseEntity.ok(report);

        } catch (Exception e) {
            log.error("Erreur lors de la génération du rapport d'audit de sécurité: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Déclenche la génération d'alertes de sécurité en temps réel
     */
    @PostMapping("/alerts/generate")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECURITY_OFFICER')")
    @Operation(
        summary = "Générer des alertes de sécurité en temps réel",
        description = "Déclenche l'analyse et la génération d'alertes de sécurité pour les dernières 24 heures"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Génération d'alertes déclenchée"),
        @ApiResponse(responseCode = "403", description = "Accès non autorisé"),
        @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<Void> generateRealTimeSecurityAlerts() {
        log.info("Demande de génération d'alertes de sécurité en temps réel");

        try {
            // Déclenchement asynchrone de la génération d'alertes
            securityAuditReporter.generateRealTimeSecurityAlerts();
            
            log.info("Génération d'alertes de sécurité en temps réel déclenchée");
            
            return ResponseEntity.accepted().build();

        } catch (Exception e) {
            log.error("Erreur lors du déclenchement des alertes de sécurité: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Obtient un résumé rapide du statut de sécurité
     */
    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'SECURITY_OFFICER')")
    @Operation(
        summary = "Obtenir le statut de sécurité actuel",
        description = "Retourne un résumé rapide du statut de sécurité des dernières 24 heures"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Statut récupéré avec succès"),
        @ApiResponse(responseCode = "403", description = "Accès non autorisé"),
        @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<SecurityStatusSummary> getSecurityStatus() {
        log.info("Demande de statut de sécurité");

        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime yesterday = now.minusDays(1);

            // Génération d'un rapport rapide pour les dernières 24h
            SecurityAuditReport report = securityAuditReporter.generateSecurityAuditReport(yesterday, now);
            
            // Création du résumé
            SecurityStatusSummary summary = SecurityStatusSummary.builder()
                    .timestamp(now)
                    .overallRiskLevel(report.getOverallRiskLevel())
                    .totalSecurityEvents(report.getSecurityEvents() != null ? report.getSecurityEvents().size() : 0)
                    .totalSecurityAlerts(report.getSecurityAlerts() != null ? report.getSecurityAlerts().size() : 0)
                    .criticalAlerts(report.hasCriticalAlerts())
                    .threatLevel(report.getThreatAnalysis() != null ? 
                               report.getThreatAnalysis().getOverallThreatLevel() : null)
                    .build();

            log.info("Statut de sécurité récupéré - Niveau de risque: {}", summary.getOverallRiskLevel());
            
            return ResponseEntity.ok(summary);

        } catch (Exception e) {
            log.error("Erreur lors de la récupération du statut de sécurité: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * DTO pour le résumé du statut de sécurité
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SecurityStatusSummary {
        private LocalDateTime timestamp;
        private SecurityAuditReport.SecurityRiskLevel overallRiskLevel;
        private int totalSecurityEvents;
        private int totalSecurityAlerts;
        private boolean criticalAlerts;
        private com.bantuops.backend.dto.ThreatAnalysis.ThreatLevel threatLevel;
    }
}