package com.bantuops.backend.service;

import com.bantuops.backend.aspect.ApiAuditInterceptor;
import com.bantuops.backend.aspect.PerformanceMonitoringAspect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service d'audit pour l'enregistrement des événements système
 * Conforme aux exigences 7.4, 7.5, 7.6, 6.1, 6.2 pour l'audit complet
 * 
 * Note: Cette implémentation utilise le logging pour l'instant.
 * L'implémentation complète avec base de données sera faite dans la tâche 7.1
 */
@Service
@Slf4j
public class AuditService {

    // Compteurs pour la détection d'abus
    private final Map<String, AtomicInteger> failedLoginAttempts = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastFailedAttempt = new ConcurrentHashMap<>();
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_DURATION_MINUTES = 30;

    // ==================== AUDIT DES APIS ====================

    /**
     * Enregistre un appel d'API de manière asynchrone
     */
    @Async
    public void logApiCall(ApiAuditInterceptor.ApiAuditInfo auditInfo) {
        try {
            log.info("API_CALL: TraceID={}, Method={}, URI={}, User={}, Status={}, Duration={}ms, Success={}", 
                    auditInfo.getTraceId(),
                    auditInfo.getMethod(),
                    auditInfo.getUri(),
                    auditInfo.getUsername(),
                    auditInfo.getResponseStatus(),
                    auditInfo.getDuration(),
                    auditInfo.isSuccess());

            // Logger les erreurs avec plus de détails
            if (!auditInfo.isSuccess()) {
                log.error("API_ERROR: TraceID={}, Error={}, Type={}", 
                        auditInfo.getTraceId(),
                        auditInfo.getErrorMessage(),
                        auditInfo.getErrorType());
            }

            // Alerter sur les requêtes lentes
            if (auditInfo.getDuration() > 5000) {
                log.warn("SLOW_API_CALL: TraceID={}, URI={}, Duration={}ms", 
                        auditInfo.getTraceId(),
                        auditInfo.getUri(),
                        auditInfo.getDuration());
            }

        } catch (Exception e) {
            log.error("Erreur lors de l'audit d'appel API: {}", e.getMessage());
        }
    }

    // ==================== AUDIT DE SÉCURITÉ ====================

    /**
     * Enregistre un événement de sécurité générique
     */
    @Async
    public void logSecurityEvent(String eventType, String principal, Map<String, Object> data, LocalDateTime timestamp) {
        log.info("SECURITY_EVENT: Type={}, Principal={}, Timestamp={}, Data={}", 
                eventType, principal, timestamp, data);
    }

    /**
     * Enregistre une connexion réussie
     */
    @Async
    public void logSuccessfulLogin(String username, String ipAddress, Map<String, Object> details) {
        // Réinitialiser le compteur d'échecs pour cette IP
        failedLoginAttempts.remove(ipAddress);
        lastFailedAttempt.remove(ipAddress);
        
        log.info("SUCCESSFUL_LOGIN: User={}, IP={}, Details={}", username, ipAddress, details);
    }

    /**
     * Enregistre un échec de connexion
     */
    @Async
    public void logFailedLogin(String username, String ipAddress, String reason, Map<String, Object> details) {
        // Incrémenter le compteur d'échecs
        failedLoginAttempts.computeIfAbsent(ipAddress, k -> new AtomicInteger(0)).incrementAndGet();
        lastFailedAttempt.put(ipAddress, LocalDateTime.now());
        
        log.warn("FAILED_LOGIN: User={}, IP={}, Reason={}, Details={}", username, ipAddress, reason, details);
    }

    /**
     * Enregistre une tentative avec credentials incorrects
     */
    @Async
    public void logBadCredentialsAttempt(String username, String ipAddress, Map<String, Object> details) {
        log.warn("BAD_CREDENTIALS: User={}, IP={}, Details={}", username, ipAddress, details);
    }

    /**
     * Enregistre une tentative sur compte désactivé
     */
    @Async
    public void logDisabledAccountAttempt(String username, String ipAddress, Map<String, Object> details) {
        log.warn("DISABLED_ACCOUNT_ATTEMPT: User={}, IP={}, Details={}", username, ipAddress, details);
    }

    /**
     * Enregistre une tentative sur compte expiré
     */
    @Async
    public void logExpiredAccountAttempt(String username, String ipAddress, Map<String, Object> details) {
        log.warn("EXPIRED_ACCOUNT_ATTEMPT: User={}, IP={}, Details={}", username, ipAddress, details);
    }

    /**
     * Enregistre une tentative sur compte verrouillé
     */
    @Async
    public void logLockedAccountAttempt(String username, String ipAddress, Map<String, Object> details) {
        log.warn("LOCKED_ACCOUNT_ATTEMPT: User={}, IP={}, Details={}", username, ipAddress, details);
    }

    /**
     * Enregistre un refus d'autorisation
     */
    @Async
    public void logAuthorizationDenied(String username, String resource, Map<String, Object> details) {
        log.warn("AUTHORIZATION_DENIED: User={}, Resource={}, Details={}", username, resource, details);
    }

    /**
     * Enregistre une déconnexion réussie
     */
    @Async
    public void logSuccessfulLogout(String username, String ipAddress, Map<String, Object> details) {
        log.info("SUCCESSFUL_LOGOUT: User={}, IP={}, Details={}", username, ipAddress, details);
    }

    /**
     * Enregistre la création d'une session
     */
    @Async
    public void logSessionCreated(String sessionId, String username, Map<String, Object> details) {
        log.info("SESSION_CREATED: SessionID={}, User={}, Details={}", sessionId, username, details);
    }

    /**
     * Vérifie si une adresse IP doit être bloquée
     */
    public boolean shouldBlockIpAddress(String ipAddress) {
        AtomicInteger attempts = failedLoginAttempts.get(ipAddress);
        LocalDateTime lastAttempt = lastFailedAttempt.get(ipAddress);
        
        if (attempts != null && lastAttempt != null) {
            // Vérifier si le délai de blocage est encore actif
            if (lastAttempt.plusMinutes(LOCKOUT_DURATION_MINUTES).isAfter(LocalDateTime.now())) {
                return attempts.get() >= MAX_FAILED_ATTEMPTS;
            } else {
                // Réinitialiser si le délai est expiré
                failedLoginAttempts.remove(ipAddress);
                lastFailedAttempt.remove(ipAddress);
            }
        }
        
        return false;
    }

    /**
     * Bloque une adresse IP
     */
    @Async
    public void blockIpAddress(String ipAddress, String reason) {
        log.error("IP_BLOCKED: IP={}, Reason={}", ipAddress, reason);
        // TODO: Implémenter le blocage effectif dans la tâche 7.1
    }

    // ==================== AUDIT DE PERFORMANCE ====================

    /**
     * Enregistre des métriques de performance
     */
    @Async
    public void logPerformanceMetrics(String methodName, Map<String, Object> metrics) {
        log.info("PERFORMANCE_METRICS: Method={}, Metrics={}", methodName, metrics);
    }

    /**
     * Enregistre une alerte de performance
     */
    @Async
    public void logPerformanceAlert(String alertType, String message, Map<String, Object> alertData) {
        log.error("PERFORMANCE_ALERT: Type={}, Message={}, Data={}", alertType, message, alertData);
    }

    // ==================== AUDIT MÉTIER ====================

    /**
     * Enregistre un événement d'authentification
     */
    @Async
    public void logAuthenticationEvent(Long userId, String event, String ipAddress) {
        log.info("AUTH_EVENT: UserID={}, Event={}, IP={}", userId, event, ipAddress);
    }

    /**
     * Enregistre la génération d'un bulletin de paie
     */
    @Async
    public void logPayslipGeneration(Long employeeId, YearMonth period) {
        log.info("PAYSLIP_GENERATED: EmployeeID={}, Period={}", employeeId, period);
    }

    /**
     * Enregistre la génération d'un PDF sécurisé
     */
    @Async
    public void logSecurePdfGeneration(Long employeeId, YearMonth period) {
        log.info("SECURE_PDF_GENERATED: EmployeeID={}, Period={}", employeeId, period);
    }

    /**
     * Enregistre la validation d'une signature
     */
    @Async
    public void logSignatureValidation(String documentId, boolean isValid) {
        log.info("SIGNATURE_VALIDATION: DocumentID={}, Valid={}", documentId, isValid);
    }

    /**
     * Enregistre la génération d'un bulletin avec template
     */
    @Async
    public void logPayslipGenerationWithTemplate(Long employeeId, YearMonth period, String templateName) {
        log.info("PAYSLIP_WITH_TEMPLATE: EmployeeID={}, Period={}, Template={}", 
                employeeId, period, templateName);
    }

    /**
     * Enregistre un calcul de paie
     */
    @Async
    public void logPayrollCalculation(Long employeeId, YearMonth period, Object result) {
        log.info("PAYROLL_CALCULATION: EmployeeID={}, Period={}, Result={}", 
                employeeId, period, result.getClass().getSimpleName());
    }

    /**
     * Enregistre la sauvegarde d'un enregistrement de paie
     */
    @Async
    public void logPayrollRecordSaved(Long recordId, Long employeeId) {
        log.info("PAYROLL_RECORD_SAVED: RecordID={}, EmployeeID={}", recordId, employeeId);
    }

    /**
     * Enregistre un calcul de taxes
     */
    @Async
    public void logTaxCalculation(Long employeeId, Object grossSalary, Object result) {
        log.info("TAX_CALCULATION: EmployeeID={}, GrossSalary={}, Result={}", 
                employeeId, grossSalary, result.getClass().getSimpleName());
    }

    /**
     * Enregistre un calcul d'heures supplémentaires
     */
    @Async
    public void logOvertimeCalculation(Long employeeId, Object overtimeHours, Object overtimeAmount) {
        log.info("OVERTIME_CALCULATION: EmployeeID={}, Hours={}, Amount={}", 
                employeeId, overtimeHours, overtimeAmount);
    }

    /**
     * Enregistre un calcul détaillé d'heures supplémentaires
     */
    @Async
    public void logDetailedOvertimeCalculation(Long employeeId, Object result) {
        log.info("DETAILED_OVERTIME_CALCULATION: EmployeeID={}, Result={}", 
                employeeId, result.getClass().getSimpleName());
    }

    /**
     * Enregistre un calcul de prime de rendement
     */
    @Async
    public void logPerformanceBonusCalculation(Long employeeId, Object metrics, Object baseBonus) {
        log.info("PERFORMANCE_BONUS_CALCULATION: EmployeeID={}, Metrics={}, Bonus={}", 
                employeeId, metrics, baseBonus);
    }

    /**
     * Enregistre une opération financière
     */
    @Async
    public void logFinancialOperation(String operationType, Long entityId, Map<String, Object> details) {
        log.info("FINANCIAL_OPERATION: Type={}, EntityID={}, Details={}", 
                operationType, entityId, details);
    }

    /**
     * Enregistre un accès aux données
     */
    @Async
    public void logDataAccess(String accessType, String description, Map<String, Object> details) {
        log.info("DATA_ACCESS: Type={}, Description={}, Details={}", 
                accessType, description, details);
    }

    /**
     * Enregistre un enregistrement d'assiduité
     */
    @Async
    public void logAttendanceRecord(Long employeeId, Long recordId, String action) {
        log.info("ATTENDANCE_RECORD: EmployeeID={}, RecordID={}, Action={}", 
                employeeId, recordId, action);
    }

    /**
     * Enregistre une action disciplinaire
     */
    @Async
    public void logDisciplinaryAction(Long employeeId, Long violationId, Object disciplinaryAction) {
        log.info("DISCIPLINARY_ACTION: EmployeeID={}, ViolationID={}, Action={}", 
                employeeId, violationId, disciplinaryAction);
    }

    /**
     * Enregistre la validation d'une justification
     */
    @Async
    public void logJustificationValidation(Long recordId, Long approverUserId, String action) {
        log.info("JUSTIFICATION_VALIDATION: RecordID={}, ApproverID={}, Action={}", 
                recordId, approverUserId, action);
    }

    /**
     * Enregistre la génération d'un rapport
     */
    @Async
    public void logReportGeneration(String reportId, String reportType, String period) {
        log.info("REPORT_GENERATION: ReportID={}, Type={}, Period={}", 
                reportId, reportType, period);
    }

    /**
     * Enregistre un export de données
     */
    @Async
    public void logDataExport(String dataType, String period, boolean anonymize) {
        log.info("DATA_EXPORT: Type={}, Period={}, Anonymized={}", 
                dataType, period, anonymize);
    }
}