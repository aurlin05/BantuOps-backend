package com.bantuops.backend.service;

import com.bantuops.backend.dto.AuditReportRequest;
import com.bantuops.backend.dto.AuditReportResponse;
import com.bantuops.backend.entity.AuditLog;
import com.bantuops.backend.entity.DataChangeHistory;
import com.bantuops.backend.entity.FieldLevelAudit;
import com.bantuops.backend.repository.AuditLogRepository;
import com.bantuops.backend.repository.DataChangeHistoryRepository;
import com.bantuops.backend.repository.FieldLevelAuditRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service principal de génération de rapports d'audit sécurisés
 * Conforme aux exigences 7.6, 2.4, 2.5 pour la génération sécurisée de rapports
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditReportService {

    private final AuditLogRepository auditLogRepository;
    private final DataChangeHistoryRepository dataChangeHistoryRepository;
    private final FieldLevelAuditRepository fieldLevelAuditRepository;
    private final ComplianceReportGenerator complianceReportGenerator;
    private final SecurityAuditReporter securityAuditReporter;
    private final AuditDataExporter auditDataExporter;
    private final DataEncryptionService dataEncryptionService;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_REPORT_SIZE = 10000; // Limite de sécurité pour éviter les rapports trop volumineux

    /**
     * Génère un rapport d'audit complet avec sécurité renforcée
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    @Transactional(readOnly = true)
    public AuditReportResponse generateSecureAuditReport(AuditReportRequest request) {
        log.info("Génération de rapport d'audit sécurisé - Type: {}, Période: {} à {}",
                request.getReportType(), request.getStartDate(), request.getEndDate());

        try {
            // Validation des paramètres
            validateReportRequest(request);

            // Audit de la demande de rapport
            auditReportGeneration(request);

            AuditReportResponse response = new AuditReportResponse();
            response.setReportId(generateReportId());
            response.setReportType(request.getReportType());
            response.setStartDate(request.getStartDate());
            response.setEndDate(request.getEndDate());
            response.setGeneratedAt(LocalDateTime.now());
            response.setGeneratedBy(getCurrentUserId());

            // Génération selon le type de rapport
            switch (request.getReportType()) {
                case COMPREHENSIVE_AUDIT:
                    response = generateComprehensiveAuditReport(request, response);
                    break;
                case SECURITY_EVENTS:
                    response = generateSecurityEventsReport(request, response);
                    break;
                case DATA_CHANGES:
                    response = generateDataChangesReport(request, response);
                    break;
                case COMPLIANCE_CHECK:
                    response = generateComplianceReport(request, response);
                    break;
                case USER_ACTIVITY:
                    response = generateUserActivityReport(request, response);
                    break;
                case SYSTEM_PERFORMANCE:
                    response = generateSystemPerformanceReport(request, response);
                    break;
                default:
                    throw new IllegalArgumentException("Type de rapport non supporté: " + request.getReportType());
            }

            // Application des filtres de sécurité
            response = applySecurityFilters(response, request);

            // Chiffrement des données sensibles si demandé
            if (request.isEncryptSensitiveData()) {
                response = encryptSensitiveReportData(response);
            }

            log.info("Rapport d'audit généré avec succès - ID: {}, Événements: {}",
                    response.getReportId(), response.getTotalEvents());

            return response;

        } catch (Exception e) {
            log.error("Erreur lors de la génération du rapport d'audit: {}", e.getMessage(), e);
            throw new RuntimeException("Échec de la génération du rapport d'audit", e);
        }
    }

    /**
     * Génère un rapport d'audit compréhensif avec tous les types d'événements
     */
    private AuditReportResponse generateComprehensiveAuditReport(AuditReportRequest request,
            AuditReportResponse response) {
        // Récupération des logs d'audit avec pagination
        Pageable pageable = PageRequest.of(0, Math.min(request.getMaxResults(), MAX_REPORT_SIZE));
        Page<AuditLog> auditLogsPage = auditLogRepository.findByTimestampBetween(
                request.getStartDate(), request.getEndDate(), pageable);

        // Récupération des changements de données
        Page<DataChangeHistory> dataChangesPage = dataChangeHistoryRepository.findByChangeTimestampBetween(
                request.getStartDate(), request.getEndDate(), pageable);

        // Récupération des audits de champs
        Page<FieldLevelAudit> fieldAuditsPage = fieldLevelAuditRepository.findByAccessTimestampBetween(
                request.getStartDate(), request.getEndDate(), pageable);

        // Construction de la réponse
        response.setAuditLogs(auditLogsPage.getContent());
        response.setDataChanges(dataChangesPage.getContent());
        response.setFieldAudits(fieldAuditsPage.getContent());
        response.setTotalEvents(auditLogsPage.getContent().size() +
                dataChangesPage.getContent().size() +
                fieldAuditsPage.getContent().size());

        // Calcul des statistiques
        response.setStatistics(calculateComprehensiveStatistics(
                auditLogsPage.getContent(),
                dataChangesPage.getContent(),
                fieldAuditsPage.getContent()));

        return response;
    }

    /**
     * Génère un rapport focalisé sur les événements de sécurité
     */
    private AuditReportResponse generateSecurityEventsReport(AuditReportRequest request, AuditReportResponse response) {
        // Utilisation du SecurityAuditReporter pour les événements de sécurité
        var securityReport = securityAuditReporter.generateSecurityAuditReport(
                request.getStartDate(), request.getEndDate());

        // Conversion des AuditLog en SecurityEvent
        List<AuditReportResponse.SecurityEvent> securityEvents = securityReport.getSecurityEvents().stream()
                .map(this::convertToSecurityEvent)
                .collect(Collectors.toList());

        response.setSecurityEvents(securityEvents);
        response.setSecurityAlerts(securityReport.getSecurityAlerts());
        response.setThreatAnalysis(securityReport.getThreatAnalysis());
        response.setTotalEvents(securityReport.getSecurityEvents().size());

        // Statistiques de sécurité
        response.setStatistics(calculateSecurityStatistics(securityReport));

        return response;
    }

    /**
     * Génère un rapport focalisé sur les changements de données
     */
    private AuditReportResponse generateDataChangesReport(AuditReportRequest request, AuditReportResponse response) {
        Pageable pageable = PageRequest.of(0, Math.min(request.getMaxResults(), MAX_REPORT_SIZE));
        Page<DataChangeHistory> dataChangesPage = dataChangeHistoryRepository
                .findByChangeTimestampBetweenOrderByChangeTimestampDesc(
                        request.getStartDate(), request.getEndDate(), pageable);

        response.setDataChanges(dataChangesPage.getContent());
        response.setTotalEvents(dataChangesPage.getContent().size());

        // Analyse des patterns de changement
        response.setStatistics(calculateDataChangeStatistics(dataChangesPage.getContent()));

        return response;
    }

    /**
     * Génère un rapport de conformité
     */
    private AuditReportResponse generateComplianceReport(AuditReportRequest request, AuditReportResponse response) {
        var complianceReport = complianceReportGenerator.generateComplianceReport(
                request.getStartDate(), request.getEndDate(),
                ComplianceReportGenerator.ReportType.COMPLIANCE_CHECK);

        // Conversion des ComplianceViolation
        List<AuditReportResponse.ComplianceViolation> violations = complianceReport.getComplianceViolations().stream()
                .map(this::convertToComplianceViolation)
                .collect(Collectors.toList());

        response.setComplianceViolations(violations);
        response.setComplianceScore(complianceReport.getStatistics().getComplianceScore());
        response.setTotalEvents(complianceReport.getTotalEvents());

        // Statistiques de conformité
        response.setStatistics(convertComplianceStatistics(complianceReport.getStatistics()));

        return response;
    }

    /**
     * Génère un rapport d'activité utilisateur
     */
    private AuditReportResponse generateUserActivityReport(AuditReportRequest request, AuditReportResponse response) {
        if (request.getUserId() == null) {
            throw new IllegalArgumentException("UserID requis pour le rapport d'activité utilisateur");
        }

        Pageable pageable = PageRequest.of(0, Math.min(request.getMaxResults(), MAX_REPORT_SIZE));
        Page<AuditLog> userActivityPage = auditLogRepository.findByUserIdOrderByTimestampDesc(
                request.getUserId(), pageable);

        // Filtrer par période
        List<AuditLog> filteredActivity = userActivityPage.getContent().stream()
                .filter(log -> log.getTimestamp().isAfter(request.getStartDate()) &&
                        log.getTimestamp().isBefore(request.getEndDate()))
                .collect(Collectors.toList());

        response.setAuditLogs(filteredActivity);
        response.setTotalEvents(filteredActivity.size());

        // Statistiques d'activité utilisateur
        response.setStatistics(calculateUserActivityStatistics(filteredActivity, request.getUserId()));

        return response;
    }

    /**
     * Génère un rapport de performance système
     */
    private AuditReportResponse generateSystemPerformanceReport(AuditReportRequest request,
            AuditReportResponse response) {
        // Récupération des métriques de performance depuis les logs d'audit
        List<AuditLog> performanceLogs = auditLogRepository.findByTimestampBetween(
                request.getStartDate(), request.getEndDate()).stream()
                .filter(log -> log.getEntityType() != null && log.getEntityType().contains("PERFORMANCE"))
                .collect(Collectors.toList());

        response.setPerformanceMetrics(extractPerformanceMetrics(performanceLogs));
        response.setTotalEvents(performanceLogs.size());

        // Statistiques de performance
        response.setStatistics(calculatePerformanceStatistics(performanceLogs));

        return response;
    }

    /**
     * Applique les filtres de sécurité selon les permissions de l'utilisateur
     */
    private AuditReportResponse applySecurityFilters(AuditReportResponse response, AuditReportRequest request) {
        String currentUserRole = getCurrentUserRole();

        // Les utilisateurs non-admin ne peuvent pas voir certaines données sensibles
        if (!"ADMIN".equals(currentUserRole) && !"AUDITOR".equals(currentUserRole)) {
            response = filterSensitiveData(response);
        }

        // Masquage des données personnelles selon les permissions
        if (!request.isIncludePersonalData() || !hasPersonalDataAccess()) {
            response = maskPersonalData(response);
        }

        return response;
    }

    /**
     * Chiffre les données sensibles du rapport
     */
    private AuditReportResponse encryptSensitiveReportData(AuditReportResponse response) {
        try {
            // Chiffrement des logs d'audit contenant des données sensibles
            if (response.getAuditLogs() != null) {
                response.getAuditLogs().forEach(log -> {
                    if (log.getSensitiveData() != null && log.getSensitiveData() && log.getNewValues() != null) {
                        log.setNewValues(dataEncryptionService.encrypt(log.getNewValues()));
                    }
                    if (log.getSensitiveData() != null && log.getSensitiveData() && log.getOldValues() != null) {
                        log.setOldValues(dataEncryptionService.encrypt(log.getOldValues()));
                    }
                });
            }

            // Chiffrement des changements de données sensibles
            if (response.getDataChanges() != null) {
                response.getDataChanges().forEach(change -> {
                    if (change.getSensitiveField() != null && change.getSensitiveField()) {
                        if (change.getOldValue() != null) {
                            change.setOldValue(dataEncryptionService.encrypt(change.getOldValue()));
                        }
                        if (change.getNewValue() != null) {
                            change.setNewValue(dataEncryptionService.encrypt(change.getNewValue()));
                        }
                    }
                });
            }

            response.setDataEncrypted(true);
            log.info("Données sensibles du rapport chiffrées - ID: {}", response.getReportId());

        } catch (Exception e) {
            log.error("Erreur lors du chiffrement des données du rapport: {}", e.getMessage(), e);
            throw new RuntimeException("Échec du chiffrement des données du rapport", e);
        }

        return response;
    }

    // ==================== MÉTHODES UTILITAIRES ====================

    private void validateReportRequest(AuditReportRequest request) {
        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new IllegalArgumentException("Les dates de début et fin sont requises");
        }

        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new IllegalArgumentException("La date de début doit être antérieure à la date de fin");
        }

        if (request.getStartDate().isBefore(LocalDateTime.now().minusYears(2))) {
            throw new IllegalArgumentException("La période de rapport ne peut pas dépasser 2 ans");
        }

        if (request.getMaxResults() > MAX_REPORT_SIZE) {
            throw new IllegalArgumentException(
                    "Le nombre maximum de résultats ne peut pas dépasser " + MAX_REPORT_SIZE);
        }
    }

    private void auditReportGeneration(AuditReportRequest request) {
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("reportType", request.getReportType());
        auditData.put("startDate", request.getStartDate());
        auditData.put("endDate", request.getEndDate());
        auditData.put("maxResults", request.getMaxResults());
        auditData.put("encryptSensitiveData", request.isEncryptSensitiveData());

        // Log de l'audit via AuditService (sera implémenté dans la tâche 7.1)
        log.info("AUDIT_REPORT_GENERATION: User={}, Type={}, Period={} to {}",
                getCurrentUserId(), request.getReportType(),
                request.getStartDate().format(FORMATTER),
                request.getEndDate().format(FORMATTER));
    }

    private String generateReportId() {
        return "AUDIT_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private AuditReportResponse.Statistics calculateComprehensiveStatistics(
            List<AuditLog> auditLogs, List<DataChangeHistory> dataChanges, List<FieldLevelAudit> fieldAudits) {

        AuditReportResponse.Statistics stats = new AuditReportResponse.Statistics();

        // Statistiques des actions
        Map<String, Long> actionCounts = auditLogs.stream()
                .collect(Collectors.groupingBy(
                        log -> log.getAction().toString(),
                        Collectors.counting()));
        stats.setActionCounts(actionCounts);

        // Statistiques des entités
        Map<String, Long> entityCounts = auditLogs.stream()
                .filter(log -> log.getEntityType() != null)
                .collect(Collectors.groupingBy(AuditLog::getEntityType, Collectors.counting()));
        stats.setEntityCounts(entityCounts);

        // Statistiques des utilisateurs
        Map<String, Long> userCounts = auditLogs.stream()
                .filter(log -> log.getUserId() != null)
                .collect(Collectors.groupingBy(AuditLog::getUserId, Collectors.counting()));
        stats.setUserCounts(userCounts);

        // Statistiques des changements de données
        Map<String, Long> changeTypeCounts = dataChanges.stream()
                .collect(Collectors.groupingBy(
                        change -> change.getChangeType().toString(),
                        Collectors.counting()));
        stats.setChangeTypeCounts(changeTypeCounts);

        return stats;
    }

    private AuditReportResponse.Statistics calculateSecurityStatistics(Object securityReport) {
        // Implémentation des statistiques de sécurité
        AuditReportResponse.Statistics stats = new AuditReportResponse.Statistics();
        // TODO: Implémenter selon la structure du SecurityAuditReport
        return stats;
    }

    private AuditReportResponse.Statistics calculateDataChangeStatistics(List<DataChangeHistory> dataChanges) {
        AuditReportResponse.Statistics stats = new AuditReportResponse.Statistics();

        // Changements par type d'entité
        Map<String, Long> entityChanges = dataChanges.stream()
                .collect(Collectors.groupingBy(DataChangeHistory::getEntityType, Collectors.counting()));
        stats.setEntityCounts(entityChanges);

        // Changements par utilisateur
        Map<String, Long> userChanges = dataChanges.stream()
                .collect(Collectors.groupingBy(DataChangeHistory::getChangedBy, Collectors.counting()));
        stats.setUserCounts(userChanges);

        // Changements de champs sensibles
        long sensitiveChanges = dataChanges.stream()
                .mapToLong(change -> (change.getSensitiveField() != null && change.getSensitiveField()) ? 1 : 0)
                .sum();
        stats.setSensitiveDataChanges(sensitiveChanges);

        return stats;
    }

    private AuditReportResponse.Statistics convertComplianceStatistics(
            ComplianceReportGenerator.AuditStatistics complianceStats) {
        AuditReportResponse.Statistics stats = new AuditReportResponse.Statistics();
        stats.setComplianceScore(complianceStats.getComplianceScore());
        stats.setActionCounts(complianceStats.getActionCounts());
        stats.setEntityCounts(complianceStats.getChangesByEntity());
        return stats;
    }

    private AuditReportResponse.Statistics calculateUserActivityStatistics(List<AuditLog> userActivity, String userId) {
        AuditReportResponse.Statistics stats = new AuditReportResponse.Statistics();

        // Actions par type
        Map<String, Long> actionCounts = userActivity.stream()
                .collect(Collectors.groupingBy(
                        log -> log.getAction().toString(),
                        Collectors.counting()));
        stats.setActionCounts(actionCounts);

        // Ressources accédées
        Map<String, Long> resourceCounts = userActivity.stream()
                .filter(log -> log.getEntityType() != null)
                .collect(Collectors.groupingBy(AuditLog::getEntityType, Collectors.counting()));
        stats.setEntityCounts(resourceCounts);

        // Calcul de la période d'activité
        if (!userActivity.isEmpty()) {
            LocalDateTime firstActivity = userActivity.stream()
                    .map(AuditLog::getTimestamp)
                    .min(LocalDateTime::compareTo)
                    .orElse(null);
            LocalDateTime lastActivity = userActivity.stream()
                    .map(AuditLog::getTimestamp)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);

            stats.setActivityPeriod(Map.of(
                    "firstActivity", firstActivity != null ? firstActivity.format(FORMATTER) : "N/A",
                    "lastActivity", lastActivity != null ? lastActivity.format(FORMATTER) : "N/A"));
        }

        return stats;
    }

    private AuditReportResponse.Statistics calculatePerformanceStatistics(List<AuditLog> performanceLogs) {
        AuditReportResponse.Statistics stats = new AuditReportResponse.Statistics();

        // Analyse des métriques de performance
        // TODO: Implémenter selon la structure des logs de performance

        return stats;
    }

    private List<Map<String, Object>> extractPerformanceMetrics(List<AuditLog> performanceLogs) {
        return performanceLogs.stream()
                .map(log -> {
                    Map<String, Object> metric = new HashMap<>();
                    metric.put("timestamp", log.getTimestamp());
                    metric.put("entityType", log.getEntityType());
                    metric.put("action", log.getAction());
                    // TODO: Extraire les métriques spécifiques depuis les logs
                    return metric;
                })
                .collect(Collectors.toList());
    }

    private AuditReportResponse filterSensitiveData(AuditReportResponse response) {
        // Filtrage des données sensibles pour les utilisateurs non autorisés
        if (response.getAuditLogs() != null) {
            response.getAuditLogs().forEach(log -> {
                if (log.getSensitiveData() != null && log.getSensitiveData()) {
                    log.setOldValues("[DONNÉES SENSIBLES MASQUÉES]");
                    log.setNewValues("[DONNÉES SENSIBLES MASQUÉES]");
                }
            });
        }

        if (response.getDataChanges() != null) {
            response.getDataChanges().forEach(change -> {
                if (change.getSensitiveField() != null && change.getSensitiveField()) {
                    change.setOldValue("[MASQUÉ]");
                    change.setNewValue("[MASQUÉ]");
                }
            });
        }

        return response;
    }

    private AuditReportResponse maskPersonalData(AuditReportResponse response) {
        // Masquage des données personnelles
        // TODO: Implémenter le masquage selon les règles RGPD
        return response;
    }

    private String getCurrentUserId() {
        // TODO: Récupérer depuis le contexte de sécurité Spring
        return "SYSTEM"; // Placeholder
    }

    private String getCurrentUserRole() {
        // TODO: Récupérer depuis le contexte de sécurité Spring
        return "ADMIN"; // Placeholder
    }

    private boolean hasPersonalDataAccess() {
        // TODO: Vérifier les permissions d'accès aux données personnelles
        return true; // Placeholder
    }

    /**
     * Convertit un AuditLog en SecurityEvent
     */
    private AuditReportResponse.SecurityEvent convertToSecurityEvent(AuditLog auditLog) {
        return AuditReportResponse.SecurityEvent.builder()
                .eventId(auditLog.getId() != null ? auditLog.getId().toString() : null)
                .timestamp(auditLog.getTimestamp())
                .eventType(auditLog.getAction() != null ? auditLog.getAction().toString() : null)
                .severity(auditLog.getSeverity() != null ? auditLog.getSeverity() : "MEDIUM")
                .description(auditLog.getDescription() != null ? auditLog.getDescription()
                        : "Security event: " + auditLog.getAction())
                .userId(auditLog.getUserId())
                .ipAddress(auditLog.getIpAddress())
                .userAgent(auditLog.getUserAgent())
                .details(Map.of(
                        "entityType", auditLog.getEntityType() != null ? auditLog.getEntityType() : "",
                        "entityId", auditLog.getEntityId() != null ? auditLog.getEntityId().toString() : "",
                        "action", auditLog.getAction() != null ? auditLog.getAction().toString() : ""))
                .resolved(auditLog.getResolved() != null ? auditLog.getResolved() : false)
                .resolution(auditLog.getResolution())
                .build();
    }

    /**
     * Convertit un ComplianceViolation du générateur en ComplianceViolation de la
     * réponse
     */
    private AuditReportResponse.ComplianceViolation convertToComplianceViolation(
            ComplianceReportGenerator.ComplianceViolation violation) {
        return AuditReportResponse.ComplianceViolation.builder()
                .violationId(UUID.randomUUID().toString())
                .timestamp(violation.getTimestamp())
                .ruleType(violation.getViolationType())
                .severity(violation.getSeverity())
                .description(violation.getDescription())
                .entityType(null) // Not available in source
                .entityId(null) // Not available in source
                .userId(violation.getUserId())
                .context(Map.of(
                        "violationType", violation.getViolationType(),
                        "severity", violation.getSeverity()))
                .resolved(false)
                .correctionAction(null)
                .build();
    }
}