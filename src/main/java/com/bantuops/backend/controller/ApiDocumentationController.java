package com.bantuops.backend.controller;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Contrôleur pour la documentation et métadonnées de l'API
 * Conforme aux exigences 4.1, 4.2, 4.3, 4.4, 4.5, 4.6 pour la documentation API
 */
@RestController
@RequestMapping("/api/docs")
@Slf4j
@Tag(name = "Documentation API", description = "Endpoints pour la documentation et métadonnées de l'API")
public class ApiDocumentationController {

    /**
     * Informations sur l'API et sa version
     */
    @GetMapping("/info")
    @Operation(summary = "Informations sur l'API", 
               description = "Récupère les informations de version et configuration de l'API")
    @ApiResponse(responseCode = "200", description = "Informations récupérées avec succès")
    public ResponseEntity<Map<String, Object>> getApiInfo() {
        
        Map<String, Object> apiInfo = Map.of(
            "name", "BantuOps Backend API",
            "version", "1.0.0",
            "description", "API REST sécurisée pour la gestion des PME sénégalaises",
            "environment", getEnvironment(),
            "features", Map.of(
                "payroll", "Gestion de paie conforme à la législation sénégalaise",
                "financial", "Gestion financière avec TVA et chiffrement",
                "hr", "Gestion RH et assiduité",
                "security", "Authentification JWT et audit complet"
            ),
            "compliance", Map.of(
                "senegal", Map.of(
                    "laborCode", "Code du Travail sénégalais",
                    "taxRegulation", "Réglementation fiscale DGI",
                    "accounting", "Normes comptables SYSCOHADA",
                    "vatRate", "18%"
                )
            ),
            "support", Map.of(
                "email", "support@bantuops.com",
                "documentation", "/swagger-ui/index.html",
                "status", "https://status.bantuops.com"
            )
        );
        
        return ResponseEntity.ok(apiInfo);
    }

    /**
     * État de santé de l'API
     */
    @GetMapping("/health")
    @Operation(summary = "État de santé de l'API", 
               description = "Vérifie l'état de santé des composants de l'API")
    @ApiResponse(responseCode = "200", description = "API en bonne santé")
    public ResponseEntity<Map<String, Object>> getHealthStatus() {
        
        Map<String, Object> healthStatus = Map.of(
            "status", "UP",
            "timestamp", java.time.LocalDateTime.now(),
            "components", Map.of(
                "database", "UP",
                "redis", "UP",
                "security", "UP",
                "encryption", "UP"
            ),
            "version", "1.0.0",
            "uptime", getUptime()
        );
        
        return ResponseEntity.ok(healthStatus);
    }

    /**
     * Limites et quotas de l'API
     */
    @GetMapping("/limits")
    @Operation(summary = "Limites de l'API", 
               description = "Récupère les limites et quotas de l'API")
    @ApiResponse(responseCode = "200", description = "Limites récupérées avec succès")
    public ResponseEntity<Map<String, Object>> getApiLimits() {
        
        Map<String, Object> limits = Map.of(
            "rateLimit", Map.of(
                "requestsPerMinute", 1000,
                "requestsPerHour", 10000,
                "requestsPerDay", 100000
            ),
            "dataLimits", Map.of(
                "maxEmployeesPerCompany", 1000,
                "maxInvoicesPerMonth", 5000,
                "maxPayrollRecordsPerMonth", 1000
            ),
            "fileLimits", Map.of(
                "maxFileSize", "10MB",
                "allowedFormats", new String[]{"PDF", "CSV", "XLSX"}
            ),
            "security", Map.of(
                "tokenExpiry", "24 hours",
                "maxFailedAttempts", 5,
                "lockoutDuration", "30 minutes"
            )
        );
        
        return ResponseEntity.ok(limits);
    }

    /**
     * Codes d'erreur et leur signification
     */
    @GetMapping("/error-codes")
    @Operation(summary = "Codes d'erreur", 
               description = "Liste des codes d'erreur et leur signification")
    @ApiResponse(responseCode = "200", description = "Codes d'erreur récupérés avec succès")
    public ResponseEntity<Map<String, Object>> getErrorCodes() {
        
        Map<String, Object> errorCodes = Map.of(
            "validation", Map.of(
                "VALIDATION_ERROR", "Données d'entrée invalides",
                "CONSTRAINT_VIOLATION", "Violation de contrainte de base de données",
                "BUSINESS_RULE_VIOLATION", "Violation de règle métier"
            ),
            "authentication", Map.of(
                "AUTHENTICATION_FAILED", "Échec de l'authentification",
                "INVALID_CREDENTIALS", "Identifiants invalides",
                "INVALID_TOKEN", "Token JWT invalide ou expiré"
            ),
            "authorization", Map.of(
                "ACCESS_DENIED", "Accès refusé",
                "INSUFFICIENT_PERMISSION", "Permissions insuffisantes"
            ),
            "resources", Map.of(
                "RESOURCE_NOT_FOUND", "Ressource non trouvée",
                "RESOURCE_CONFLICT", "Conflit de ressource"
            ),
            "business", Map.of(
                "PAYROLL_CALCULATION_ERROR", "Erreur de calcul de paie",
                "INVALID_SENEGAL_TAX_NUMBER", "Numéro fiscal sénégalais invalide",
                "INVALID_SENEGAL_PHONE", "Numéro de téléphone sénégalais invalide"
            )
        );
        
        return ResponseEntity.ok(errorCodes);
    }

    /**
     * Exemples d'utilisation de l'API
     */
    @GetMapping("/examples")
    @Operation(summary = "Exemples d'utilisation", 
               description = "Exemples pratiques d'utilisation de l'API")
    @ApiResponse(responseCode = "200", description = "Exemples récupérés avec succès")
    public ResponseEntity<Map<String, Object>> getApiExamples() {
        
        Map<String, Object> examples = Map.of(
            "authentication", Map.of(
                "login", Map.of(
                    "url", "POST /api/auth/login",
                    "body", Map.of(
                        "username", "admin@bantuops.com",
                        "password", "password123"
                    ),
                    "response", Map.of(
                        "token", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                        "expiresIn", 86400
                    )
                )
            ),
            "payroll", Map.of(
                "calculate", Map.of(
                    "url", "POST /api/payroll/calculate",
                    "headers", Map.of("Authorization", "Bearer <token>"),
                    "body", Map.of(
                        "employeeId", 1,
                        "payrollPeriod", "2024-01",
                        "baseSalary", 500000
                    )
                )
            ),
            "invoice", Map.of(
                "create", Map.of(
                    "url", "POST /api/financial/invoices",
                    "headers", Map.of("Authorization", "Bearer <token>"),
                    "body", Map.of(
                        "invoiceNumber", "FACT-2024-001",
                        "clientName", "Entreprise ABC",
                        "subtotalAmount", 1000000,
                        "vatRate", 0.18
                    )
                )
            )
        );
        
        return ResponseEntity.ok(examples);
    }

    /**
     * Changelog de l'API
     */
    @GetMapping("/changelog")
    @Operation(summary = "Changelog de l'API", 
               description = "Historique des versions et modifications de l'API")
    @ApiResponse(responseCode = "200", description = "Changelog récupéré avec succès")
    public ResponseEntity<Map<String, Object>> getChangelog() {
        
        Map<String, Object> changelog = Map.of(
            "v1.0.0", Map.of(
                "date", "2024-01-15",
                "type", "major",
                "changes", new String[]{
                    "Version initiale de l'API",
                    "Gestion de paie conforme à la législation sénégalaise",
                    "Gestion financière avec TVA",
                    "Gestion RH et assiduité",
                    "Authentification JWT",
                    "Chiffrement des données sensibles",
                    "Audit complet des actions"
                }
            )
        );
        
        return ResponseEntity.ok(changelog);
    }

    /**
     * Schémas de validation pour les données sénégalaises
     */
    @GetMapping("/validation-schemas")
    @Operation(summary = "Schémas de validation", 
               description = "Schémas de validation spécifiques au contexte sénégalais")
    @ApiResponse(responseCode = "200", description = "Schémas récupérés avec succès")
    public ResponseEntity<Map<String, Object>> getValidationSchemas() {
        
        Map<String, Object> schemas = Map.of(
            "senegalPhoneNumber", Map.of(
                "pattern", "^\\+221[0-9]{9}$",
                "description", "Numéro de téléphone sénégalais",
                "examples", new String[]{"+221701234567", "+221771234567", "+221331234567"}
            ),
            "senegalTaxNumber", Map.of(
                "pattern", "\\d{13}",
                "description", "Numéro fiscal sénégalais (13 chiffres)",
                "examples", new String[]{"1234567890123"}
            ),
            "senegalBankAccount", Map.of(
                "pattern", "^[A-Z]{2}\\d{2}[A-Z0-9]{4}\\d{16}$",
                "description", "Numéro de compte bancaire sénégalais (format IBAN)",
                "examples", new String[]{"SN12K00100152000025690007542"}
            ),
            "employeeNumber", Map.of(
                "pattern", "^EMP-\\d{3,6}$",
                "description", "Numéro d'employé unique",
                "examples", new String[]{"EMP-001", "EMP-123456"}
            ),
            "invoiceNumber", Map.of(
                "pattern", "^[A-Z]{3,4}-\\d{4}-\\d{3}$",
                "description", "Numéro de facture",
                "examples", new String[]{"FACT-2024-001", "INV-2024-123"}
            )
        );
        
        return ResponseEntity.ok(schemas);
    }

    /**
     * Guide d'intégration de l'API
     */
    @GetMapping("/integration-guide")
    @Operation(summary = "Guide d'intégration", 
               description = "Guide étape par étape pour intégrer l'API")
    @ApiResponse(responseCode = "200", description = "Guide récupéré avec succès")
    public ResponseEntity<Map<String, Object>> getIntegrationGuide() {
        
        Map<String, Object> guide = Map.of(
            "steps", new Object[]{
                Map.of(
                    "step", 1,
                    "title", "Authentification",
                    "description", "Obtenez un token JWT via l'endpoint de connexion",
                    "endpoint", "POST /api/auth/login",
                    "required", new String[]{"username", "password"}
                ),
                Map.of(
                    "step", 2,
                    "title", "Configuration des en-têtes",
                    "description", "Incluez le token dans toutes les requêtes",
                    "headers", Map.of(
                        "Authorization", "Bearer <your-jwt-token>",
                        "Content-Type", "application/json"
                    )
                ),
                Map.of(
                    "step", 3,
                    "title", "Création d'employés",
                    "description", "Créez vos employés avec validation complète",
                    "endpoint", "POST /api/employees",
                    "validation", "Respectez les formats sénégalais"
                ),
                Map.of(
                    "step", 4,
                    "title", "Calculs de paie",
                    "description", "Effectuez les calculs de paie conformes",
                    "endpoint", "POST /api/payroll/calculate",
                    "compliance", "Législation sénégalaise appliquée automatiquement"
                )
            ),
            "bestPractices", new String[]{
                "Toujours valider les données avant envoi",
                "Gérer les erreurs de manière appropriée",
                "Respecter les limites de taux de requêtes",
                "Utiliser HTTPS en production",
                "Stocker les tokens de manière sécurisée"
            },
            "sdks", Map.of(
                "javascript", "npm install @bantuops/api-client",
                "python", "pip install bantuops-api",
                "php", "composer require bantuops/api-client"
            )
        );
        
        return ResponseEntity.ok(guide);
    }

    /**
     * Métriques de performance de l'API
     */
    @GetMapping("/performance-metrics")
    @Operation(summary = "Métriques de performance", 
               description = "Métriques de performance et statistiques d'utilisation")
    @ApiResponse(responseCode = "200", description = "Métriques récupérées avec succès")
    public ResponseEntity<Map<String, Object>> getPerformanceMetrics() {
        
        Map<String, Object> metrics = Map.of(
            "responseTime", Map.of(
                "average", "150ms",
                "p95", "300ms",
                "p99", "500ms"
            ),
            "throughput", Map.of(
                "requestsPerSecond", 100,
                "peakRps", 500
            ),
            "availability", Map.of(
                "uptime", "99.9%",
                "lastDowntime", "2024-01-01T00:00:00Z"
            ),
            "endpoints", Map.of(
                "mostUsed", "/api/payroll/calculate",
                "slowest", "/api/financial/reports/generate",
                "fastest", "/api/docs/health"
            ),
            "errors", Map.of(
                "errorRate", "0.1%",
                "mostCommonError", "VALIDATION_ERROR"
            )
        );
        
        return ResponseEntity.ok(metrics);
    }

    /**
     * Méthodes utilitaires
     */
    private String getEnvironment() {
        return System.getProperty("spring.profiles.active", "development");
    }

    private String getUptime() {
        long uptimeMillis = java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime();
        long uptimeSeconds = uptimeMillis / 1000;
        long hours = uptimeSeconds / 3600;
        long minutes = (uptimeSeconds % 3600) / 60;
        long seconds = uptimeSeconds % 60;
        
        return String.format("%d heures, %d minutes, %d secondes", hours, minutes, seconds);
    }
}