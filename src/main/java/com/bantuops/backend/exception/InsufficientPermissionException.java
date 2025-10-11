package com.bantuops.backend.exception;

/**
 * Exception pour les permissions insuffisantes
 */
public class InsufficientPermissionException extends SecurityException {

    private final String resource;
    private final String requiredPermission;

    public InsufficientPermissionException(String resource, String requiredPermission) {
        super(String.format("Permission insuffisante pour %s sur la ressource %s", requiredPermission, resource));
        this.resource = resource;
        this.requiredPermission = requiredPermission;
    }

    public InsufficientPermissionException(String resource, String requiredPermission, String message) {
        super(message);
        this.resource = resource;
        this.requiredPermission = requiredPermission;
    }

    public String getResource() {
        return resource;
    }

    public String getRequiredPermission() {
        return requiredPermission;
    }
}