package com.bantuops.backend.exception;

/**
 * Exception pour les violations de règles métier
 */
public class BusinessRuleException extends BusinessException {

    public BusinessRuleException(String message) {
        super("BUSINESS_RULE_VIOLATION", message);
    }

    public BusinessRuleException(String message, Throwable cause) {
        super("BUSINESS_RULE_VIOLATION", message, cause);
    }
}