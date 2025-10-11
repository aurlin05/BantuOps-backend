package com.bantuops.backend.exception;

/**
 * Exception pour les conflits de ressources (ressource déjà existante)
 */
public class ResourceConflictException extends BusinessException {

    public ResourceConflictException(String resourceType, Object resourceId) {
        super("RESOURCE_CONFLICT", 
              String.format("%s avec l'ID '%s' existe déjà", resourceType, resourceId));
    }

    public ResourceConflictException(String message) {
        super("RESOURCE_CONFLICT", message);
    }

    public ResourceConflictException(String message, Throwable cause) {
        super("RESOURCE_CONFLICT", message, cause);
    }
}