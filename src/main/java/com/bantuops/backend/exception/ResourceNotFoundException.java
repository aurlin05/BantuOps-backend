package com.bantuops.backend.exception;

/**
 * Exception pour les ressources non trouvées
 */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String resourceType, Object resourceId) {
        super("RESOURCE_NOT_FOUND", 
              String.format("%s avec l'ID '%s' n'a pas été trouvé", resourceType, resourceId));
    }

    public ResourceNotFoundException(String message) {
        super("RESOURCE_NOT_FOUND", message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super("RESOURCE_NOT_FOUND", message, cause);
    }
}