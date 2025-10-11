package com.bantuops.backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Annotation de validation pour les numéros fiscaux sénégalais
 * Conforme aux exigences 3.1, 3.2, 3.3 pour la validation locale
 */
@Documented
@Constraint(validatedBy = SenegaleseTaxNumberValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SenegaleseTaxNumber {
    
    String message() default "senegal.tax.number.invalid";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
    
    /**
     * Indique si le champ est obligatoire
     */
    boolean required() default true;
}