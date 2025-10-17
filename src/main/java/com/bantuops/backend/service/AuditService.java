package com.bantuops.backend.service;

import com.bantuops.backend.aspect.ApiAuditInterceptor;
import com.bantuops.backend.entity.AuditLog;
import com.bantuops.backend.repository.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service d'audit pour l'enregistrement asynchrone des événements système
 * Conforme aux exigences 7.4, 7.5, 7.6 pour l'audit complet avec persistance en base
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    // Compteurs pour la détection d'abus
    private final Map<String, AtomicInteger> failedLoginAttempts = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastFailedAttempt = new ConcurrentHashMap<>();
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_DURATION_MINUTES = 30;

    // ==================== AUDIT DES APIS ====================

    /**
     * Enregistre un appel d'API de manière asynchrone avec persistance en base
     */
    @Async
    public void logApiCall(ApiAuditInterceptor.ApiAuditInfo auditInfo) {
        try {
            // Log traditionnel pour compatibilité
            log.info("API_CALL: TraceID={}, Method={}, URI={}, User={}, Status={}, Duration={}ms, Success={}", 
                    auditInfo.getTraceId(),
                    auditInfo.getMethod(),
                    auditInfo.getUri(),
                    auditInfo.getUsername(),
                    auditInfo.getResponseStatus(),
                    auditInfo.getDuration(),
                    auditInfo.isSuccess());

            // Persistance en base de données
            AuditLog auditLog = AuditLog.builder()
                    .entityType("API_CALL")
                    .entityId(0L) // Pas d'entité spécifique pour les appels API
                    .action(auditInfo.isSuccess() ? AuditLog.AuditAction.VIEW : AuditLog.AuditAction.UNAUTHORIZED_ACCESS)
                    .userId(auditInfo.getUsername())
                    .userRole(getCurrentUserRole())
                    .ipAddress(getCurrentClientIp())
                    .userAgent(getCurrentUserAgent())
                    .sessionId(getCurrentSessionId())
                    .timestamp(LocalDateTime.now())
                    .description(String.format("%s %s - Status: %d - Duration: %dms", 
                            auditInfo.getMethod(), auditInfo.getUri(), 
                            auditInfo.getResponseStatus(), auditInfo.getDuration()))
                    .metadata(createApiCallMetadata(auditInfo))
                    .sensitiveData(isSensitiveApiCall(auditInfo.getUri()))
                    .severity(auditInfo.isSuccess() ? "INFO" : "WARNING")
                    .build();

            auditLogRepository.save(auditLog);

            // Logger les erreurs avec plus de détails
            if (!auditInfo.isSuccess()) {
                log.error("API_ERROR: TraceID={}, Error={}, Type={}", 
                        auditInfo.getTraceId(),
                        auditInfo.getErrorMessage(),
                        auditInfo.getErrorType());
                
                // Créer un log d'audit spécifique pour l'erreur
                logSecurityEvent("API_ERROR", auditInfo.getUsername(), 
                        Map.of("traceId", auditInfo.getTraceId(),
                               "error", auditInfo.getErrorMessage(),
                               "errorType", auditInfo.getErrorType(),
                               "uri", auditInfo.getUri()),
                        LocalDateTime.now());
            }

            // Alerter sur les requêtes lentes
            if (auditInfo.getDuration() > 5000) {
                log.warn("SLOW_API_CALL: TraceID={}, URI={}, Duration={}ms", 
                        auditInfo.getTraceId(),
                        auditInfo.getUri(),
                        auditInfo.getDuration());
                
                logPerformanceAlert("SLOW_API_CALL", 
                        String.format("Requête lente détectée: %s", auditInfo.getUri()),
                        Map.of("traceId", auditInfo.getTraceId(),
                               "duration", auditInfo.getDuration(),
                               "uri", auditInfo.getUri()));
            }

        } catch (Exception e) {
            log.error("Erreur lors de l'audit d'appel API: {}", e.getMessage());
        }
    }

    // ==================== AUDIT DE SÉCURITÉ ====================

    /**
     * Enregistre un événement de sécurité générique avec persistance
     */
    @Async
    public void logSecurityEvent(String eventType, String principal, Map<String, Object> data, LocalDateTime timestamp) {
        try {
            log.info("SECURITY_EVENT: Type={}, Principal={}, Timestamp={}, Data={}", 
                    eventType, principal, timestamp, data);

            AuditLog auditLog = AuditLog.builder()
                    .entityType("SECURITY_EVENT")
                    .entityId(0L)
                    .action(getSecurityAction(eventType))
                    .userId(principal)
                    .userRole(getCurrentUserRole())
                    .ipAddress(getCurrentClientIp())
                    .userAgent(getCurrentUserAgent())
                    .sessionId(getCurrentSessionId())
                    .timestamp(timestamp)
                    .description(String.format("Événement de sécurité: %s", eventType))
                    .metadata(serializeToJson(data))
                    .sensitiveData(true)
                    .severity(getSecurityEventSeverity(eventType))
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Erreur lors de l'enregistrement de l'événement de sécurité: {}", e.getMessage());
        }
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
        try {
            log.info("DATA_EXPORT: Type={}, Period={}, Anonymized={}", 
                    dataType, period, anonymize);

            AuditLog auditLog = AuditLog.builder()
                    .entityType("DATA_EXPORT")
                    .entityId(0L)
                    .action(AuditLog.AuditAction.EXPORT)
                    .userId(getCurrentUserId())
                    .userRole(getCurrentUserRole())
                    .ipAddress(getCurrentClientIp())
                    .userAgent(getCurrentUserAgent())
                    .sessionId(getCurrentSessionId())
                    .timestamp(LocalDateTime.now())
                    .description(String.format("Export de données: %s pour la période %s", dataType, period))
                    .metadata(serializeToJson(Map.of("dataType", dataType, "period", period, "anonymized", anonymize)))
                    .sensitiveData(!anonymize)
                    .severity("HIGH")
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Erreur lors de l'enregistrement de l'export de données: {}", e.getMessage());
        }
    }

    // ==================== MÉTHODES UTILITAIRES ====================

    /**
     * Récupère l'ID de l'utilisateur actuel
     */
    private String getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && 
                !"anonymousUser".equals(authentication.getPrincipal())) {
                return authentication.getName();
            }
        } catch (Exception e) {
            log.debug("Impossible de récupérer l'ID utilisateur: {}", e.getMessage());
        }
        return "system";
    }

    /**
     * Récupère le rôle de l'utilisateur actuel
     */
    private String getCurrentUserRole() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getAuthorities() != null) {
                return authentication.getAuthorities().stream()
                        .map(authority -> authority.getAuthority())
                        .reduce((a, b) -> a + "," + b)
                        .orElse("USER");
            }
        } catch (Exception e) {
            log.debug("Impossible de récupérer le rôle utilisateur: {}", e.getMessage());
        }
        return "UNKNOWN";
    }

    /**
     * Récupère l'adresse IP du client actuel
     */
    private String getCurrentClientIp() {
        try {
            ServletRequestAttributes requestAttributes = 
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            
            if (requestAttributes != null) {
                HttpServletRequest request = requestAttributes.getRequest();
                
                String xForwardedFor = request.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                    return xForwardedFor.split(",")[0].trim();
                }
                
                String xRealIp = request.getHeader("X-Real-IP");
                if (xRealIp != null && !xRealIp.isEmpty()) {
                    return xRealIp;
                }
                
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            log.debug("Impossible de récupérer l'IP client: {}", e.getMessage());
        }
        return "unknown";
    }

    /**
     * Récupère le User-Agent du client actuel
     */
    private String getCurrentUserAgent() {
        try {
            ServletRequestAttributes requestAttributes = 
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            
            if (requestAttributes != null) {
                HttpServletRequest request = requestAttributes.getRequest();
                return request.getHeader("User-Agent");
            }
        } catch (Exception e) {
            log.debug("Impossible de récupérer le User-Agent: {}", e.getMessage());
        }
        return "unknown";
    }

    /**
     * Récupère l'ID de session actuel
     */
    private String getCurrentSessionId() {
        try {
            ServletRequestAttributes requestAttributes = 
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            
            if (requestAttributes != null) {
                HttpServletRequest request = requestAttributes.getRequest();
                return request.getSession(false) != null ? request.getSession().getId() : null;
            }
        } catch (Exception e) {
            log.debug("Impossible de récupérer l'ID de session: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Sérialise un objet en JSON
     */
    private String serializeToJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.warn("Erreur lors de la sérialisation JSON: {}", e.getMessage());
            return object.toString();
        }
    }

    /**
     * Crée les métadonnées pour un appel API
     */
    private String createApiCallMetadata(ApiAuditInterceptor.ApiAuditInfo auditInfo) {
        Map<String, Object> metadata = Map.of(
                "traceId", auditInfo.getTraceId(),
                "method", auditInfo.getMethod(),
                "uri", auditInfo.getUri(),
                "responseStatus", auditInfo.getResponseStatus(),
                "duration", auditInfo.getDuration(),
                "success", auditInfo.isSuccess()
        );
        return serializeToJson(metadata);
    }

    /**
     * Détermine si un appel API concerne des données sensibles
     */
    private boolean isSensitiveApiCall(String uri) {
        return uri.contains("/payroll") || 
               uri.contains("/financial") || 
               uri.contains("/employee") ||
               uri.contains("/invoice") ||
               uri.contains("/transaction");
    }

    /**
     * Détermine l'action d'audit pour un événement de sécurité
     */
    private AuditLog.AuditAction getSecurityAction(String eventType) {
        return switch (eventType.toUpperCase()) {
            case "LOGIN_FAILED", "BAD_CREDENTIALS", "DISABLED_ACCOUNT", "EXPIRED_ACCOUNT", "LOCKED_ACCOUNT" -> 
                AuditLog.AuditAction.FAILED_LOGIN_ATTEMPT;
            case "AUTHORIZATION_DENIED", "ACCESS_DENIED" -> 
                AuditLog.AuditAction.UNAUTHORIZED_ACCESS;
            case "API_ERROR" -> 
                AuditLog.AuditAction.ABNORMAL_ACTIVITY;
            default -> 
                AuditLog.AuditAction.VIEW;
        };
    }

    /**
     * Détermine la sévérité d'un événement de sécurité
     */
    private String getSecurityEventSeverity(String eventType) {
        return switch (eventType.toUpperCase()) {
            case "LOGIN_FAILED", "BAD_CREDENTIALS" -> "MEDIUM";
            case "DISABLED_ACCOUNT", "EXPIRED_ACCOUNT", "LOCKED_ACCOUNT" -> "HIGH";
            case "AUTHORIZATION_DENIED", "ACCESS_DENIED" -> "HIGH";
            case "API_ERROR" -> "MEDIUM";
            case "SECURITY_BREACH", "SYSTEM_COMPROMISE" -> "CRITICAL";
            default -> "LOW";
        };
    }

    // ==================== MÉTHODES D'AUDIT AVANCÉES ====================

    /**
     * Enregistre un événement d'audit avec tous les détails
     */
    @Async
    public void logAuditEvent(String entityType, Long entityId, AuditLog.AuditAction action, 
                             String description, Object oldValue, Object newValue, 
                             String reason, boolean sensitiveData) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .entityType(entityType)
                    .entityId(entityId)
                    .action(action)
                    .userId(getCurrentUserId())
                    .userRole(getCurrentUserRole())
                    .ipAddress(getCurrentClientIp())
                    .userAgent(getCurrentUserAgent())
                    .sessionId(getCurrentSessionId())
                    .timestamp(LocalDateTime.now())
                    .description(description)
                    .oldValues(oldValue != null ? serializeToJson(oldValue) : null)
                    .newValues(newValue != null ? serializeToJson(newValue) : null)
                    .reason(reason)
                    .sensitiveData(sensitiveData)
                    .severity(sensitiveData ? "HIGH" : "MEDIUM")
                    .build();

            auditLogRepository.save(auditLog);
            
            log.info("AUDIT_EVENT: Entity={}, ID={}, Action={}, User={}, Sensitive={}", 
                    entityType, entityId, action, getCurrentUserId(), sensitiveData);
                    
        } catch (Exception e) {
            log.error("Erreur lors de l'enregistrement de l'événement d'audit: {}", e.getMessage());
        }
    }

    /**
     * Enregistre une violation de règle métier
     */
    @Async
    public void logBusinessRuleViolation(String ruleName, String entityType, Long entityId, 
                                        String violationDetails, Map<String, Object> context) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .entityType(entityType)
                    .entityId(entityId)
                    .action(AuditLog.AuditAction.BUSINESS_RULE_VIOLATION)
                    .userId(getCurrentUserId())
                    .userRole(getCurrentUserRole())
                    .ipAddress(getCurrentClientIp())
                    .userAgent(getCurrentUserAgent())
                    .sessionId(getCurrentSessionId())
                    .timestamp(LocalDateTime.now())
                    .description(String.format("Violation de règle métier: %s - %s", ruleName, violationDetails))
                    .metadata(serializeToJson(context))
                    .sensitiveData(true)
                    .severity("HIGH")
                    .build();

            auditLogRepository.save(auditLog);
            
            log.warn("BUSINESS_RULE_VIOLATION: Rule={}, Entity={}, ID={}, Details={}", 
                    ruleName, entityType, entityId, violationDetails);
                    
        } catch (Exception e) {
            log.error("Erreur lors de l'enregistrement de la violation de règle métier: {}", e.getMessage());
        }
    }

    /**
     * Enregistre une tentative de compromission système
     */
    @Async
    public void logSystemCompromiseAttempt(String attackType, String description, 
                                          Map<String, Object> attackDetails) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .entityType("SYSTEM_SECURITY")
                    .entityId(0L)
                    .action(AuditLog.AuditAction.SYSTEM_COMPROMISE)
                    .userId(getCurrentUserId())
                    .userRole(getCurrentUserRole())
                    .ipAddress(getCurrentClientIp())
                    .userAgent(getCurrentUserAgent())
                    .sessionId(getCurrentSessionId())
                    .timestamp(LocalDateTime.now())
                    .description(String.format("Tentative de compromission: %s - %s", attackType, description))
                    .metadata(serializeToJson(attackDetails))
                    .sensitiveData(true)
                    .severity("CRITICAL")
                    .build();

            auditLogRepository.save(auditLog);
            
            log.error("SYSTEM_COMPROMISE_ATTEMPT: Type={}, Description={}, IP={}", 
                    attackType, description, getCurrentClientIp());
                    
        } catch (Exception e) {
            log.error("Erreur lors de l'enregistrement de la tentative de compromission: {}", e.getMessage());
        }
    }
}