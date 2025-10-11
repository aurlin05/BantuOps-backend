package com.bantuops.backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Annotation de validation pour les comptes bancaires sénégalais
 * Conforme aux exigences 3.1, 3.2, 3.3 pour la validation locale
 */
@Documented
@Constraint(validatedBy = SenegaleseBankAccountValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SenegaleseBankAccount {
    
    String message() default "senegal.bank.account.invalid";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
    
    /**
     * Indique si le champ est obligatoire
     */
    boolean required() default true;
}