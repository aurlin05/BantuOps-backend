package com.bantuops.backend.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Classe de réponse d'erreur standardisée
 * Conforme aux exigences 4.2, 4.3, 4.4 pour les réponses d'erreur uniformes
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /**
     * Code d'erreur unique pour identifier le type d'erreur
     */
    private String code;

    /**
     * Message d'erreur localisé pour l'utilisateur
     */
    private String message;

    /**
     * Détails supplémentaires de l'erreur (champs de validation, etc.)
     */
    private Map<String, Object> details;

    /**
     * Timestamp de l'erreur
     */
    private LocalDateTime timestamp;

    /**
     * Chemin de la requête qui a causé l'erreur
     */
    private String path;

    /**
     * ID de trace pour le debugging (optionnel)
     */
    private String traceId;

    /**
     * Suggestions pour résoudre l'erreur (optionnel)
     */
    private String suggestion;

    /**
     * Lien vers la documentation (optionnel)
     */
    private String documentationUrl;

    /**
     * Crée une réponse d'erreur simple
     */
    public static ErrorResponse simple(String code, String message) {
        return ErrorResponse.builder()
            .code(code)
            .message(message)
            .timestamp(LocalDateTime.now())
            .build();
    }

    /**
     * Crée une réponse d'erreur avec détails
     */
    public static ErrorResponse withDetails(String code, String message, Map<String, Object> details) {
        return ErrorResponse.builder()
            .code(code)
            .message(message)
            .details(details)
            .timestamp(LocalDateTime.now())
            .build();
    }

    /**
     * Crée une réponse d'erreur de validation
     */
    public static ErrorResponse validation(String message, Map<String, Object> fieldErrors) {
        return ErrorResponse.builder()
            .code("VALIDATION_ERROR")
            .message(message)
            .details(fieldErrors)
            .timestamp(LocalDateTime.now())
            .suggestion("Veuillez corriger les champs invalides et réessayer")
            .build();
    }

    /**
     * Crée une réponse d'erreur d'authentification
     */
    public static ErrorResponse authentication(String message) {
        return ErrorResponse.builder()
            .code("AUTHENTICATION_ERROR")
            .message(message)
            .timestamp(LocalDateTime.now())
            .suggestion("Veuillez vérifier vos identifiants et réessayer")
            .build();
    }

    /**
     * Crée une réponse d'erreur d'autorisation
     */
    public static ErrorResponse authorization(String message) {
        return ErrorResponse.builder()
            .code("AUTHORIZATION_ERROR")
            .message(message)
            .timestamp(LocalDateTime.now())
            .suggestion("Vous n'avez pas les permissions nécessaires pour cette action")
            .build();
    }

    /**
     * Crée une réponse d'erreur de ressource non trouvée
     */
    public static ErrorResponse notFound(String resource) {
        return ErrorResponse.builder()
            .code("RESOURCE_NOT_FOUND")
            .message(String.format("La ressource '%s' n'a pas été trouvée", resource))
            .timestamp(LocalDateTime.now())
            .suggestion("Veuillez vérifier l'identifiant de la ressource")
            .build();
    }

    /**
     * Crée une réponse d'erreur de conflit
     */
    public static ErrorResponse conflict(String message) {
        return ErrorResponse.builder()
            .code("RESOURCE_CONFLICT")
            .message(message)
            .timestamp(LocalDateTime.now())
            .suggestion("La ressource existe déjà ou est en conflit avec une autre")
            .build();
    }

    /**
     * Crée une réponse d'erreur de règle métier
     */
    public static ErrorResponse businessRule(String message) {
        return ErrorResponse.builder()
            .code("BUSINESS_RULE_VIOLATION")
            .message(message)
            .timestamp(LocalDateTime.now())
            .suggestion("Veuillez respecter les règles métier de l'application")
            .build();
    }

    /**
     * Crée une réponse d'erreur interne du serveur
     */
    public static ErrorResponse internalError() {
        return ErrorResponse.builder()
            .code("INTERNAL_SERVER_ERROR")
            .message("Une erreur interne du serveur s'est produite")
            .timestamp(LocalDateTime.now())
            .suggestion("Veuillez réessayer plus tard ou contacter le support technique")
            .build();
    }
}