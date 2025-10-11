package com.bantuops.backend.controller;

import com.bantuops.backend.dto.SecurityAlert;
import com.bantuops.backend.service.SecurityAlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST pour la gestion des alertes de sécurité
 * Accessible uniquement aux administrateurs de sécurité
 */
@RestController
@RequestMapping("/api/security/alerts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Security Alerts", description = "Gestion des alertes de sécurité")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('SECURITY_ADMIN') or hasRole('ADMIN')")
public class SecurityAlertController {

    private final SecurityAlertService securityAlertService;

    /**
     * Récupère les alertes de sécurité récentes
     */
    @GetMapping("/recent")
    @Operation(summary = "Récupérer les alertes récentes", 
               description = "Récupère les alertes de sécurité les plus récentes")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Alertes récupérées avec succès"),
        @ApiResponse(responseCode = "403", description = "Accès non autorisé")
    })
    public ResponseEntity<List<SecurityAlert>> getRecentAlerts(
            @Parameter(description = "Nombre maximum d'alertes à retourner")
            @RequestParam(defaultValue = "50") int limit) {
        
        log.info("Récupération des {} alertes récentes", limit);
        List<SecurityAlert> alerts = securityAlertService.getRecentSecurityAlerts(limit);
        return ResponseEntity.ok(alerts);
    }

    /**
     * Récupère les alertes de sécurité pour un utilisateur spécifique
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "Récupérer les alertes par utilisateur", 
               description = "Récupère les alertes de sécurité pour un utilisateur donné")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Alertes utilisateur récupérées"),
        @ApiResponse(responseCode = "403", description = "Accès non autorisé"),
        @ApiResponse(responseCode = "404", description = "Utilisateur non trouvé")
    })
    public ResponseEntity<List<SecurityAlert>> getUserAlerts(
            @Parameter(description = "ID de l'utilisateur")
            @PathVariable String userId,
            @Parameter(description = "Date de début (format: yyyy-MM-dd'T'HH:mm:ss)")
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since) {
        
        if (since == null) {
            since = LocalDateTime.now().minusDays(30); // Par défaut, 30 derniers jours
        }
        
        log.info("Récupération des alertes pour l'utilisateur {} depuis {}", userId, since);
        List<SecurityAlert> alerts = securityAlertService.getSecurityAlertsByUser(userId, since);
        return ResponseEntity.ok(alerts);
    }

    /**
     * Crée manuellement une alerte de sécurité
     */
    @PostMapping
    @Operation(summary = "Créer une alerte de sécurité", 
               description = "Crée manuellement une nouvelle alerte de sécurité")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Alerte créée avec succès"),
        @ApiResponse(responseCode = "400", description = "Données invalides"),
        @ApiResponse(responseCode = "403", description = "Accès non autorisé")
    })
    @PreAuthorize("hasRole('SECURITY_ADMIN')")
    public ResponseEntity<Void> createSecurityAlert(
            @Parameter(description = "Données de l'alerte de sécurité")
            @RequestBody CreateSecurityAlertRequest request) {
        
        log.warn("Création manuelle d'alerte de sécurité: {} pour l'utilisateur: {}", 
            request.getAlertType(), request.getUserId());
        
        securityAlertService.createSecurityAlert(
            request.getAlertType(),
            request.getUserId(),
            request.getDescription(),
            request.getMetadata()
        );
        
        return ResponseEntity.status(201).build();
    }

    /**
     * Résout une alerte de sécurité
     */
    @PutMapping("/{alertId}/resolve")
    @Operation(summary = "Résoudre une alerte", 
               description = "Marque une alerte de sécurité comme résolue")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Alerte résolue avec succès"),
        @ApiResponse(responseCode = "403", description = "Accès non autorisé"),
        @ApiResponse(responseCode = "404", description = "Alerte non trouvée")
    })
    public ResponseEntity<Void> resolveAlert(
            @Parameter(description = "ID de l'alerte")
            @PathVariable Long alertId,
            @Parameter(description = "Données de résolution")
            @RequestBody ResolveAlertRequest request) {
        
        log.info("Résolution de l'alerte {} par {}: {}", 
            alertId, request.getResolvedBy(), request.getResolution());
        
        securityAlertService.resolveSecurityAlert(
            alertId, 
            request.getResolvedBy(), 
            request.getResolution()
        );
        
        return ResponseEntity.ok().build();
    }

    /**
     * Déclenche une alerte pour tentative de connexion échouée
     */
    @PostMapping("/failed-login")
    @Operation(summary = "Alerte connexion échouée", 
               description = "Déclenche une alerte pour tentative de connexion échouée")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Alerte déclenchée"),
        @ApiResponse(responseCode = "403", description = "Accès non autorisé")
    })
    public ResponseEntity<Void> alertFailedLogin(
            @Parameter(description = "Données de la tentative de connexion")
            @RequestBody FailedLoginAlertRequest request) {
        
        log.warn("Alerte connexion échouée pour: {} depuis {}", 
            request.getUsername(), request.getIpAddress());
        
        securityAlertService.alertFailedLogin(
            request.getUsername(),
            request.getIpAddress(),
            request.getUserAgent()
        );
        
        return ResponseEntity.ok().build();
    }

    /**
     * Déclenche une alerte pour accès non autorisé
     */
    @PostMapping("/unauthorized-access")
    @Operation(summary = "Alerte accès non autorisé", 
               description = "Déclenche une alerte pour tentative d'accès non autorisé")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Alerte déclenchée"),
        @ApiResponse(responseCode = "403", description = "Accès non autorisé")
    })
    public ResponseEntity<Void> alertUnauthorizedAccess(
            @Parameter(description = "Données de la tentative d'accès")
            @RequestBody UnauthorizedAccessAlertRequest request) {
        
        log.warn("Alerte accès non autorisé: utilisateur {} vers {} (action: {})", 
            request.getUserId(), request.getResource(), request.getAction());
        
        securityAlertService.alertUnauthorizedAccess(
            request.getUserId(),
            request.getResource(),
            request.getAction()
        );
        
        return ResponseEntity.ok().build();
    }

    /**
     * Nettoie les alertes anciennes du cache
     */
    @PostMapping("/cleanup")
    @Operation(summary = "Nettoyer les alertes anciennes", 
               description = "Nettoie les alertes anciennes du cache")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Nettoyage effectué"),
        @ApiResponse(responseCode = "403", description = "Accès non autorisé")
    })
    @PreAuthorize("hasRole('SECURITY_ADMIN')")
    public ResponseEntity<Void> cleanupOldAlerts() {
        log.info("Nettoyage des alertes anciennes demandé");
        securityAlertService.cleanupOldAlerts();
        return ResponseEntity.ok().build();
    }

    // DTOs pour les requêtes

    public static class CreateSecurityAlertRequest {
        private SecurityAlert.AlertType alertType;
        private String userId;
        private String description;
        private Map<String, Object> metadata;

        // Getters et setters
        public SecurityAlert.AlertType getAlertType() { return alertType; }
        public void setAlertType(SecurityAlert.AlertType alertType) { this.alertType = alertType; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }

    public static class ResolveAlertRequest {
        private String resolvedBy;
        private String resolution;

        // Getters et setters
        public String getResolvedBy() { return resolvedBy; }
        public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
        public String getResolution() { return resolution; }
        public void setResolution(String resolution) { this.resolution = resolution; }
    }

    public static class FailedLoginAlertRequest {
        private String username;
        private String ipAddress;
        private String userAgent;

        // Getters et setters
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
        public String getUserAgent() { return userAgent; }
        public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    }

    public static class UnauthorizedAccessAlertRequest {
        private String userId;
        private String resource;
        private String action;

        // Getters et setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getResource() { return resource; }
        public void setResource(String resource) { this.resource = resource; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
    }
}