package com.bantuops.backend.aspect;

import com.bantuops.backend.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.actuate.metrics.MetricsEndpoint;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Aspect pour le monitoring des performances
 * Conforme aux exigences 7.4, 7.5, 7.6, 6.1, 6.2 pour le monitoring des performances
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class PerformanceMonitoringAspect {

    private final AuditService auditService;

    // Seuils de performance (en millisecondes)
    private static final long SLOW_QUERY_THRESHOLD = 1000; // 1 seconde
    private static final long VERY_SLOW_QUERY_THRESHOLD = 5000; // 5 secondes
    private static final long CRITICAL_SLOW_THRESHOLD = 10000; // 10 secondes

    /**
     * Monitore les performances des contrôleurs
     */
    @Around("execution(* com.bantuops.backend.controller..*(..))")
    public Object monitorControllerPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        return monitorMethodPerformance(joinPoint, "CONTROLLER");
    }

    /**
     * Monitore les performances des services
     */
    @Around("execution(* com.bantuops.backend.service..*(..))")
    public Object monitorServicePerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        return monitorMethodPerformance(joinPoint, "SERVICE");
    }

    /**
     * Monitore les performances des repositories
     */
    @Around("execution(* com.bantuops.backend.repository..*(..))")
    public Object monitorRepositoryPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        return monitorMethodPerformance(joinPoint, "REPOSITORY");
    }

    /**
     * Monitore les performances des méthodes de calcul de paie
     */
    @Around("execution(* com.bantuops.backend.service.PayrollCalculationService.*(..))")
    public Object monitorPayrollCalculationPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        return monitorMethodPerformance(joinPoint, "PAYROLL_CALCULATION");
    }

    /**
     * Monitore les performances des méthodes financières
     */
    @Around("execution(* com.bantuops.backend.service.FinancialService.*(..))")
    public Object monitorFinancialServicePerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        return monitorMethodPerformance(joinPoint, "FINANCIAL_SERVICE");
    }

    /**
     * Monitore les performances des méthodes de chiffrement
     */
    @Around("execution(* com.bantuops.backend.service.DataEncryptionService.*(..))")
    public Object monitorEncryptionPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        return monitorMethodPerformance(joinPoint, "ENCRYPTION");
    }

    /**
     * Méthode générique de monitoring des performances
     */
    private Object monitorMethodPerformance(ProceedingJoinPoint joinPoint, String category) throws Throwable {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String fullMethodName = className + "." + methodName;
        
        long startTime = System.currentTimeMillis();
        Object[] args = joinPoint.getArgs();
        
        // Informations sur la méthode
        PerformanceInfo performanceInfo = PerformanceInfo.builder()
            .category(category)
            .className(className)
            .methodName(methodName)
            .fullMethodName(fullMethodName)
            .argumentCount(args.length)
            .startTime(LocalDateTime.now())
            .build();

        Object result = null;
        Throwable exception = null;
        
        try {
            // Exécuter la méthode
            result = joinPoint.proceed();
            performanceInfo.setSuccess(true);
            
        } catch (Throwable e) {
            exception = e;
            performanceInfo.setSuccess(false);
            performanceInfo.setErrorMessage(e.getMessage());
            performanceInfo.setErrorType(e.getClass().getSimpleName());
            throw e;
            
        } finally {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            performanceInfo.setDuration(duration);
            performanceInfo.setEndTime(LocalDateTime.now());
            
            // Logger selon le niveau de performance
            logPerformance(performanceInfo, exception);
            
            // Enregistrer les métriques de performance
            recordPerformanceMetrics(performanceInfo);
            
            // Alerter si la performance est critique
            if (duration > CRITICAL_SLOW_THRESHOLD) {
                alertCriticalPerformance(performanceInfo);
            }
        }
        
        return result;
    }

    /**
     * Logger les informations de performance
     */
    private void logPerformance(PerformanceInfo info, Throwable exception) {
        String logMessage = String.format(
            "Performance [%s] %s - Durée: %dms, Succès: %s",
            info.getCategory(),
            info.getFullMethodName(),
            info.getDuration(),
            info.isSuccess()
        );
        
        if (exception != null) {
            log.error("{} - Erreur: {}", logMessage, exception.getMessage());
        } else if (info.getDuration() > CRITICAL_SLOW_THRESHOLD) {
            log.error("{} - CRITIQUE: Méthode très lente!", logMessage);
        } else if (info.getDuration() > VERY_SLOW_QUERY_THRESHOLD) {
            log.warn("{} - ATTENTION: Méthode lente", logMessage);
        } else if (info.getDuration() > SLOW_QUERY_THRESHOLD) {
            log.info("{} - INFO: Méthode un peu lente", logMessage);
        } else {
            log.debug(logMessage);
        }
    }

    /**
     * Enregistre les métriques de performance
     */
    private void recordPerformanceMetrics(PerformanceInfo info) {
        try {
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("category", info.getCategory());
            metrics.put("className", info.getClassName());
            metrics.put("methodName", info.getMethodName());
            metrics.put("duration", info.getDuration());
            metrics.put("success", info.isSuccess());
            metrics.put("argumentCount", info.getArgumentCount());
            metrics.put("timestamp", info.getStartTime());
            
            if (!info.isSuccess()) {
                metrics.put("errorType", info.getErrorType());
                metrics.put("errorMessage", info.getErrorMessage());
            }
            
            // Enregistrer les métriques de manière asynchrone
            auditService.logPerformanceMetrics(info.getFullMethodName(), metrics);
            
        } catch (Exception e) {
            log.error("Erreur lors de l'enregistrement des métriques de performance: {}", e.getMessage());
        }
    }

    /**
     * Alerte pour les performances critiques
     */
    private void alertCriticalPerformance(PerformanceInfo info) {
        try {
            Map<String, Object> alertData = new HashMap<>();
            alertData.put("severity", "CRITICAL");
            alertData.put("type", "SLOW_PERFORMANCE");
            alertData.put("method", info.getFullMethodName());
            alertData.put("duration", info.getDuration());
            alertData.put("threshold", CRITICAL_SLOW_THRESHOLD);
            alertData.put("category", info.getCategory());
            alertData.put("timestamp", info.getStartTime());
            
            // Enregistrer l'alerte
            auditService.logPerformanceAlert(
                "CRITICAL_SLOW_METHOD",
                String.format("Méthode critique lente détectée: %s (%dms)", 
                             info.getFullMethodName(), info.getDuration()),
                alertData
            );
            
            log.error("ALERTE PERFORMANCE CRITIQUE: {} a pris {}ms (seuil: {}ms)", 
                     info.getFullMethodName(), info.getDuration(), CRITICAL_SLOW_THRESHOLD);
            
        } catch (Exception e) {
            log.error("Erreur lors de l'envoi d'alerte de performance critique: {}", e.getMessage());
        }
    }

    /**
     * Classe pour les informations de performance
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PerformanceInfo {
        private String category;
        private String className;
        private String methodName;
        private String fullMethodName;
        private int argumentCount;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private long duration;
        private boolean success;
        private String errorMessage;
        private String errorType;
    }

    /**
     * Annotation personnalisée pour marquer les méthodes à monitorer spécialement
     */
    @java.lang.annotation.Target({java.lang.annotation.ElementType.METHOD})
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    public @interface MonitorPerformance {
        String value() default "";
        long threshold() default SLOW_QUERY_THRESHOLD;
        boolean alertOnSlow() default false;
    }

    /**
     * Monitore les méthodes annotées avec @MonitorPerformance
     */
    @Around("@annotation(monitorPerformance)")
    public Object monitorAnnotatedMethod(ProceedingJoinPoint joinPoint, MonitorPerformance monitorPerformance) throws Throwable {
        String customCategory = monitorPerformance.value().isEmpty() ? "CUSTOM" : monitorPerformance.value();
        long customThreshold = monitorPerformance.threshold();
        boolean alertOnSlow = monitorPerformance.alertOnSlow();
        
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String fullMethodName = className + "." + methodName;
        
        long startTime = System.currentTimeMillis();
        
        try {
            Object result = joinPoint.proceed();
            
            long duration = System.currentTimeMillis() - startTime;
            
            if (duration > customThreshold) {
                log.warn("Méthode personnalisée lente [{}] {}: {}ms (seuil: {}ms)", 
                        customCategory, fullMethodName, duration, customThreshold);
                
                if (alertOnSlow) {
                    Map<String, Object> alertData = new HashMap<>();
                    alertData.put("method", fullMethodName);
                    alertData.put("duration", duration);
                    alertData.put("threshold", customThreshold);
                    alertData.put("category", customCategory);
                    
                    auditService.logPerformanceAlert(
                        "CUSTOM_SLOW_METHOD",
                        String.format("Méthode personnalisée lente: %s (%dms)", fullMethodName, duration),
                        alertData
                    );
                }
            }
            
            return result;
            
        } catch (Throwable e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Erreur dans méthode personnalisée [{}] {} après {}ms: {}", 
                     customCategory, fullMethodName, duration, e.getMessage());
            throw e;
        }
    }
}