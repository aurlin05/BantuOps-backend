package com.bantuops.backend.exception;

import com.bantuops.backend.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Gestionnaire spécialisé pour les exceptions de sécurité
 * Conforme aux exigences 4.2, 4.3, 4.4, 3.1, 3.2, 3.3 pour les violations de sécurité
 */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class SecurityExceptionHandler {

    private final AuditService auditService;

    /**
     * Gestion des violations d'accès avec audit de sécurité
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, WebRequest request, HttpServletRequest httpRequest) {
        
        String username = getCurrentUsername();
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        String requestedResource = request.getDescription(false);
        
        log.warn("Tentative d'accès non autorisé - Utilisateur: {}, IP: {}, Ressource: {}", 
                username, ipAddress, requestedResource);
        
        // Audit de sécurité
        auditService.logSecurityViolation(
            "ACCESS_DENIED",
            username,
            ipAddress,
            userAgent,
            requestedResource,
            ex.getMessage()
        );
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .code("ACCESS_DENIED")
            .message("Accès non autorisé à cette ressource")
            .timestamp(LocalDateTime.now())
            .path(requestedResource)
            .suggestion("Veuillez contacter votre administrateur pour obtenir les permissions nécessaires")
            .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    /**
     * Gestion des erreurs d'authentification avec audit
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex, WebRequest request, HttpServletRequest httpRequest) {
        
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        String requestedResource = request.getDescription(false);
        
        log.warn("Échec d'authentification - IP: {}, Ressource: {}, Erreur: {}", 
                ipAddress, requestedResource, ex.getMessage());
        
        // Audit de sécurité
        auditService.logSecurityViolation(
            "AUTHENTICATION_FAILED",
            "anonymous",
            ipAddress,
            userAgent,
            requestedResource,
            ex.getMessage()
        );
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .code("AUTHENTICATION_FAILED")
            .message("Échec de l'authentification")
            .timestamp(LocalDateTime.now())
            .path(requestedResource)
            .suggestion("Veuillez vérifier vos identifiants et réessayer")
            .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    /**
     * Gestion des credentials invalides avec compteur de tentatives
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex, WebRequest request, HttpServletRequest httpRequest) {
        
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        String requestedResource = request.getDescription(false);
        
        log.warn("Credentials invalides - IP: {}, User-Agent: {}", ipAddress, userAgent);
        
        // Audit de sécurité avec comptage des tentatives
        auditService.logFailedLoginAttempt(ipAddress, userAgent, ex.getMessage());
        
        // Vérifier si l'IP doit être bloquée (trop de tentatives)
        boolean shouldBlockIp = auditService.shouldBlockIpAddress(ipAddress);
        
        ErrorResponse.ErrorResponseBuilder responseBuilder = ErrorResponse.builder()
            .code("INVALID_CREDENTIALS")
            .message("Identifiants invalides")
            .timestamp(LocalDateTime.now())
            .path(requestedResource);
        
        if (shouldBlockIp) {
            responseBuilder
                .details(Map.of("blocked", true, "reason", "Trop de tentatives de connexion"))
                .suggestion("Votre adresse IP a été temporairement bloquée. Veuillez réessayer plus tard");
            
            log.warn("Adresse IP bloquée pour tentatives multiples: {}", ipAddress);
        } else {
            responseBuilder.suggestion("Veuillez vérifier votre nom d'utilisateur et mot de passe");
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(responseBuilder.build());
    }

    /**
     * Gestion des authentifications insuffisantes
     */
    @ExceptionHandler(InsufficientAuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientAuthentication(
            InsufficientAuthenticationException ex, WebRequest request, HttpServletRequest httpRequest) {
        
        String ipAddress = getClientIpAddress(httpRequest);
        String requestedResource = request.getDescription(false);
        
        log.warn("Authentification insuffisante - IP: {}, Ressource: {}", ipAddress, requestedResource);
        
        // Audit de sécurité
        auditService.logSecurityViolation(
            "INSUFFICIENT_AUTHENTICATION",
            getCurrentUsername(),
            ipAddress,
            httpRequest.getHeader("User-Agent"),
            requestedResource,
            ex.getMessage()
        );
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .code("INSUFFICIENT_AUTHENTICATION")
            .message("Authentification requise pour accéder à cette ressource")
            .timestamp(LocalDateTime.now())
            .path(requestedResource)
            .suggestion("Veuillez vous connecter avec un compte valide")
            .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    /**
     * Gestion des tokens JWT invalides
     */
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(
            InvalidTokenException ex, WebRequest request, HttpServletRequest httpRequest) {
        
        String ipAddress = getClientIpAddress(httpRequest);
        String requestedResource = request.getDescription(false);
        
        log.warn("Token JWT invalide - IP: {}, Ressource: {}", ipAddress, requestedResource);
        
        // Audit de sécurité
        auditService.logSecurityViolation(
            "INVALID_TOKEN",
            getCurrentUsername(),
            ipAddress,
            httpRequest.getHeader("User-Agent"),
            requestedResource,
            ex.getMessage()
        );
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .code("INVALID_TOKEN")
            .message("Token d'authentification invalide ou expiré")
            .timestamp(LocalDateTime.now())
            .path(requestedResource)
            .suggestion("Veuillez vous reconnecter pour obtenir un nouveau token")
            .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    /**
     * Gestion des violations de permissions spécifiques
     */
    @ExceptionHandler(InsufficientPermissionException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientPermission(
            InsufficientPermissionException ex, WebRequest request, HttpServletRequest httpRequest) {
        
        String username = getCurrentUsername();
        String ipAddress = getClientIpAddress(httpRequest);
        String requestedResource = request.getDescription(false);
        
        log.warn("Permission insuffisante - Utilisateur: {}, IP: {}, Ressource: {}", 
                username, ipAddress, requestedResource);
        
        // Audit de sécurité détaillé
        auditService.logPermissionViolation(
            username,
            ipAddress,
            requestedResource,
            ex.getRequiredPermission(),
            ex.getMessage()
        );
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .code("INSUFFICIENT_PERMISSION")
            .message(ex.getMessage())
            .details(Map.of(
                "requiredPermission", ex.getRequiredPermission(),
                "resource", ex.getResource()
            ))
            .timestamp(LocalDateTime.now())
            .path(requestedResource)
            .suggestion("Contactez votre administrateur pour obtenir les permissions nécessaires")
            .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    /**
     * Récupère le nom d'utilisateur actuel
     */
    private String getCurrentUsername() {
        try {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            return authentication != null ? authentication.getName() : "anonymous";
        } catch (Exception e) {
            return "unknown";
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
}