package com.bantuops.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/docs")
@Slf4j
@Tag(name = "Documentation API", description = "Endpoints pour la documentation et métadonnées de l'API")
public class ApiDocumentationController {

    @Value("${spring.application.name:BantuOps Backend API}")
    private String applicationName;
    
    @Value("${spring.application.version:1.0.0}")
    private String applicationVersion;
    
    @Value("${spring.profiles.active:development}")
    private String activeProfile;

    @GetMapping("/info")
    @Operation(summary = "Informations sur l'API")
    @ApiResponse(responseCode = "200", description = "Informations récupérées avec succès")
    public ResponseEntity<Map<String, Object>> getApiInfo() {
        Map<String, Object> apiInfo = new HashMap<>();
        apiInfo.put("name", applicationName);
        apiInfo.put("version", applicationVersion);
        apiInfo.put("description", "API REST sécurisée pour la gestion des PME sénégalaises");
        apiInfo.put("environment", activeProfile);
        apiInfo.put("documentation", "/swagger-ui/index.html");
        return ResponseEntity.ok(apiInfo);
    }

    @GetMapping("/health")
    @Operation(summary = "État de santé de l'API")
    @ApiResponse(responseCode = "200", description = "API en bonne santé")
    public ResponseEntity<Map<String, Object>> getHealthStatus() {
        Map<String, Object> healthStatus = Map.of(
            "status", "UP",
            "timestamp", LocalDateTime.now(),
            "version", applicationVersion
        );
        return ResponseEntity.ok(healthStatus);
    }

    @GetMapping("/limits")
    @Operation(summary = "Limites de l'API")
    @ApiResponse(responseCode = "200", description = "Limites récupérées avec succès")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getApiLimits() {
        Map<String, Object> limits = new HashMap<>();
        limits.put("requestsPerMinute", 100);
        limits.put("requestsPerHour", 5000);
        limits.put("maxEmployeesPerCompany", 1000);
        limits.put("maxFileSize", "10MB");
        return ResponseEntity.ok(limits);
    }

    @GetMapping("/error-codes")
    @Operation(summary = "Codes d'erreur")
    @ApiResponse(responseCode = "200", description = "Codes d'erreur récupérés avec succès")
    public ResponseEntity<Map<String, Object>> getErrorCodes() {
        Map<String, Object> errorCodes = new HashMap<>();
        errorCodes.put("VALIDATION_ERROR", "Données d'entrée invalides");
        errorCodes.put("AUTHENTICATION_FAILED", "Échec de l'authentification");
        errorCodes.put("ACCESS_DENIED", "Accès refusé");
        errorCodes.put("RESOURCE_NOT_FOUND", "Ressource non trouvée");
        return ResponseEntity.ok(errorCodes);
    }

    @GetMapping("/validation-schemas")
    @Operation(summary = "Schémas de validation")
    @ApiResponse(responseCode = "200", description = "Schémas récupérés avec succès")
    public ResponseEntity<Map<String, Object>> getValidationSchemas() {
        Map<String, Object> schemas = new HashMap<>();
        schemas.put("senegalPhonePattern", "^\\+221[0-9]{9}$");
        schemas.put("senegalTaxNumberPattern", "\\d{13}");
        schemas.put("employeeNumberPattern", "^EMP-\\d{3,6}$");
        schemas.put("invoiceNumberPattern", "^[A-Z]{3,4}-\\d{4}-\\d{3}$");
        return ResponseEntity.ok(schemas);
    }
}
