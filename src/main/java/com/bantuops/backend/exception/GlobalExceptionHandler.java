package com.bantuops.backend.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gestionnaire global d'exceptions avec messages localisés
 * Conforme aux exigences 4.2, 4.3, 4.4, 3.1, 3.2, 3.3 pour la gestion d'erreurs
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * Gestion des erreurs de validation Bean Validation
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex, WebRequest request) {
        
        log.warn("Erreur de validation: {}", ex.getMessage());
        
        Map<String, Object> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = getLocalizedMessage(error.getDefaultMessage());
            fieldErrors.put(fieldName, errorMessage);
        });

        ErrorResponse errorResponse = ErrorResponse.builder()
            .code("VALIDATION_ERROR")
            .message(getLocalizedMessage("validation.failed"))
            .details(fieldErrors)
            .timestamp(LocalDateTime.now())
            .path(request.getDescription(false))
            .suggestion(getLocalizedSuggestion("suggestion.correct.fields"))
            .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Gestion des violations de contraintes
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, WebRequest request) {
        
        log.warn("Violation de contrainte: {}", ex.getMessage());
        
        Map<String, Object> violations = new HashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String fieldName = violation.getPropertyPath().toString();
            String errorMessage = getLocalizedMessage(violation.getMessage());
            violations.put(fieldName, errorMessage);
        }

        ErrorResponse errorResponse = ErrorResponse.builder()
            .code("CONSTRAINT_VIOLATION")
            .message(getLocalizedMessage("constraint.violation"))
            .details(violations)
            .timestamp(LocalDateTime.now())
            .path(request.getDescription(false))
            .suggestion(getLocalizedSuggestion("suggestion.correct.fields"))
            .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Gestion des exceptions de règles métier
     */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRuleViolation(
            BusinessRuleException ex, WebRequest request) {
        
        log.warn("Violation de règle métier: {}", ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .code(ex.getErrorCode())
            .message(getLocalizedMessage(ex.getMessage()))
            .timestamp(LocalDateTime.now())
            .path(request.getDescription(false))
            .build();

        return ResponseEntity.unprocessableEntity().body(errorResponse);
    }

    /**
     * Gestion des exceptions métier génériques
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex, WebRequest request) {
        
        log.warn("Exception métier: {} - {}", ex.getErrorCode(), ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .code(ex.getErrorCode())
            .message(getLocalizedMessage(ex.getMessage()))
            .timestamp(LocalDateTime.now())
            .path(request.getDescription(false))
            .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Gestion des exceptions de calcul de paie
     */
    @ExceptionHandler(PayrollCalculationException.class)
    public ResponseEntity<ErrorResponse> handlePayrollCalculationException(
            PayrollCalculationException ex, WebRequest request) {
        
        log.error("Erreur de calcul de paie: {}", ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .code("PAYROLL_CALCULATION_ERROR")
            .message(getLocalizedMessage("payroll.calculation.failed"))
            .details(Map.of("originalError", ex.getMessage()))
            .timestamp(LocalDateTime.now())
            .path(request.getDescription(false))
            .build();

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(errorResponse);
    }

    /**
     * Gestion des erreurs d'authentification
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex, WebRequest request) {
        
        log.warn("Erreur d'authentification: {}", ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .code("AUTHENTICATION_FAILED")
            .message(getLocalizedMessage("authentication.failed"))
            .timestamp(LocalDateTime.now())
            .path(request.getDescription(false))
            .suggestion(getLocalizedSuggestion("suggestion.check.credentials"))
            .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    /**
     * Gestion des erreurs de credentials invalides
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex, WebRequest request) {
        
        log.warn("Credentials invalides: {}", ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .code("INVALID_CREDENTIALS")
            .message(getLocalizedMessage("credentials.invalid"))
            .timestamp(LocalDateTime.now())
            .path(request.getDescription(false))
            .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    /**
     * Gestion des erreurs d'accès refusé
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, WebRequest request) {
        
        log.warn("Accès refusé: {}", ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .code("ACCESS_DENIED")
            .message(getLocalizedMessage("access.denied"))
            .timestamp(LocalDateTime.now())
            .path(request.getDescription(false))
            .suggestion(getLocalizedSuggestion("suggestion.contact.admin"))
            .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    /**
     * Gestion des erreurs de token invalide
     */
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(
            InvalidTokenException ex, WebRequest request) {
        
        log.warn("Token invalide: {}", ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .code("INVALID_TOKEN")
            .message(getLocalizedMessage("token.invalid"))
            .timestamp(LocalDateTime.now())
            .path(request.getDescription(false))
            .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    /**
     * Gestion des erreurs de ressource non trouvée
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex, WebRequest request) {
        
        log.warn("Ressource non trouvée: {}", ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .code("RESOURCE_NOT_FOUND")
            .message(getLocalizedMessage(ex.getMessage()))
            .timestamp(LocalDateTime.now())
            .path(request.getDescription(false))
            .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * Gestion des erreurs de conflit (ressource déjà existante)
     */
    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<ErrorResponse> handleResourceConflict(
            ResourceConflictException ex, WebRequest request) {
        
        log.warn("Conflit de ressource: {}", ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .code("RESOURCE_CONFLICT")
            .message(getLocalizedMessage(ex.getMessage()))
            .timestamp(LocalDateTime.now())
            .path(request.getDescription(false))
            .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    /**
     * Gestion des erreurs génériques
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, WebRequest request) {
        
        log.error("Erreur interne du serveur: {}", ex.getMessage(), ex);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .code("INTERNAL_SERVER_ERROR")
            .message(getLocalizedMessage("internal.server.error"))
            .timestamp(LocalDateTime.now())
            .path(request.getDescription(false))
            .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Gestion des erreurs d'argument illégal
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {
        
        log.warn("Argument illégal: {}", ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .code("INVALID_ARGUMENT")
            .message(getLocalizedMessage("invalid.argument"))
            .details(Map.of("error", ex.getMessage()))
            .timestamp(LocalDateTime.now())
            .path(request.getDescription(false))
            .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Gestion des erreurs d'état illégal
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(
            IllegalStateException ex, WebRequest request) {
        
        log.warn("État illégal: {}", ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .code("INVALID_STATE")
            .message(getLocalizedMessage("invalid.state"))
            .details(Map.of("error", ex.getMessage()))
            .timestamp(LocalDateTime.now())
            .path(request.getDescription(false))
            .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    /**
     * Récupère un message localisé
     */
    private String getLocalizedMessage(String messageKey) {
        try {
            return messageSource.getMessage(messageKey, null, LocaleContextHolder.getLocale());
        } catch (Exception e) {
            // Si la clé n'existe pas, retourner la clé elle-même
            return messageKey;
        }
    }

    /**
     * Récupère un message localisé avec paramètres
     */
    private String getLocalizedMessage(String messageKey, Object... args) {
        try {
            return messageSource.getMessage(messageKey, args, LocaleContextHolder.getLocale());
        } catch (Exception e) {
            // Si la clé n'existe pas, retourner la clé elle-même avec les paramètres
            return String.format(messageKey, args);
        }
    }

    /**
     * Récupère un message de suggestion localisé
     */
    private String getLocalizedSuggestion(String suggestionKey) {
        try {
            return messageSource.getMessage(suggestionKey, null, LocaleContextHolder.getLocale());
        } catch (Exception e) {
            return null; // Pas de suggestion si la clé n'existe pas
        }
    }
}