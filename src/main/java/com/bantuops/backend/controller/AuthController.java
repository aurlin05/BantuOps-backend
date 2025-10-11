package com.bantuops.backend.controller;

import com.bantuops.backend.dto.auth.AuthenticationRequest;
import com.bantuops.backend.dto.auth.AuthenticationResponse;
import com.bantuops.backend.dto.auth.TokenResponse;
import com.bantuops.backend.security.JwtAuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;

/**
 * Contrôleur REST pour l'authentification et la gestion des sessions
 * Conforme aux exigences 4.1, 4.2, 4.3, 4.4 pour les APIs d'authentification sécurisées
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Authentification", description = "API pour l'authentification et la gestion des sessions JWT")
public class AuthController {

    private final JwtAuthenticationService jwtAuthenticationService;

    /**
     * Authentifie un utilisateur et retourne un token JWT
     */
    @PostMapping("/login")
    @Operation(
        summary = "Connexion utilisateur", 
        description = """
            Authentifie un utilisateur avec ses identifiants et retourne un token JWT.
            
            Le token JWT doit être inclus dans l'en-tête Authorization de toutes les requêtes suivantes :
            `Authorization: Bearer <token>`
            
            **Sécurité :**
            - Les mots de passe sont hachés avec BCrypt
            - Les tokens expirent après 24 heures
            - Les tentatives de connexion échouées sont limitées
            """,
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Identifiants de connexion",
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = AuthenticationRequest.class),
                examples = @ExampleObject(
                    name = "Connexion admin",
                    summary = "Exemple de connexion administrateur",
                    value = """
                        {
                          "username": "admin@bantuops.com",
                          "password": "SecurePassword123!"
                        }
                        """
                )
            )
        )
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200", 
            description = "Authentification réussie",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = AuthenticationResponse.class),
                examples = @ExampleObject(
                    name = "Réponse de connexion",
                    summary = "Token JWT généré avec succès",
                    value = """
                        {
                          "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                          "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                          "expiresIn": 86400,
                          "tokenType": "Bearer",
                          "user": {
                            "id": 1,
                            "username": "admin@bantuops.com",
                            "roles": ["ADMIN"],
                            "permissions": ["READ", "WRITE", "DELETE"]
                          }
                        }
                        """
                )
            )
        ),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "401", 
                    description = "Identifiants invalides",
                    content = @Content(
                        mediaType = "application/json",
                        examples = @ExampleObject(
                            name = "Erreur d'authentification",
                            value = """
                                {
                                  "code": "AUTHENTICATION_FAILED",
                                  "message": "Nom d'utilisateur ou mot de passe incorrect",
                                  "timestamp": "2024-01-15T10:30:00Z",
                                  "path": "/api/auth/login"
                                }
                                """
                        )
                    )),
        @ApiResponse(responseCode = "429", 
                    description = "Trop de tentatives de connexion",
                    content = @Content(
                        mediaType = "application/json",
                        examples = @ExampleObject(
                            name = "Limite de tentatives atteinte",
                            value = """
                                {
                                  "code": "TOO_MANY_ATTEMPTS",
                                  "message": "Trop de tentatives de connexion. Réessayez dans 30 minutes.",
                                  "timestamp": "2024-01-15T10:30:00Z",
                                  "path": "/api/auth/login"
                                }
                                """
                        )
                    ))
    })
    public ResponseEntity<AuthenticationResponse> login(
            @Valid @RequestBody AuthenticationRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("Tentative de connexion pour l'utilisateur: {}", request.getUsername());
        
        try {
            AuthenticationResponse response = jwtAuthenticationService.authenticate(request);
            
            log.info("Connexion réussie pour l'utilisateur: {}", request.getUsername());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.warn("Échec de connexion pour l'utilisateur {}: {}", request.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /**
     * Renouvelle un token JWT expiré
     */
    @PostMapping("/refresh")
    @Operation(
        summary = "Renouveler le token", 
        description = """
            Renouvelle un token JWT expiré en utilisant le refresh token.
            
            **Important :**
            - Le refresh token doit être valide et non expiré
            - Un nouveau token et refresh token sont générés
            - L'ancien refresh token devient invalide
            """,
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Refresh token pour renouveler l'accès",
            required = true,
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Renouvellement de token",
                    value = """
                        {
                          "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
                        }
                        """
                )
            )
        )
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200", 
            description = "Token renouvelé avec succès",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = TokenResponse.class)
            )
        ),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "401", description = "Refresh token invalide ou expiré")
    })
    public ResponseEntity<TokenResponse> refreshToken(
            @RequestBody Map<String, String> request) {
        
        String refreshToken = request.get("refreshToken");
        log.info("Demande de renouvellement de token");
        
        try {
            TokenResponse response = jwtAuthenticationService.refreshToken(refreshToken);
            
            log.info("Token renouvelé avec succès");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.warn("Échec du renouvellement de token: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /**
     * Déconnecte un utilisateur et révoque son token
     */
    @PostMapping("/logout")
    @Operation(
        summary = "Déconnexion utilisateur", 
        description = """
            Déconnecte l'utilisateur et révoque son token JWT.
            
            **Sécurité :**
            - Le token devient immédiatement invalide
            - Toutes les sessions actives sont terminées
            - L'action est auditée
            """,
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Token à révoquer (optionnel si fourni dans l'en-tête)",
            required = false,
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Déconnexion",
                    value = """
                        {
                          "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
                        }
                        """
                )
            )
        )
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200", 
            description = "Déconnexion réussie",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Confirmation de déconnexion",
                    value = """
                        {
                          "message": "Déconnexion réussie",
                          "timestamp": "2024-01-15T10:30:00Z"
                        }
                        """
                )
            )
        ),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    })
    public ResponseEntity<Map<String, Object>> logout(
            @RequestBody(required = false) Map<String, String> request,
            HttpServletRequest httpRequest) {
        
        String token = null;
        if (request != null) {
            token = request.get("token");
        }
        
        // Extraire le token de l'en-tête Authorization si non fourni dans le body
        if (token == null) {
            String authHeader = httpRequest.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }
        }
        
        log.info("Demande de déconnexion");
        
        try {
            if (token != null) {
                jwtAuthenticationService.revokeToken(token);
            }
            
            Map<String, Object> response = Map.of(
                "message", "Déconnexion réussie",
                "timestamp", java.time.LocalDateTime.now()
            );
            
            log.info("Déconnexion réussie");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Erreur lors de la déconnexion: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Valide un token JWT
     */
    @PostMapping("/validate")
    @Operation(
        summary = "Valider un token", 
        description = """
            Valide un token JWT et retourne les informations de l'utilisateur.
            
            **Utilisation :**
            - Vérifier la validité d'un token avant utilisation
            - Obtenir les informations de l'utilisateur connecté
            - Vérifier les permissions et rôles
            """,
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Token à valider",
            required = true,
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Validation de token",
                    value = """
                        {
                          "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
                        }
                        """
                )
            )
        )
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200", 
            description = "Token valide",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Token valide",
                    value = """
                        {
                          "valid": true,
                          "user": {
                            "id": 1,
                            "username": "admin@bantuops.com",
                            "roles": ["ADMIN"],
                            "permissions": ["READ", "WRITE", "DELETE"]
                          },
                          "expiresAt": "2024-01-16T10:30:00Z"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "401", 
            description = "Token invalide",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Token invalide",
                    value = """
                        {
                          "valid": false,
                          "error": "Token expiré ou invalide"
                        }
                        """
                )
            )
        )
    })
    public ResponseEntity<Map<String, Object>> validateToken(
            @RequestBody Map<String, String> request) {
        
        String token = request.get("token");
        log.info("Demande de validation de token");
        
        try {
            boolean isValid = jwtAuthenticationService.validateToken(token);
            
            if (isValid) {
                var userDetails = jwtAuthenticationService.extractUserDetails(token);
                
                Map<String, Object> response = Map.of(
                    "valid", true,
                    "user", Map.of(
                        "username", userDetails.getUsername(),
                        "authorities", userDetails.getAuthorities()
                    ),
                    "expiresAt", java.time.LocalDateTime.now().plusDays(1) // Placeholder
                );
                
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> response = Map.of(
                    "valid", false,
                    "error", "Token invalide ou expiré"
                );
                
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
            
        } catch (Exception e) {
            log.error("Erreur lors de la validation du token: {}", e.getMessage());
            
            Map<String, Object> response = Map.of(
                "valid", false,
                "error", "Erreur de validation du token"
            );
            
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    /**
     * Récupère les informations de l'utilisateur connecté
     */
    @GetMapping("/me")
    @Operation(
        summary = "Profil utilisateur", 
        description = """
            Récupère les informations du profil de l'utilisateur connecté.
            
            **Authentification requise :**
            - Token JWT valide dans l'en-tête Authorization
            """,
        security = @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200", 
            description = "Informations utilisateur récupérées",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "Profil utilisateur",
                    value = """
                        {
                          "id": 1,
                          "username": "admin@bantuops.com",
                          "email": "admin@bantuops.com",
                          "roles": ["ADMIN"],
                          "permissions": ["READ", "WRITE", "DELETE"],
                          "lastLogin": "2024-01-15T10:30:00Z",
                          "profile": {
                            "firstName": "Administrateur",
                            "lastName": "Système",
                            "company": "BantuOps"
                          }
                        }
                        """
                )
            )
        ),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    })
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getCurrentUser(HttpServletRequest request) {
        
        log.info("Récupération du profil utilisateur");
        
        try {
            // Extraire les informations de l'utilisateur du token JWT
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                var userDetails = jwtAuthenticationService.extractUserDetails(token);
                
                Map<String, Object> userProfile = Map.of(
                    "username", userDetails.getUsername(),
                    "authorities", userDetails.getAuthorities(),
                    "lastLogin", java.time.LocalDateTime.now(), // Placeholder
                    "profile", Map.of(
                        "company", "BantuOps"
                    )
                );
                
                return ResponseEntity.ok(userProfile);
            }
            
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            
        } catch (Exception e) {
            log.error("Erreur lors de la récupération du profil: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}