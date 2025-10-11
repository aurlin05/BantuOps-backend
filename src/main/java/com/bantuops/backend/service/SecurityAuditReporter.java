package com.bantuops.backend.service;

import com.bantuops.backend.dto.SecurityAlert;
import com.bantuops.backend.dto.SecurityAuditReport;
import com.bantuops.backend.dto.ThreatAnalysis;
import com.bantuops.backend.entity.AuditLog;
import com.bantuops.backend.entity.FieldLevelAudit;
import com.bantuops.backend.repository.AuditLogRepository;
import com.bantuops.backend.repository.FieldLevelAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service de génération de rapports d'audit de sécurité
 * Conforme aux exigences 7.6, 2.4, 2.5 pour les rapports de sécurité
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityAuditReporter {

    private final AuditLogRepository auditLogRepository;
    private final FieldLevelAuditRepository fieldLevelAuditRepository;
    private final DataEncryptionService dataEncryptionService;

    // Seuils de détection des menaces
    private static final int FAILED_LOGIN_THRESHOLD = 5;
    private static final int SUSPICIOUS_ACCESS_THRESHOLD = 10;
    private static final int DATA_ACCESS_THRESHOLD = 50;
    private static final long THREAT_DETECTION_WINDOW_HOURS = 24;

    /**
     * Génère un rapport d'audit de sécurité complet
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    @Transactional(readOnly = true)
    public SecurityAuditReport generateSecurityAuditReport(LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Génération du rapport d'audit de sécurité - Période: {} à {}", startDate, endDate);

        try {
            SecurityAuditReport report = new SecurityAuditReport();
            report.setReportId(generateSecurityReportId());
            report.setStartDate(startDate);
            report.setEndDate(endDate);
            report.setGeneratedAt(LocalDateTime.now());
            report.setGeneratedBy(getCurrentUserId());

            // Collecte des événements de sécurité
            List<AuditLog> securityEvents = collectSecurityEvents(startDate, endDate);
            report.setSecurityEvents(securityEvents);

            // Génération des alertes de sécurité
            List<SecurityAlert> securityAlerts = generateSecurityAlerts(securityEvents, startDate, endDate);
            report.setSecurityAlerts(securityAlerts);

            // Analyse des menaces
            ThreatAnalysis threatAnalysis = performThreatAnalysis(securityEvents, securityAlerts);
            report.setThreatAnalysis(threatAnalysis);

            // Calcul des métriques de sécurité
            report.setSecurityMetrics(calculateSecurityMetrics(securityEvents, securityAlerts));

            // Recommandations de sécurité
            report.setSecurityRecommendations(generateSecurityRecommendations(threatAnalysis));

            log.info("Rapport d'audit de sécurité généré - ID: {}, Événements: {}, Alertes: {}", 
                    report.getReportId(), securityEvents.size(), securityAlerts.size());

            return report;

        } catch (Exception e) {
            log.error("Erreur lors de la génération du rapport d'audit de sécurité: {}", e.getMessage(), e);
            throw new RuntimeException("Échec de la génération du rapport d'audit de sécurité", e);
        }
    }

    /**
     * Génère des alertes de sécurité en temps réel
     */
    @Async
    public void generateRealTimeSecurityAlerts() {
        log.debug("Génération d'alertes de sécurité en temps réel");

        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime windowStart = now.minusHours(THREAT_DETECTION_WINDOW_HOURS);

            // Collecte des événements récents
            List<AuditLog> recentEvents = collectSecurityEvents(windowStart, now);

            // Génération des alertes
            List<SecurityAlert> alerts = generateSecurityAlerts(recentEvents, windowStart, now);

            // Traitement des alertes critiques
            alerts.stream()
                    .filter(alert -> alert.getSeverity() == SecurityAlert.Severity.CRITICAL)
                    .forEach(this::processImmediateSecurityAlert);

        } catch (Exception e) {
            log.error("Erreur lors de la génération d'alertes de sécurité en temps réel: {}", e.getMessage(), e);
        }
    }

    /**
     * Collecte les événements de sécurité pour la période donnée
     */
    private List<AuditLog> collectSecurityEvents(LocalDateTime startDate, LocalDateTime endDate) {
        // Types d'événements de sécurité à surveiller
        List<String> securityActions = Arrays.asList(
                "LOGIN_ATTEMPT", "LOGIN_SUCCESS", "LOGIN_FAILURE", "LOGOUT",
                "UNAUTHORIZED_ACCESS", "PERMISSION_DENIED", "DATA_ACCESS_VIOLATION",
                "SUSPICIOUS_ACTIVITY", "SECURITY_BREACH", "PASSWORD_CHANGE",
                "ROLE_CHANGE", "ACCOUNT_LOCKED", "ACCOUNT_UNLOCKED"
        );

        return auditLogRepository.findByTimestampBetween(startDate, endDate)
                .stream()
                .filter(log -> securityActions.contains(log.getAction().toString()) ||
                              isSecurityRelatedEntity(log.getEntityType()) ||
                              containsSecurityKeywords(log))
                .collect(Collectors.toList());
    }

    /**
     * Génère les alertes de sécurité basées sur les événements
     */
    private List<SecurityAlert> generateSecurityAlerts(List<AuditLog> securityEvents, 
                                                      LocalDateTime startDate, LocalDateTime endDate) {
        List<SecurityAlert> alerts = new ArrayList<>();

        // Détection des tentatives de connexion échouées
        alerts.addAll(detectFailedLoginAttempts(securityEvents));

        // Détection des accès suspects
        alerts.addAll(detectSuspiciousAccess(securityEvents));

        // Détection des violations de données
        alerts.addAll(detectDataViolations(securityEvents));

        // Détection des changements de permissions suspects
        alerts.addAll(detectSuspiciousPermissionChanges(securityEvents));

        // Détection des accès hors heures
        alerts.addAll(detectAfterHoursAccess(securityEvents));

        // Détection des accès géographiques suspects
        alerts.addAll(detectGeographicalAnomalies(securityEvents));

        return alerts;
    }

    /**
     * Détecte les tentatives de connexion échouées répétées
     */
    private List<SecurityAlert> detectFailedLoginAttempts(List<AuditLog> events) {
        Map<String, List<AuditLog>> failedLoginsByUser = events.stream()
                .filter(log -> "LOGIN_FAILURE".equals(log.getAction().toString()))
                .collect(Collectors.groupingBy(AuditLog::getUserId));

        return failedLoginsByUser.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= FAILED_LOGIN_THRESHOLD)
                .map(entry -> createSecurityAlert(
                        SecurityAlert.AlertType.FAILED_LOGIN_ATTEMPT,
                        SecurityAlert.Severity.HIGH,
                        String.format("Tentatives de connexion échouées répétées pour l'utilisateur %s (%d tentatives)", 
                                entry.getKey(), entry.getValue().size()),
                        entry.getValue().get(0).getTimestamp(),
                        entry.getKey(),
                        entry.getValue().get(0).getIpAddress(),
                        Map.of(
                                "userId", entry.getKey(),
                                "attemptCount", entry.getValue().size(),
                                "ipAddresses", entry.getValue().stream()
                                        .map(AuditLog::getIpAddress)
                                        .distinct()
                                        .collect(Collectors.toList())
                        )
                ))
                .collect(Collectors.toList());
    }

    /**
     * Détecte les accès suspects basés sur les patterns d'utilisation
     */
    private List<SecurityAlert> detectSuspiciousAccess(List<AuditLog> events) {
        List<SecurityAlert> alerts = new ArrayList<>();

        // Grouper par utilisateur
        Map<String, List<AuditLog>> eventsByUser = events.stream()
                .collect(Collectors.groupingBy(AuditLog::getUserId));

        for (Map.Entry<String, List<AuditLog>> userEvents : eventsByUser.entrySet()) {
            String userId = userEvents.getKey();
            List<AuditLog> userEventList = userEvents.getValue();

            // Détection d'activité excessive
            if (userEventList.size() > SUSPICIOUS_ACCESS_THRESHOLD) {
                alerts.add(createSecurityAlert(
                        SecurityAlert.AlertType.ABNORMAL_ACTIVITY,
                        SecurityAlert.Severity.MEDIUM,
                        String.format("Activité excessive détectée pour l'utilisateur %s (%d événements)", 
                                userId, userEventList.size()),
                        userEventList.get(0).getTimestamp(),
                        userId,
                        userEventList.get(0).getIpAddress(),
                        Map.of("userId", userId, "eventCount", userEventList.size())
                ));
            }

            // Détection d'accès depuis plusieurs IP
            Set<String> uniqueIPs = userEventList.stream()
                    .map(AuditLog::getIpAddress)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            if (uniqueIPs.size() > 3) {
                alerts.add(createSecurityAlert(
                        SecurityAlert.AlertType.SUSPICIOUS_TRANSACTION,
                        SecurityAlert.Severity.MEDIUM,
                        String.format("Accès depuis plusieurs adresses IP pour l'utilisateur %s", userId),
                        userEventList.get(0).getTimestamp(),
                        userId,
                        userEventList.get(0).getIpAddress(),
                        Map.of("userId", userId, "ipAddresses", uniqueIPs)
                ));
            }
        }

        return alerts;
    }

    /**
     * Détecte les violations d'accès aux données sensibles
     */
    private List<SecurityAlert> detectDataViolations(List<AuditLog> events) {
        List<SecurityAlert> alerts = new ArrayList<>();

        // Accès non autorisé aux données sensibles
        List<AuditLog> sensitiveDataAccess = events.stream()
                .filter(log -> log.isSensitiveData() && 
                              ("UNAUTHORIZED_ACCESS".equals(log.getAction().toString()) ||
                               "PERMISSION_DENIED".equals(log.getAction().toString())))
                .collect(Collectors.toList());

        if (!sensitiveDataAccess.isEmpty()) {
            AuditLog firstViolation = sensitiveDataAccess.get(0);
            alerts.add(createSecurityAlert(
                    SecurityAlert.AlertType.UNAUTHORIZED_ACCESS,
                    SecurityAlert.Severity.CRITICAL,
                    String.format("Tentatives d'accès non autorisé aux données sensibles (%d événements)", 
                            sensitiveDataAccess.size()),
                    firstViolation.getTimestamp(),
                    firstViolation.getUserId(),
                    firstViolation.getIpAddress(),
                    Map.of("violationCount", sensitiveDataAccess.size())
            ));
        }

        // Volume élevé d'accès aux données
        Map<String, Long> dataAccessByUser = events.stream()
                .filter(log -> log.getEntityType() != null && 
                              (log.getEntityType().contains("Employee") || 
                               log.getEntityType().contains("Payroll") ||
                               log.getEntityType().contains("Financial")))
                .collect(Collectors.groupingBy(AuditLog::getUserId, Collectors.counting()));

        dataAccessByUser.entrySet().stream()
                .filter(entry -> entry.getValue() > DATA_ACCESS_THRESHOLD)
                .forEach(entry -> {
                    // Trouver un événement représentatif pour cet utilisateur
                    AuditLog representativeEvent = events.stream()
                            .filter(log -> entry.getKey().equals(log.getUserId()))
                            .findFirst()
                            .orElse(null);
                    
                    alerts.add(createSecurityAlert(
                            SecurityAlert.AlertType.DATA_EXPORT_VIOLATION,
                            SecurityAlert.Severity.HIGH,
                            String.format("Volume élevé d'accès aux données par l'utilisateur %s (%d accès)", 
                                    entry.getKey(), entry.getValue()),
                            representativeEvent != null ? representativeEvent.getTimestamp() : LocalDateTime.now(),
                            entry.getKey(),
                            representativeEvent != null ? representativeEvent.getIpAddress() : null,
                            Map.of("userId", entry.getKey(), "accessCount", entry.getValue())
                    ));
                });

        return alerts;
    }

    /**
     * Détecte les changements de permissions suspects
     */
    private List<SecurityAlert> detectSuspiciousPermissionChanges(List<AuditLog> events) {
        return events.stream()
                .filter(log -> "ROLE_CHANGE".equals(log.getAction().toString()) ||
                              "PERMISSION_GRANTED".equals(log.getAction().toString()) ||
                              "PERMISSION_REVOKED".equals(log.getAction().toString()))
                .map(log -> createSecurityAlert(
                        SecurityAlert.AlertType.PRIVILEGE_ESCALATION,
                        SecurityAlert.Severity.HIGH,
                        String.format("Changement de permissions détecté: %s pour l'utilisateur %s", 
                                log.getAction(), log.getUserId()),
                        log.getTimestamp(),
                        log.getUserId(),
                        log.getIpAddress(),
                        Map.of(
                                "userId", log.getUserId(),
                                "action", log.getAction().toString(),
                                "changedBy", log.getLastModifiedBy() != null ? log.getLastModifiedBy() : "SYSTEM"
                        )
                ))
                .collect(Collectors.toList());
    }

    /**
     * Détecte les accès hors heures ouvrables
     */
    private List<SecurityAlert> detectAfterHoursAccess(List<AuditLog> events) {
        return events.stream()
                .filter(this::isAfterHours)
                .filter(log -> isHighPrivilegeAction(log.getAction().toString()))
                .map(log -> createSecurityAlert(
                        SecurityAlert.AlertType.ABNORMAL_ACTIVITY,
                        SecurityAlert.Severity.MEDIUM,
                        String.format("Accès hors heures détecté: %s par l'utilisateur %s", 
                                log.getAction(), log.getUserId()),
                        log.getTimestamp(),
                        log.getUserId(),
                        log.getIpAddress(),
                        Map.of("userId", log.getUserId(), "action", log.getAction().toString())
                ))
                .collect(Collectors.toList());
    }

    /**
     * Détecte les anomalies géographiques (placeholder pour future implémentation)
     */
    private List<SecurityAlert> detectGeographicalAnomalies(List<AuditLog> events) {
        // TODO: Implémenter la détection géographique basée sur les adresses IP
        return new ArrayList<>();
    }

    /**
     * Effectue une analyse des menaces basée sur les événements et alertes
     */
    private ThreatAnalysis performThreatAnalysis(List<AuditLog> events, List<SecurityAlert> alerts) {
        ThreatAnalysis analysis = new ThreatAnalysis();
        analysis.setAnalysisDate(LocalDateTime.now());

        // Calcul du niveau de menace global
        ThreatAnalysis.ThreatLevel overallThreatLevel = calculateOverallThreatLevel(alerts);
        analysis.setOverallThreatLevel(overallThreatLevel);

        // Analyse des patterns d'attaque
        analysis.setAttackPatterns(identifyAttackPatterns(events, alerts));

        // Identification des utilisateurs à risque
        analysis.setHighRiskUsers(identifyHighRiskUsers(events, alerts));

        // Analyse des vulnérabilités
        analysis.setVulnerabilities(identifyVulnerabilities(events, alerts));

        // Recommandations d'atténuation
        analysis.setMitigationRecommendations(generateMitigationRecommendations(alerts));

        return analysis;
    }

    /**
     * Calcule les métriques de sécurité
     */
    private Map<String, Object> calculateSecurityMetrics(List<AuditLog> events, List<SecurityAlert> alerts) {
        Map<String, Object> metrics = new HashMap<>();

        // Métriques générales
        metrics.put("totalSecurityEvents", events.size());
        metrics.put("totalSecurityAlerts", alerts.size());

        // Métriques par sévérité
        Map<String, Long> alertsBySeverity = alerts.stream()
                .collect(Collectors.groupingBy(
                        alert -> alert.getSeverity().toString(),
                        Collectors.counting()
                ));
        metrics.put("alertsBySeverity", alertsBySeverity);

        // Métriques par type d'alerte
        Map<String, Long> alertsByType = alerts.stream()
                .collect(Collectors.groupingBy(
                        alert -> alert.getType().toString(),
                        Collectors.counting()
                ));
        metrics.put("alertsByType", alertsByType);

        // Métriques temporelles
        if (!events.isEmpty()) {
            LocalDateTime firstEvent = events.stream()
                    .map(AuditLog::getTimestamp)
                    .min(LocalDateTime::compareTo)
                    .orElse(null);
            LocalDateTime lastEvent = events.stream()
                    .map(AuditLog::getTimestamp)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);

            metrics.put("firstSecurityEvent", firstEvent);
            metrics.put("lastSecurityEvent", lastEvent);
        }

        // Score de sécurité (0-100)
        int securityScore = calculateSecurityScore(events, alerts);
        metrics.put("securityScore", securityScore);

        return metrics;
    }

    // ==================== MÉTHODES UTILITAIRES ====================

    private SecurityAlert createSecurityAlert(SecurityAlert.AlertType alertType, SecurityAlert.Severity severity,
                                             String description, LocalDateTime timestamp, String userId, 
                                             String ipAddress, Map<String, Object> metadata) {
        return SecurityAlert.builder()
                .alertType(alertType)
                .severity(severity)
                .description(description)
                .timestamp(timestamp)
                .userId(userId)
                .ipAddress(ipAddress)
                .metadata(metadata)
                .resolved(false)
                .build();
    }

    private boolean isSecurityRelatedEntity(String entityType) {
        if (entityType == null) return false;
        return entityType.contains("User") || entityType.contains("Role") || 
               entityType.contains("Permission") || entityType.contains("Security");
    }

    private boolean containsSecurityKeywords(AuditLog log) {
        String[] securityKeywords = {"password", "login", "auth", "security", "permission", "role"};
        String logContent = (log.getOldValues() + " " + log.getNewValues()).toLowerCase();
        return Arrays.stream(securityKeywords).anyMatch(logContent::contains);
    }

    private boolean isAfterHours(AuditLog log) {
        int hour = log.getTimestamp().getHour();
        return hour < 7 || hour > 19; // Hors heures 7h-19h
    }

    private boolean isHighPrivilegeAction(String action) {
        String[] highPrivilegeActions = {
                "DELETE", "UPDATE", "ROLE_CHANGE", "PERMISSION_GRANTED",
                "DATA_EXPORT", "FINANCIAL_CALCULATION", "PAYROLL_CALCULATION"
        };
        return Arrays.asList(highPrivilegeActions).contains(action);
    }

    private ThreatAnalysis.ThreatLevel calculateOverallThreatLevel(List<SecurityAlert> alerts) {
        long criticalAlerts = alerts.stream()
                .mapToLong(alert -> alert.getSeverity() == SecurityAlert.Severity.CRITICAL ? 1 : 0)
                .sum();
        long highAlerts = alerts.stream()
                .mapToLong(alert -> alert.getSeverity() == SecurityAlert.Severity.HIGH ? 1 : 0)
                .sum();

        if (criticalAlerts > 0) return ThreatAnalysis.ThreatLevel.CRITICAL;
        if (highAlerts > 3) return ThreatAnalysis.ThreatLevel.HIGH;
        if (alerts.size() > 10) return ThreatAnalysis.ThreatLevel.MEDIUM;
        return ThreatAnalysis.ThreatLevel.LOW;
    }

    private List<String> identifyAttackPatterns(List<AuditLog> events, List<SecurityAlert> alerts) {
        List<String> patterns = new ArrayList<>();

        // Pattern de force brute
        if (alerts.stream().anyMatch(alert -> alert.getAlertType() == SecurityAlert.AlertType.FAILED_LOGIN_ATTEMPT)) {
            patterns.add("Attaque par force brute détectée");
        }

        // Pattern d'escalade de privilèges
        if (alerts.stream().anyMatch(alert -> alert.getAlertType() == SecurityAlert.AlertType.PRIVILEGE_ESCALATION)) {
            patterns.add("Tentative d'escalade de privilèges");
        }

        // Pattern d'accès excessif
        if (alerts.stream().anyMatch(alert -> alert.getAlertType() == SecurityAlert.AlertType.ABNORMAL_ACTIVITY)) {
            patterns.add("Pattern d'accès excessif aux ressources");
        }

        return patterns;
    }

    private List<String> identifyHighRiskUsers(List<AuditLog> events, List<SecurityAlert> alerts) {
        return alerts.stream()
                .filter(alert -> alert.getUserId() != null)
                .map(SecurityAlert::getUserId)
                .collect(Collectors.groupingBy(userId -> userId, Collectors.counting()))
                .entrySet().stream()
                .filter(entry -> entry.getValue() > 2) // Utilisateurs avec plus de 2 alertes
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private List<String> identifyVulnerabilities(List<AuditLog> events, List<SecurityAlert> alerts) {
        List<String> vulnerabilities = new ArrayList<>();

        if (alerts.stream().anyMatch(alert -> alert.getAlertType() == SecurityAlert.AlertType.FAILED_LOGIN_ATTEMPT)) {
            vulnerabilities.add("Système vulnérable aux attaques par force brute");
        }

        if (alerts.stream().anyMatch(alert -> alert.getAlertType() == SecurityAlert.AlertType.UNAUTHORIZED_ACCESS)) {
            vulnerabilities.add("Contrôles d'accès aux données insuffisants");
        }

        return vulnerabilities;
    }

    private List<String> generateMitigationRecommendations(List<SecurityAlert> alerts) {
        List<String> recommendations = new ArrayList<>();

        if (alerts.stream().anyMatch(alert -> alert.getAlertType() == SecurityAlert.AlertType.FAILED_LOGIN_ATTEMPT)) {
            recommendations.add("Implémenter un système de verrouillage de compte après échecs répétés");
            recommendations.add("Activer l'authentification à deux facteurs");
        }

        if (alerts.stream().anyMatch(alert -> alert.getAlertType() == SecurityAlert.AlertType.ABNORMAL_ACTIVITY)) {
            recommendations.add("Réviser les permissions utilisateur et appliquer le principe du moindre privilège");
        }

        if (alerts.stream().anyMatch(alert -> alert.getAlertType() == SecurityAlert.AlertType.ABNORMAL_ACTIVITY)) {
            recommendations.add("Configurer des restrictions d'accès basées sur les heures");
        }

        return recommendations;
    }

    private List<String> generateSecurityRecommendations(ThreatAnalysis threatAnalysis) {
        List<String> recommendations = new ArrayList<>();
        recommendations.addAll(threatAnalysis.getMitigationRecommendations());

        // Recommandations générales basées sur le niveau de menace
        switch (threatAnalysis.getOverallThreatLevel()) {
            case CRITICAL:
                recommendations.add("Activation immédiate du plan de réponse aux incidents");
                recommendations.add("Audit de sécurité complet requis");
                break;
            case HIGH:
                recommendations.add("Renforcement des contrôles de sécurité");
                recommendations.add("Surveillance accrue des activités suspectes");
                break;
            case MEDIUM:
                recommendations.add("Révision des politiques de sécurité");
                break;
            case LOW:
                recommendations.add("Maintenir la surveillance de routine");
                break;
        }

        return recommendations;
    }

    private int calculateSecurityScore(List<AuditLog> events, List<SecurityAlert> alerts) {
        int baseScore = 100;

        // Déduction basée sur les alertes
        for (SecurityAlert alert : alerts) {
            switch (alert.getSeverity()) {
                case CRITICAL:
                    baseScore -= 20;
                    break;
                case HIGH:
                    baseScore -= 10;
                    break;
                case MEDIUM:
                    baseScore -= 5;
                    break;
                case LOW:
                    baseScore -= 2;
                    break;
            }
        }

        return Math.max(0, baseScore); // Score minimum de 0
    }

    private void processImmediateSecurityAlert(SecurityAlert alert) {
        log.warn("ALERTE SÉCURITÉ CRITIQUE: {}", alert.getMessage());
        // TODO: Implémenter les actions automatiques (notifications, blocages, etc.)
    }

    private String generateSecurityReportId() {
        return "SEC_AUDIT_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String getCurrentUserId() {
        // TODO: Récupérer depuis le contexte de sécurité Spring
        return "SYSTEM";
    }
}