package com.bantuops.backend.validation;

import com.bantuops.backend.dto.*;
import com.bantuops.backend.service.BusinessRuleValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validateur pour les règles métier sénégalaises
 * Conforme aux exigences 3.1, 3.2, 3.3 pour la validation locale
 */
@Component
@RequiredArgsConstructor
public class SenegaleseBusinessRuleValidator implements ConstraintValidator<SenegaleseBusinessRule, Object> {

    private final BusinessRuleValidator businessRuleValidator;
    private SenegaleseBusinessRule.RuleType ruleType;

    @Override
    public void initialize(SenegaleseBusinessRule constraintAnnotation) {
        this.ruleType = constraintAnnotation.value();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        BusinessRuleValidator.ValidationResult result = null;

        switch (ruleType) {
            case PAYROLL:
                if (value instanceof PayrollRequest) {
                    result = businessRuleValidator.validatePayrollData((PayrollRequest) value);
                }
                break;
            case EMPLOYEE:
                if (value instanceof EmployeeRequest) {
                    result = businessRuleValidator.validateEmployeeData((EmployeeRequest) value);
                }
                break;
            case INVOICE:
                if (value instanceof InvoiceRequest) {
                    result = businessRuleValidator.validateInvoiceData((InvoiceRequest) value);
                }
                break;
            case TRANSACTION:
                if (value instanceof TransactionRequest) {
                    result = businessRuleValidator.validateTransactionData((TransactionRequest) value);
                }
                break;
            case ATTENDANCE:
                if (value instanceof AttendanceRequest) {
                    result = businessRuleValidator.validateAttendanceData((AttendanceRequest) value);
                }
                break;
            default:
                return true;
        }

        if (result != null && !result.isValid()) {
            // Désactiver le message par défaut
            context.disableDefaultConstraintViolation();
            
            // Ajouter les erreurs spécifiques
            for (String error : result.getErrors()) {
                context.buildConstraintViolationWithTemplate(error)
                    .addConstraintViolation();
            }
            
            return false;
        }

        return true;
    }
}