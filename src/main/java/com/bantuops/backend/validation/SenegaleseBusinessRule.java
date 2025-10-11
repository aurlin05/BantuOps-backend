package com.bantuops.backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Annotation de validation pour les règles métier sénégalaises
 * Conforme aux exigences 3.1, 3.2, 3.3 pour la validation locale
 */
@Documented
@Constraint(validatedBy = SenegaleseBusinessRuleValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SenegaleseBusinessRule {
    
    String message() default "Violation des règles métier sénégalaises";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
    
    /**
     * Type de règle métier à valider
     */
    RuleType value();
    
    /**
     * Types de règles métier sénégalaises
     */
    enum RuleType {
        PAYROLL,        // Règles de paie
        EMPLOYEE,       // Règles d'employé
        INVOICE,        // Règles de facturation
        TRANSACTION,    // Règles de transaction
        ATTENDANCE      // Règles d'assiduité
    }
}