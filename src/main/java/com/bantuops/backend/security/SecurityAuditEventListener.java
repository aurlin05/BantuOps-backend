package com.bantuops.backend.security;

import com.bantuops.backend.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.listener.AuditApplicationEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.*;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Écouteur d'événements de sécurité pour l'audit
 * Conforme aux exigences 7.4, 7.5, 7.6, 6.1, 6.2 pour l'audit de sécurité
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityAuditEventListener {

    private final AuditService auditService;

    /**
     * Écoute les événements d'audit Spring Boot Actuator
     */
    @EventListener
    public void onAuditEvent(AuditApplicationEvent event) {
        AuditEvent auditEvent = event.getAuditEvent();
        
        log.debug("Événement d'audit reçu: Type={}, Principal={}", 
                auditEvent.getType(), auditEvent.getPrincipal());
        
        try {
            // Enregistrer l'événement d'audit
            auditService.logSecurityEvent(
                auditEvent.getType(),
                auditEvent.getPrincipal(),
                auditEvent.getData(),
                LocalDateTime.now()
            );
        } catch (Exception e) {
            log.error("Erreur lors de l'enregistrement de l'événement d'audit: {}", e.getMessage());
        }
    }

    /**
     * Écoute les événements de connexion réussie
     */
    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        Authentication authentication = event.getAuthentication();
        String username = authentication.getName();
        String ipAddress = getIpAddress(authentication);
        
        log.info("Connexion réussie - Utilisateur: {}, IP: {}", username, ipAddress);
        
        try {
            Map<String, Object> details = new HashMap<>();
            details.put("username", username);
            details.put("ipAddress", ipAddress);
            details.put("authorities", authentication.getAuthorities().toString());
            details.put("timestamp", LocalDateTime.now());
            
            auditService.logSuccessfulLogin(username, ipAddress, details);
            
        } catch (Exception e) {
            log.error("Erreur lors de l'audit de connexion réussie: {}", e.getMessage());
        }
    }

    /**
     * Écoute les événements d'échec d'authentification
     */
    @EventListener
    public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        String username = event.getAuthentication().getName();
        String ipAddress = getIpAddress(event.getAuthentication());
        String reason = event.getException().getMessage();
        
        log.warn("Échec d'authentification - Utilisateur: {}, IP: {}, Raison: {}", 
                username, ipAddress, reason);
        
        try {
            Map<String, Object> details = new HashMap<>();
            details.put("username", username);
            details.put("ipAddress", ipAddress);
            details.put("reason", reason);
            details.put("exceptionType", event.getException().getClass().getSimpleName());
            details.put("timestamp", LocalDateTime.now());
            
            auditService.logFailedLogin(username, ipAddress, reason, details);
            
        } catch (Exception e) {
            log.error("Erreur lors de l'audit d'échec d'authentification: {}", e.getMessage());
        }
    }

    /**
     * Écoute les événements de credentials incorrects
     */
    @EventListener
    public void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        String username = event.getAuthentication().getName();
        String ipAddress = getIpAddress(event.getAuthentication());
        
        log.warn("Credentials incorrects - Utilisateur: {}, IP: {}", username, ipAddress);
        
        try {
            Map<String, Object> details = new HashMap<>();
            details.put("username", username);
            details.put("ipAddress", ipAddress);
            details.put("attemptType", "BAD_CREDENTIALS");
            details.put("timestamp", LocalDateTime.now());
            
            auditService.logBadCredentialsAttempt(username, ipAddress, details);
            
            // Vérifier si l'IP doit être bloquée
            if (auditService.shouldBlockIpAddress(ipAddress)) {
                log.warn("Adresse IP bloquée pour tentatives multiples: {}", ipAddress);
                auditService.blockIpAddress(ipAddress, "Tentatives de connexion multiples avec credentials incorrects");
            }
            
        } catch (Exception e) {
            log.error("Erreur lors de l'audit de credentials incorrects: {}", e.getMessage());
        }
    }

    /**
     * Écoute les événements de compte désactivé
     */
    @EventListener
    public void onAccountDisabled(AuthenticationFailureDisabledEvent event) {
        String username = event.getAuthentication().getName();
        String ipAddress = getIpAddress(event.getAuthentication());
        
        log.warn("Tentative de connexion sur compte désactivé - Utilisateur: {}, IP: {}", 
                username, ipAddress);
        
        try {
            Map<String, Object> details = new HashMap<>();
            details.put("username", username);
            details.put("ipAddress", ipAddress);
            details.put("attemptType", "DISABLED_ACCOUNT");
            details.put("timestamp", LocalDateTime.now());
            
            auditService.logDisabledAccountAttempt(username, ipAddress, details);
            
        } catch (Exception e) {
            log.error("Erreur lors de l'audit de compte désactivé: {}", e.getMessage());
        }
    }

    /**
     * Écoute les événements de compte expiré
     */
    @EventListener
    public void onAccountExpired(AuthenticationFailureExpiredEvent event) {
        String username = event.getAuthentication().getName();
        String ipAddress = getIpAddress(event.getAuthentication());
        
        log.warn("Tentative de connexion sur compte expiré - Utilisateur: {}, IP: {}", 
                username, ipAddress);
        
        try {
            Map<String, Object> details = new HashMap<>();
            details.put("username", username);
            details.put("ipAddress", ipAddress);
            details.put("attemptType", "EXPIRED_ACCOUNT");
            details.put("timestamp", LocalDateTime.now());
            
            auditService.logExpiredAccountAttempt(username, ipAddress, details);
            
        } catch (Exception e) {
            log.error("Erreur lors de l'audit de compte expiré: {}", e.getMessage());
        }
    }

    /**
     * Écoute les événements de compte verrouillé
     */
    @EventListener
    public void onAccountLocked(AuthenticationFailureLockedEvent event) {
        String username = event.getAuthentication().getName();
        String ipAddress = getIpAddress(event.getAuthentication());
        
        log.warn("Tentative de connexion sur compte verrouillé - Utilisateur: {}, IP: {}", 
                username, ipAddress);
        
        try {
            Map<String, Object> details = new HashMap<>();
            details.put("username", username);
            details.put("ipAddress", ipAddress);
            details.put("attemptType", "LOCKED_ACCOUNT");
            details.put("timestamp", LocalDateTime.now());
            
            auditService.logLockedAccountAttempt(username, ipAddress, details);
            
        } catch (Exception e) {
            log.error("Erreur lors de l'audit de compte verrouillé: {}", e.getMessage());
        }
    }

    /**
     * Écoute les événements d'autorisation refusée
     */
    @EventListener
    public void onAuthorizationDenied(AuthorizationDeniedEvent<?> event) {
        Authentication authentication = event.getAuthentication().get();
        String username = authentication != null ? authentication.getName() : "anonymous";
        String resource = event.getObject() != null ? event.getObject().toString() : "unknown";
        
        log.warn("Autorisation refusée - Utilisateur: {}, Ressource: {}", username, resource);
        
        try {
            Map<String, Object> details = new HashMap<>();
            details.put("username", username);
            details.put("resource", resource);
            details.put("authorities", authentication != null ? authentication.getAuthorities().toString() : "[]");
            details.put("timestamp", LocalDateTime.now());
            
            // TODO: Implement logAuthorizationDenied method in AuditService
            // auditService.logAuthorizationDenied(username, resource, details);
            
        } catch (Exception e) {
            log.error("Erreur lors de l'audit d'autorisation refusée: {}", e.getMessage());
        }
    }

    /**
     * Écoute les événements de déconnexion
     */
    @EventListener
    public void onLogout(org.springframework.security.authentication.event.LogoutSuccessEvent event) {
        Authentication authentication = event.getAuthentication();
        String username = authentication.getName();
        String ipAddress = getIpAddress(authentication);
        
        log.info("Déconnexion réussie - Utilisateur: {}, IP: {}", username, ipAddress);
        
        try {
            Map<String, Object> details = new HashMap<>();
            details.put("username", username);
            details.put("ipAddress", ipAddress);
            details.put("timestamp", LocalDateTime.now());
            
            auditService.logSuccessfulLogout(username, ipAddress, details);
            
        } catch (Exception e) {
            log.error("Erreur lors de l'audit de déconnexion: {}", e.getMessage());
        }
    }

    /**
     * Écoute les événements de changement de session
     */
    @EventListener
    public void onSessionCreated(org.springframework.security.web.session.HttpSessionCreatedEvent event) {
        String sessionId = event.getSession().getId();
        String username = getCurrentUsername();
        
        log.debug("Session créée - ID: {}, Utilisateur: {}", sessionId, username);
        
        try {
            Map<String, Object> details = new HashMap<>();
            details.put("sessionId", sessionId);
            details.put("username", username);
            details.put("maxInactiveInterval", event.getSession().getMaxInactiveInterval());
            details.put("timestamp", LocalDateTime.now());
            
            auditService.logSessionCreated(sessionId, username, details);
            
        } catch (Exception e) {
            log.error("Erreur lors de l'audit de création de session: {}", e.getMessage());
        }
    }

    /**
     * Récupère l'adresse IP depuis les détails d'authentification
     */
    private String getIpAddress(Authentication authentication) {
        if (authentication != null && authentication.getDetails() instanceof WebAuthenticationDetails) {
            WebAuthenticationDetails details = (WebAuthenticationDetails) authentication.getDetails();
            return details.getRemoteAddress();
        }
        return "unknown";
    }

    /**
     * Récupère le nom d'utilisateur actuel
     */
    private String getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            return authentication != null ? authentication.getName() : "anonymous";
        } catch (Exception e) {
            return "unknown";
        }
    }
}