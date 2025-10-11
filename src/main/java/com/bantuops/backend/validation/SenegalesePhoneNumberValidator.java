package com.bantuops.backend.validation;

import com.bantuops.backend.service.BusinessRuleValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validateur pour les numéros de téléphone sénégalais
 * Conforme aux exigences 3.1, 3.2, 3.3 pour la validation locale
 */
@Component
@RequiredArgsConstructor
public class SenegalesePhoneNumberValidator implements ConstraintValidator<SenegalesePhoneNumber, String> {

    private final BusinessRuleValidator businessRuleValidator;
    private boolean required;

    @Override
    public void initialize(SenegalesePhoneNumber constraintAnnotation) {
        this.required = constraintAnnotation.required();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.trim().isEmpty()) {
            return !required;
        }

        return businessRuleValidator.validateSenegalPhoneNumber(value);
    }
}