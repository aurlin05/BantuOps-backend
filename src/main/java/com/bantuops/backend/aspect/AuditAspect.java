package com.bantuops.backend.aspect;

import com.bantuops.backend.config.DatabaseConfig;
import com.bantuops.backend.security.CustomUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final DatabaseConfig.DatabaseAuditService auditService;

    @Around("@annotation(org.springframework.transaction.annotation.Transactional) || " +
            "execution(* com.bantuops.backend.repository.*.*(..))")
    public Object setAuditContext(ProceedingJoinPoint joinPoint) throws Throwable {
        // Set audit context before database operations
        setDatabaseAuditContext();
        
        try {
            return joinPoint.proceed();
        } finally {
            // Clear audit context after database operations
            auditService.clearAuditContext();
        }
    }

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

            auditService.setAuditContext(userId, userEmail, userRole, clientIp, userAgent, sessionId);
            
        } catch (Exception e) {
            log.warn("Failed to set audit context: {}", e.getMessage());
        }
    }

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
}