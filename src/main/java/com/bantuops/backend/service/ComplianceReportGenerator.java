package com.bantuops.backend.service;

import com.bantuops.backend.entity.AuditLog;
import com.bantuops.backend.entity.DataChangeHistory;
import com.bantuops.backend.entity.FieldLevelAudit;
import com.bantuops.backend.repository.AuditLogRepository;
import com.bantuops.backend.repository.DataChangeHistoryRepository;
import com.bantuops.backend.repository.FieldLevelAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Générateur de rapports de conformité et d'audit
 * Conforme aux exigences 7.4, 7.5, 7.6 pour les rapports d'audit complets
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComplianceReportGenerator {

    private final AuditLogRepository auditLogRepository;
    private final DataChangeHistoryRepository dataChangeHistoryRepository;
    private final FieldLevelAuditRepository fieldLevelAuditRepository;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Génère un rapport d'audit complet pour une période donnée
     */
    public ComplianceReport generateComplianceReport(LocalDateTime startDate, LocalDateTime endDate,
            ReportType reportType) {
        log.info("Génération du rapport de conformité du {} au {} - Type: {}",
                startDate.format(FORMATTER), endDate.format(FORMATTER), reportType);

        ComplianceReport report = new ComplianceReport();
        report.setReportType(reportType);
        report.setStartDate(startDate);
        report.setEndDate(endDate);
        report.setGeneratedAt(LocalDateTime.now());

        try {
            // Collecte des données d'audit selon le type de rapport
            switch (reportType) {
                case FULL_AUDIT:
                    report = generateFullAuditReport(startDate, endDate);
                    break;
                case DATA_CHANGES:
                    report = generateDataChangesReport(startDate, endDate);
                    break;
                case SECURITY_EVENTS:
                    report = generateSecurityEventsReport(startDate, endDate);
                    break;
                case COMPLIANCE_CHECK:
                    report = generateComplianceCheckReport(startDate, endDate);
                    break;
                default:
                    throw new IllegalArgumentException("Type de rapport non supporté: " + reportType);
            }

            log.info("Rapport de conformité généré avec succès - {} événements trouvés",
                    report.getTotalEvents());
            return report;

        } catch (Exception e) {
            log.error("Erreur lors de la génération du rapport de conformité: {}", e.getMessage(), e);
            throw new RuntimeException("Échec de la génération du rapport", e);
        }
    }

    /**
     * Génère un rapport d'audit complet avec tous les événements
     */
    private ComplianceReport generateFullAuditReport(LocalDateTime startDate, LocalDateTime endDate) {
        ComplianceReport report = new ComplianceReport();
        report.setReportType(ReportType.FULL_AUDIT);
        report.setStartDate(startDate);
        report.setEndDate(endDate);
        report.setGeneratedAt(LocalDateTime.now());

        // Récupération des logs d'audit
        List<AuditLog> auditLogs = auditLogRepository.findByTimestampBetween(startDate, endDate);

        // Récupération des changements de données
        List<DataChangeHistory> dataChanges = dataChangeHistoryRepository.findByChangeTimestampBetween(startDate,
                endDate);

        // Récupération des audits au niveau des champs
        List<FieldLevelAudit> fieldAudits = fieldLevelAuditRepository
                .findByAccessTimestampBetween(startDate, endDate, org.springframework.data.domain.Pageable.unpaged())
                .getContent();

        // Construction du rapport
        report.setAuditEvents(auditLogs);
        report.setDataChanges(dataChanges);
        report.setFieldAudits(fieldAudits);
        report.setTotalEvents(auditLogs.size() + dataChanges.size() + fieldAudits.size());

        // Calcul des statistiques
        report.setStatistics(calculateAuditStatistics(auditLogs, dataChanges, fieldAudits));

        return report;
    }

    /**
     * Génère un rapport focalisé sur les changements de données
     */
    private ComplianceReport generateDataChangesReport(LocalDateTime startDate, LocalDateTime endDate) {
        ComplianceReport report = new ComplianceReport();
        report.setReportType(ReportType.DATA_CHANGES);
        report.setStartDate(startDate);
        report.setEndDate(endDate);
        report.setGeneratedAt(LocalDateTime.now());

        // Récupération des changements de données avec pagination
        Pageable pageable = PageRequest.of(0, 1000);
        Page<DataChangeHistory> dataChangesPage = dataChangeHistoryRepository
                .findByChangeTimestampBetweenOrderByChangeTimestampDesc(startDate, endDate, pageable);

        List<DataChangeHistory> dataChanges = dataChangesPage.getContent();
        report.setDataChanges(dataChanges);
        report.setTotalEvents(dataChanges.size());

        // Analyse des patterns de changement
        Map<String, Long> changesByEntity = dataChanges.stream()
                .collect(Collectors.groupingBy(DataChangeHistory::getEntityType, Collectors.counting()));

        Map<String, Long> changesByUser = dataChanges.stream()
                .collect(Collectors.groupingBy(DataChangeHistory::getChangedBy, Collectors.counting()));

        AuditStatistics stats = new AuditStatistics();
        stats.setChangesByEntity(changesByEntity);
        stats.setChangesByUser(changesByUser);
        report.setStatistics(stats);

        return report;
    }

    /**
     * Génère un rapport focalisé sur les événements de sécurité
     */
    private ComplianceReport generateSecurityEventsReport(LocalDateTime startDate, LocalDateTime endDate) {
        ComplianceReport report = new ComplianceReport();
        report.setReportType(ReportType.SECURITY_EVENTS);
        report.setStartDate(startDate);
        report.setEndDate(endDate);
        report.setGeneratedAt(LocalDateTime.now());

        // Récupération des événements de sécurité
        List<AuditLog> securityEvents = auditLogRepository
                .findSecurityEventsByTimestampBetween(startDate, endDate);

        report.setAuditEvents(securityEvents);
        report.setTotalEvents(securityEvents.size());

        // Analyse des événements de sécurité
        Map<String, Long> eventsByType = securityEvents.stream()
                .collect(Collectors.groupingBy(
                        audit -> audit.getAction().toString(),
                        Collectors.counting()));

        AuditStatistics stats = new AuditStatistics();
        stats.setSecurityEventsByType(eventsByType);
        report.setStatistics(stats);

        return report;
    }

    /**
     * Génère un rapport de vérification de conformité
     */
    private ComplianceReport generateComplianceCheckReport(LocalDateTime startDate, LocalDateTime endDate) {
        ComplianceReport report = new ComplianceReport();
        report.setReportType(ReportType.COMPLIANCE_CHECK);
        report.setStartDate(startDate);
        report.setEndDate(endDate);
        report.setGeneratedAt(LocalDateTime.now());

        // Vérifications de conformité
        List<ComplianceViolation> violations = performComplianceChecks(startDate, endDate);
        report.setComplianceViolations(violations);
        report.setTotalEvents(violations.size());

        // Calcul du score de conformité
        double complianceScore = calculateComplianceScore(violations);
        AuditStatistics stats = new AuditStatistics();
        stats.setComplianceScore(complianceScore);
        report.setStatistics(stats);

        return report;
    }

    /**
     * Calcule les statistiques d'audit
     */
    private AuditStatistics calculateAuditStatistics(List<AuditLog> auditLogs,
            List<DataChangeHistory> dataChanges,
            List<FieldLevelAudit> fieldAudits) {
        AuditStatistics stats = new AuditStatistics();

        // Statistiques des logs d'audit
        Map<String, Long> actionCounts = auditLogs.stream()
                .collect(Collectors.groupingBy(
                        audit -> audit.getAction().toString(),
                        Collectors.counting()));
        stats.setActionCounts(actionCounts);

        // Statistiques des changements de données
        Map<String, Long> changesByEntity = dataChanges.stream()
                .collect(Collectors.groupingBy(DataChangeHistory::getEntityType, Collectors.counting()));
        stats.setChangesByEntity(changesByEntity);

        // Statistiques des audits de champs
        Map<String, Long> fieldChangesByEntity = fieldAudits.stream()
                .collect(Collectors.groupingBy(FieldLevelAudit::getEntityType, Collectors.counting()));
        stats.setFieldChangesByEntity(fieldChangesByEntity);

        return stats;
    }

    /**
     * Effectue les vérifications de conformité
     */
    private List<ComplianceViolation> performComplianceChecks(LocalDateTime startDate, LocalDateTime endDate) {
        List<ComplianceViolation> violations = new ArrayList<>();

        // Vérification 1: Accès non autorisés
        List<AuditLog> unauthorizedAccess = auditLogRepository
                .findUnauthorizedAccessAttempts(startDate, endDate);

        for (AuditLog log : unauthorizedAccess) {
            violations.add(new ComplianceViolation(
                    "UNAUTHORIZED_ACCESS",
                    "Tentative d'accès non autorisé détectée",
                    log.getTimestamp(),
                    log.getUserId(),
                    "HIGH"));
        }

        // Vérification 2: Modifications de données sensibles sans justification
        List<DataChangeHistory> unjustifiedChanges = dataChangeHistoryRepository
                .findUnjustifiedSensitiveDataChanges(startDate, endDate);

        for (DataChangeHistory change : unjustifiedChanges) {
            violations.add(new ComplianceViolation(
                    "UNJUSTIFIED_SENSITIVE_CHANGE",
                    "Modification de données sensibles sans justification",
                    change.getChangeTimestamp(),
                    change.getChangedBy(),
                    "MEDIUM"));
        }

        // Vérification 3: Tentatives de suppression de logs d'audit
        List<AuditLog> auditDeletionAttempts = auditLogRepository
                .findAuditDeletionAttempts(startDate, endDate);

        for (AuditLog log : auditDeletionAttempts) {
            violations.add(new ComplianceViolation(
                    "AUDIT_TAMPERING",
                    "Tentative de suppression de logs d'audit",
                    log.getTimestamp(),
                    log.getUserId(),
                    "CRITICAL"));
        }

        return violations;
    }

    /**
     * Calcule le score de conformité basé sur les violations
     */
    private double calculateComplianceScore(List<ComplianceViolation> violations) {
        if (violations.isEmpty()) {
            return 100.0;
        }

        int totalPenalty = 0;
        for (ComplianceViolation violation : violations) {
            switch (violation.getSeverity()) {
                case "CRITICAL":
                    totalPenalty += 25;
                    break;
                case "HIGH":
                    totalPenalty += 15;
                    break;
                case "MEDIUM":
                    totalPenalty += 10;
                    break;
                case "LOW":
                    totalPenalty += 5;
                    break;
            }
        }

        return Math.max(0, 100.0 - totalPenalty);
    }

    /**
     * Exporte un rapport au format JSON
     */
    public String exportReportAsJson(ComplianceReport report) {
        try {
            // Utilisation d'une bibliothèque JSON comme Jackson
            // Pour la simplicité, on retourne une représentation basique
            return String.format(
                    "{\n" +
                            "  \"reportType\": \"%s\",\n" +
                            "  \"startDate\": \"%s\",\n" +
                            "  \"endDate\": \"%s\",\n" +
                            "  \"generatedAt\": \"%s\",\n" +
                            "  \"totalEvents\": %d,\n" +
                            "  \"complianceScore\": %.2f\n" +
                            "}",
                    report.getReportType(),
                    report.getStartDate().format(FORMATTER),
                    report.getEndDate().format(FORMATTER),
                    report.getGeneratedAt().format(FORMATTER),
                    report.getTotalEvents(),
                    report.getStatistics() != null ? report.getStatistics().getComplianceScore() : 0.0);
        } catch (Exception e) {
            log.error("Erreur lors de l'export JSON du rapport: {}", e.getMessage());
            throw new RuntimeException("Échec de l'export JSON", e);
        }
    }

    // Classes internes pour les DTOs du rapport

    public static class ComplianceReport {
        private ReportType reportType;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private LocalDateTime generatedAt;
        private int totalEvents;
        private List<AuditLog> auditEvents = new ArrayList<>();
        private List<DataChangeHistory> dataChanges = new ArrayList<>();
        private List<FieldLevelAudit> fieldAudits = new ArrayList<>();
        private List<ComplianceViolation> complianceViolations = new ArrayList<>();
        private AuditStatistics statistics;

        // Getters et setters
        public ReportType getReportType() {
            return reportType;
        }

        public void setReportType(ReportType reportType) {
            this.reportType = reportType;
        }

        public LocalDateTime getStartDate() {
            return startDate;
        }

        public void setStartDate(LocalDateTime startDate) {
            this.startDate = startDate;
        }

        public LocalDateTime getEndDate() {
            return endDate;
        }

        public void setEndDate(LocalDateTime endDate) {
            this.endDate = endDate;
        }

        public LocalDateTime getGeneratedAt() {
            return generatedAt;
        }

        public void setGeneratedAt(LocalDateTime generatedAt) {
            this.generatedAt = generatedAt;
        }

        public int getTotalEvents() {
            return totalEvents;
        }

        public void setTotalEvents(int totalEvents) {
            this.totalEvents = totalEvents;
        }

        public List<AuditLog> getAuditEvents() {
            return auditEvents;
        }

        public void setAuditEvents(List<AuditLog> auditEvents) {
            this.auditEvents = auditEvents;
        }

        public List<DataChangeHistory> getDataChanges() {
            return dataChanges;
        }

        public void setDataChanges(List<DataChangeHistory> dataChanges) {
            this.dataChanges = dataChanges;
        }

        public List<FieldLevelAudit> getFieldAudits() {
            return fieldAudits;
        }

        public void setFieldAudits(List<FieldLevelAudit> fieldAudits) {
            this.fieldAudits = fieldAudits;
        }

        public List<ComplianceViolation> getComplianceViolations() {
            return complianceViolations;
        }

        public void setComplianceViolations(List<ComplianceViolation> complianceViolations) {
            this.complianceViolations = complianceViolations;
        }

        public AuditStatistics getStatistics() {
            return statistics;
        }

        public void setStatistics(AuditStatistics statistics) {
            this.statistics = statistics;
        }
    }

    public static class AuditStatistics {
        private Map<String, Long> actionCounts = new HashMap<>();
        private Map<String, Long> changesByEntity = new HashMap<>();
        private Map<String, Long> changesByUser = new HashMap<>();
        private Map<String, Long> fieldChangesByEntity = new HashMap<>();
        private Map<String, Long> securityEventsByType = new HashMap<>();
        private double complianceScore = 100.0;

        // Getters et setters
        public Map<String, Long> getActionCounts() {
            return actionCounts;
        }

        public void setActionCounts(Map<String, Long> actionCounts) {
            this.actionCounts = actionCounts;
        }

        public Map<String, Long> getChangesByEntity() {
            return changesByEntity;
        }

        public void setChangesByEntity(Map<String, Long> changesByEntity) {
            this.changesByEntity = changesByEntity;
        }

        public Map<String, Long> getChangesByUser() {
            return changesByUser;
        }

        public void setChangesByUser(Map<String, Long> changesByUser) {
            this.changesByUser = changesByUser;
        }

        public Map<String, Long> getFieldChangesByEntity() {
            return fieldChangesByEntity;
        }

        public void setFieldChangesByEntity(Map<String, Long> fieldChangesByEntity) {
            this.fieldChangesByEntity = fieldChangesByEntity;
        }

        public Map<String, Long> getSecurityEventsByType() {
            return securityEventsByType;
        }

        public void setSecurityEventsByType(Map<String, Long> securityEventsByType) {
            this.securityEventsByType = securityEventsByType;
        }

        public double getComplianceScore() {
            return complianceScore;
        }

        public void setComplianceScore(double complianceScore) {
            this.complianceScore = complianceScore;
        }
    }

    public static class ComplianceViolation {
        private String violationType;
        private String description;
        private LocalDateTime timestamp;
        private String userId;
        private String severity;

        public ComplianceViolation(String violationType, String description, LocalDateTime timestamp,
                String userId, String severity) {
            this.violationType = violationType;
            this.description = description;
            this.timestamp = timestamp;
            this.userId = userId;
            this.severity = severity;
        }

        // Getters et setters
        public String getViolationType() {
            return violationType;
        }

        public void setViolationType(String violationType) {
            this.violationType = violationType;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }
    }

    public enum ReportType {
        FULL_AUDIT,
        DATA_CHANGES,
        SECURITY_EVENTS,
        COMPLIANCE_CHECK
    }
}