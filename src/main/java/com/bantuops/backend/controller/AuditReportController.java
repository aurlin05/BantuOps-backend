package com.bantuops.backend.controller;

import com.bantuops.backend.dto.AuditReportRequest;
import com.bantuops.backend.dto.AuditReportResponse;
import com.bantuops.backend.service.AuditDataExporter;
import com.bantuops.backend.service.AuditReportService;
import com.bantuops.backend.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Contrôleur REST pour la génération de rapports d'audit sécurisés
 * Conforme aux exigences 7.6, 2.4, 2.5 pour les rapports d'audit
 */
@RestController
@RequestMapping("/api/audit/reports")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Audit Reports", description = "API pour la génération de rapports d'audit sécurisés")
@SecurityRequirement(name = "bearerAuth")
public class AuditReportController {

    private final AuditReportService auditReportService;
    private final AuditDataExporter auditDataExporter;
    private final AuditService auditService;

    private static final DateTimeFormatter FILENAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * Génère un rapport d'audit sécurisé
     */
    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    @Operation(summary = "Générer un rapport d'audit sécurisé", 
               description = "Génère un rapport d'audit complet avec chiffrement des données sensibles")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Rapport généré avec succès"),
        @ApiResponse(responseCode = "400", description = "Paramètres de demande invalides"),
        @ApiResponse(responseCode = "403", description = "Accès non autorisé"),
        @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<AuditReportResponse> generateAuditReport(
            @Valid @RequestBody AuditReportRequest request) {
        
        log.info("Demande de génération de rapport d'audit - Type: {}, Période: {} à {}", 
                request.getReportType(), request.getStartDate(), request.getEndDate());

        try {
            // Audit de la demande
            auditService.logAuditEvent(
                "AUDIT_REPORT", 0L, 
                com.bantuops.backend.entity.AuditLog.AuditAction.GENERATE,
                "Demande de génération de rapport d'audit",
                null, request, 
                "Génération de rapport d'audit demandée",
                true
            );

            AuditReportResponse response = auditReportService.generateSecureAuditReport(request);
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.warn("Paramètres invalides pour la génération de rapport: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Erreur lors de la génération du rapport d'audit: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Exporte un rapport d'audit au format demandé
     */
    @PostMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    @Operation(summary = "Exporter un rapport d'audit", 
               description = "Exporte un rapport d'audit dans le format spécifié avec chiffrement")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Export réussi"),
        @ApiResponse(responseCode = "400", description = "Paramètres d'export invalides"),
        @ApiResponse(responseCode = "403", description = "Accès non autorisé")
    })
    public ResponseEntity<byte[]> exportAuditReport(
            @Valid @RequestBody AuditReportRequest request) {
        
        log.info("Demande d'export de rapport d'audit - Type: {}, Format: {}", 
                request.getReportType(), request.getOutputFormat());

        try {
            // Audit de la demande d'export
            auditService.logAuditEvent(
                "AUDIT_EXPORT", 0L, 
                com.bantuops.backend.entity.AuditLog.AuditAction.EXPORT,
                "Demande d'export de rapport d'audit",
                null, request, 
                "Export de rapport d'audit demandé",
                true
            );

            AuditDataExporter.ExportResult exportResult = auditDataExporter.exportAuditDataWithEncryption(request);
            
            // Préparation de la réponse HTTP
            HttpHeaders headers = new HttpHeaders();
            String filename = generateExportFilename(request);
            headers.setContentDispositionFormData("attachment", filename);
            
            // Type de contenu selon le format
            MediaType contentType = switch (request.getOutputFormat()) {
                case JSON -> MediaType.APPLICATION_JSON;
                case CSV -> MediaType.parseMediaType("text/csv");
                case EXCEL -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                case PDF -> MediaType.APPLICATION_PDF;
            };
            headers.setContentType(contentType);
            
            // Ajout des métadonnées dans les en-têtes
            headers.add("X-Export-ID", exportResult.getExportId());
            headers.add("X-Export-Encrypted", String.valueOf(exportResult.isEncrypted()));
            headers.add("X-Export-Checksum-MD5", exportResult.getChecksumMD5());
            headers.add("X-Export-Checksum-SHA256", exportResult.getChecksumSHA256());
            
            byte[] exportData = java.util.Base64.getDecoder().decode(exportResult.getData());
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(exportData);
                    
        } catch (IllegalArgumentException e) {
            log.warn("Paramètres invalides pour l'export: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Erreur lors de l'export du rapport: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Génère un rapport d'audit rapide pour une période donnée
     */
    @GetMapping("/quick")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'HR')")
    @Operation(summary = "Générer un rapport d'audit rapide", 
               description = "Génère un rapport d'audit simplifié pour une période donnée")
    public ResponseEntity<AuditReportResponse> generateQuickAuditReport(
            @Parameter(description = "Date de début (format: yyyy-MM-dd'T'HH:mm:ss)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            
            @Parameter(description = "Date de fin (format: yyyy-MM-dd'T'HH:mm:ss)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            
            @Parameter(description = "Type de rapport")
            @RequestParam(defaultValue = "COMPREHENSIVE_AUDIT") AuditReportRequest.ReportType reportType,
            
            @Parameter(description = "Nombre maximum de résultats")
            @RequestParam(defaultValue = "1000") int maxResults) {
        
        log.info("Génération de rapport d'audit rapide - Période: {} à {}", startDate, endDate);

        try {
            AuditReportRequest request = AuditReportRequest.builder()
                    .reportType(reportType)
                    .startDate(startDate)
                    .endDate(endDate)
                    .maxResults(maxResults)
                    .encryptSensitiveData(false) // Pas de chiffrement pour les rapports rapides
                    .includeStatistics(true)
                    .build();

            AuditReportResponse response = auditReportService.generateSecureAuditReport(request);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Erreur lors de la génération du rapport rapide: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Récupère les statistiques d'audit pour le tableau de bord
     */
    @GetMapping("/dashboard-stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'HR')")
    @Operation(summary = "Statistiques d'audit pour le tableau de bord", 
               description = "Récupère les statistiques d'audit récentes pour l'affichage du tableau de bord")
    public ResponseEntity<Map<String, Object>> getDashboardAuditStats(
            @Parameter(description = "Nombre d'heures à inclure dans les statistiques")
            @RequestParam(defaultValue = "24") int hours) {
        
        log.debug("Récupération des statistiques d'audit pour le tableau de bord - {} heures", hours);

        try {
            LocalDateTime endDate = LocalDateTime.now();
            LocalDateTime startDate = endDate.minusHours(hours);

            AuditReportRequest request = AuditReportRequest.builder()
                    .reportType(AuditReportRequest.ReportType.COMPREHENSIVE_AUDIT)
                    .startDate(startDate)
                    .endDate(endDate)
                    .maxResults(5000)
                    .includeStatistics(true)
                    .encryptSensitiveData(false)
                    .build();

            AuditReportResponse response = auditReportService.generateSecureAuditReport(request);
            
            // Extraction des statistiques pour le tableau de bord
            Map<String, Object> dashboardStats = Map.of(
                    "totalEvents", response.getTotalEvents(),
                    "period", Map.of("hours", hours, "startDate", startDate, "endDate", endDate),
                    "statistics", response.getStatistics() != null ? response.getStatistics() : Map.of(),
                    "complianceScore", response.getComplianceScore() != null ? response.getComplianceScore() : 0.0,
                    "lastUpdated", LocalDateTime.now()
            );
            
            return ResponseEntity.ok(dashboardStats);
            
        } catch (Exception e) {
            log.error("Erreur lors de la récupération des statistiques du tableau de bord: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Valide les paramètres d'un rapport d'audit
     */
    @PostMapping("/validate")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    @Operation(summary = "Valider les paramètres d'un rapport", 
               description = "Valide les paramètres d'une demande de rapport d'audit")
    public ResponseEntity<Map<String, Object>> validateReportRequest(
            @Valid @RequestBody AuditReportRequest request) {
        
        log.debug("Validation des paramètres de rapport d'audit");

        try {
            // Validation basique
            boolean isValid = true;
            StringBuilder validationMessage = new StringBuilder();

            if (request.getStartDate().isAfter(request.getEndDate())) {
                isValid = false;
                validationMessage.append("La date de début doit être antérieure à la date de fin. ");
            }

            if (request.getStartDate().isBefore(LocalDateTime.now().minusYears(2))) {
                isValid = false;
                validationMessage.append("La période ne peut pas dépasser 2 ans. ");
            }

            if (request.getMaxResults() > 50000) {
                isValid = false;
                validationMessage.append("Le nombre maximum de résultats ne peut pas dépasser 50000. ");
            }

            // Estimation de la taille du rapport
            long estimatedSize = estimateReportSize(request);
            
            Map<String, Object> validationResult = Map.of(
                    "valid", isValid,
                    "message", isValid ? "Paramètres valides" : validationMessage.toString().trim(),
                    "estimatedSize", estimatedSize,
                    "estimatedDuration", estimateProcessingTime(request),
                    "recommendations", generateRecommendations(request)
            );
            
            return ResponseEntity.ok(validationResult);
            
        } catch (Exception e) {
            log.error("Erreur lors de la validation: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ==================== MÉTHODES UTILITAIRES ====================

    private String generateExportFilename(AuditReportRequest request) {
        String timestamp = LocalDateTime.now().format(FILENAME_FORMATTER);
        String reportType = request.getReportType().toString().toLowerCase();
        String extension = switch (request.getOutputFormat()) {
            case JSON -> "json";
            case CSV -> "csv";
            case EXCEL -> "xlsx";
            case PDF -> "pdf";
        };
        
        return String.format("audit_report_%s_%s.%s", reportType, timestamp, extension);
    }

    private long estimateReportSize(AuditReportRequest request) {
        // Estimation basique basée sur la période et le type de rapport
        long days = java.time.Duration.between(request.getStartDate(), request.getEndDate()).toDays();
        long baseSize = days * 1000; // 1KB par jour en moyenne
        
        return switch (request.getReportType()) {
            case COMPREHENSIVE_AUDIT -> baseSize * 5;
            case SECURITY_EVENTS -> baseSize * 2;
            case DATA_CHANGES -> baseSize * 3;
            case USER_ACTIVITY -> baseSize * 2;
            default -> baseSize;
        };
    }

    private String estimateProcessingTime(AuditReportRequest request) {
        long days = java.time.Duration.between(request.getStartDate(), request.getEndDate()).toDays();
        
        if (days <= 7) return "< 30 secondes";
        if (days <= 30) return "30-60 secondes";
        if (days <= 90) return "1-2 minutes";
        return "2-5 minutes";
    }

    private java.util.List<String> generateRecommendations(AuditReportRequest request) {
        java.util.List<String> recommendations = new java.util.ArrayList<>();
        
        long days = java.time.Duration.between(request.getStartDate(), request.getEndDate()).toDays();
        
        if (days > 90) {
            recommendations.add("Considérez diviser la période en plusieurs rapports plus petits pour de meilleures performances");
        }
        
        if (request.getMaxResults() > 10000) {
            recommendations.add("Un grand nombre de résultats peut ralentir la génération du rapport");
        }
        
        if (request.isEncryptSensitiveData()) {
            recommendations.add("Le chiffrement des données sensibles augmentera le temps de traitement");
        }
        
        if (request.getOutputFormat() == AuditReportRequest.OutputFormat.PDF) {
            recommendations.add("Le format PDF peut prendre plus de temps à générer pour de gros volumes");
        }
        
        return recommendations;
    }
}