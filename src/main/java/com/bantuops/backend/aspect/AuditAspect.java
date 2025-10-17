package com.bantuops.backend.aspect;

import com.bantuops.backend.config.DatabaseConfig;
import com.bantuops.backend.entity.AuditLog;
import com.bantuops.backend.listener.AuditEventListener;
import com.bantuops.backend.security.CustomUserPrincipal;
import com.bantuops.backend.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Aspect pour l'audit automatique des méthodes critiques
 * Conforme aux exigences 7.4, 7.5, 7.6 pour l'audit complet
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final DatabaseConfig.DatabaseAuditService databaseAuditService;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    // ==================== POINTCUTS ====================

    /**
     * Pointcut pour les méthodes de service critiques
     */
    @Pointcut("execution(* com.bantuops.backend.service.PayrollCalculationService.*(..))")
    public void payrollServiceMethods() {}

    /**
     * Pointcut pour les méthodes financières
     */
    @Pointcut("execution(* com.bantuops.backend.service.FinancialService.*(..))")
    public void financialServiceMethods() {}

    /**
     * Pointcut pour les méthodes de gestion RH
     */
    @Pointcut("execution(* com.bantuops.backend.service.HRManagementService.*(..))")
    public void hrServiceMethods() {}

    /**
     * Pointcut pour les opérations de base de données
     */
    @Pointcut("@annotation(org.springframework.transaction.annotation.Transactional) || " +
              "execution(* com.bantuops.backend.repository.*.*(..))")
    public void databaseOperations() {}

    /**
     * Pointcut pour les méthodes sensibles (calculs, modifications de données)
     */
    @Pointcut("payrollServiceMethods() || financialServiceMethods() || hrServiceMethods()")
    public void sensitiveOperations() {}

    // ==================== ADVICE ====================

    /**
     * Audit des opérations de base de données
     */
    @Around("databaseOperations()")
    public Object auditDatabaseOperations(ProceedingJoinPoint joinPoint) throws Throwable {
        // Set audit context before database operations
        setDatabaseAuditContext();
        
        try {
            return joinPoint.proceed();
        } finally {
            // Clear audit context after database operations
            databaseAuditService.clearAuditContext();
        }
    }

    /**
     * Audit des opérations sensibles
     */
    @Around("sensitiveOperations()")
    public Object auditSensitiveOperations(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        Object[] args = joinPoint.getArgs();
        
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("SENSITIVE_OPERATION_START: {}.{} avec {} arguments", 
                    className, methodName, args.length);
            
            // Publier un événement d'audit pour le début de l'opération
            eventPublisher.publishEvent(new AuditEventListener.AuditEvent(
                className,
                extractEntityId(args),
                AuditLog.AuditAction.VIEW,
                String.format("Début d'opération sensible: %s", methodName),
                null,
                args,
                "Opération métier critique",
                true
            ));
            
            Object result = joinPoint.proceed();
            
            long duration = System.currentTimeMillis() - startTime;
            
            log.info("SENSITIVE_OPERATION_SUCCESS: {}.{} terminée en {}ms", 
                    className, methodName, duration);
            
            // Publier un événement d'audit pour le succès de l'opération
            eventPublisher.publishEvent(new AuditEventListener.AuditEvent(
                className,
                extractEntityId(args),
                getActionFromMethodName(methodName),
                String.format("Opération sensible réussie: %s", methodName),
                args,
                result,
                "Opération métier critique terminée avec succès",
                true
            ));
            
            // Enregistrer les métriques de performance
            eventPublisher.publishEvent(new AuditEventListener.PerformanceEvent(
                methodName,
                Map.of(
                    "duration", duration,
                    "className", className,
                    "argumentCount", args.length,
                    "success", true
                )
            ));
            
            return result;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            
            log.error("SENSITIVE_OPERATION_ERROR: {}.{} échouée après {}ms: {}", 
                    className, methodName, duration, e.getMessage());
            
            // Publier un événement d'audit pour l'échec de l'opération
            eventPublisher.publishEvent(new AuditEventListener.AuditEvent(
                className,
                extractEntityId(args),
                AuditLog.AuditAction.ABNORMAL_ACTIVITY,
                String.format("Échec d'opération sensible: %s - %s", methodName, e.getMessage()),
                args,
                null,
                "Erreur lors d'une opération métier critique",
                true
            ));
            
            // Publier un événement de sécurité si c'est une erreur critique
            if (isCriticalError(e)) {
                eventPublisher.publishEvent(new AuditEventListener.SecurityEvent(
                    "CRITICAL_OPERATION_FAILURE",
                    getCurrentUserId(),
                    Map.of(
                        "method", methodName,
                        "className", className,
                        "error", e.getMessage(),
                        "duration", duration
                    )
                ));
            }
            
            throw e;
        }
    }

    // ==================== MÉTHODES UTILITAIRES ====================

    /**
     * Configure le contexte d'audit pour les opérations de base de données
     */
    private void setDatabaseAuditContext() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            Long userId = null;
            String userEmail = "system";
            String userRole = "system";
            
            if (authentication != null && authentication.isAuthenticated() && 
                !"anonymousUser".equals(authentication.getPrincipal())) {
                
                if (authentication.getPrincipal() instanceof CustomUserPrincipal userPrincipal) {
                    userId = userPrincipal.getUserId();
                    userEmail = userPrincipal.getUsername();
                    userRole = String.join(",", userPrincipal.getRoleNames());
                }
            }

            // Get request information if available
            String clientIp = null;
            String userAgent = null;
            String sessionId = null;
            
            ServletRequestAttributes requestAttributes = 
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            
            if (requestAttributes != null) {
                HttpServletRequest request = requestAttributes.getRequest();
                clientIp = getClientIpAddress(request);
                userAgent = request.getHeader("User-Agent");
                sessionId = request.getSession(false) != null ? request.getSession().getId() : null;
            }

            databaseAuditService.setAuditContext(userId, userEmail, userRole, clientIp, userAgent, sessionId);
            
        } catch (Exception e) {
            log.warn("Failed to set audit context: {}", e.getMessage());
        }
    }

    /**
     * Récupère l'adresse IP du client
     */
    private String getClientIpAddress(HttpServletRequest request) {
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

    /**
     * Récupère l'ID utilisateur actuel
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
     * Extrait l'ID d'entité des arguments de méthode
     */
    private Long extractEntityId(Object[] args) {
        if (args != null && args.length > 0) {
            for (Object arg : args) {
                if (arg instanceof Long) {
                    return (Long) arg;
                }
                // Essayer d'extraire l'ID depuis un objet avec une méthode getId()
                try {
                    if (arg != null && arg.getClass().getMethod("getId") != null) {
                        Object id = arg.getClass().getMethod("getId").invoke(arg);
                        if (id instanceof Long) {
                            return (Long) id;
                        }
                    }
                } catch (Exception e) {
                    // Ignorer si pas de méthode getId()
                }
            }
        }
        return 0L; // ID par défaut si non trouvé
    }

    /**
     * Détermine l'action d'audit basée sur le nom de la méthode
     */
    private AuditLog.AuditAction getActionFromMethodName(String methodName) {
        String lowerMethodName = methodName.toLowerCase();
        
        if (lowerMethodName.contains("create") || lowerMethodName.contains("save") || 
            lowerMethodName.contains("add") || lowerMethodName.contains("insert")) {
            return AuditLog.AuditAction.CREATE;
        } else if (lowerMethodName.contains("update") || lowerMethodName.contains("modify") || 
                   lowerMethodName.contains("edit") || lowerMethodName.contains("change")) {
            return AuditLog.AuditAction.UPDATE;
        } else if (lowerMethodName.contains("delete") || lowerMethodName.contains("remove")) {
            return AuditLog.AuditAction.DELETE;
        } else if (lowerMethodName.contains("calculate") || lowerMethodName.contains("compute")) {
            return AuditLog.AuditAction.CALCULATE;
        } else if (lowerMethodName.contains("generate") || lowerMethodName.contains("create")) {
            return AuditLog.AuditAction.GENERATE;
        } else if (lowerMethodName.contains("export")) {
            return AuditLog.AuditAction.EXPORT;
        } else if (lowerMethodName.contains("import")) {
            return AuditLog.AuditAction.IMPORT;
        } else {
            return AuditLog.AuditAction.VIEW;
        }
    }

    /**
     * Détermine si une erreur est critique
     */
    private boolean isCriticalError(Exception e) {
        String errorMessage = e.getMessage();
        if (errorMessage == null) {
            return false;
        }
        
        String lowerErrorMessage = errorMessage.toLowerCase();
        return lowerErrorMessage.contains("security") ||
               lowerErrorMessage.contains("unauthorized") ||
               lowerErrorMessage.contains("access denied") ||
               lowerErrorMessage.contains("permission") ||
               lowerErrorMessage.contains("authentication") ||
               lowerErrorMessage.contains("sql injection") ||
               lowerErrorMessage.contains("xss") ||
               lowerErrorMessage.contains("csrf");
    }
}