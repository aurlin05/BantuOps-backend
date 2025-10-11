package com.bantuops.backend.service;

import com.bantuops.backend.dto.*;
import com.bantuops.backend.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service de validation centralisé combinant Bean Validation et règles métier
 * Conforme aux exigences 3.1, 3.2, 3.3, 3.4, 3.5, 3.6 pour la validation complète
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationService {

    private final Validator validator;
    private final BusinessRuleValidator businessRuleValidator;
    private final MessageSource messageSource;

    /**
     * Valide complètement une requête de paie (Bean Validation + règles métier)
     */
    public ValidationResult validatePayrollRequest(PayrollRequest request) {
        log.debug("Validation complète de la requête de paie pour l'employé: {}", request.getEmployeeId());

        // Validation Bean Validation
        ValidationResult beanValidationResult = performBeanValidation(request);
        
        // Validation des règles métier sénégalaises
        ValidationResult businessRuleResult = businessRuleValidator.validatePayrollData(request);
        
        // Combinaison des résultats
        ValidationResult combinedResult = beanValidationResult.combine(businessRuleResult);
        
        // Ajout de suggestions spécifiques
        if (!combinedResult.isValid()) {
            combinedResult.addSuggestion(getLocalizedMessage("suggestion.check.senegal.regulations"));
        }
        
        log.debug("Validation de paie terminée: {} erreurs, {} avertissements", 
                 combinedResult.getErrors().size(), combinedResult.getWarnings().size());
        
        return combinedResult;
    }

    /**
     * Valide complètement une requête d'employé
     */
    public ValidationResult validateEmployeeRequest(EmployeeRequest request) {
        log.debug("Validation complète de la requête d'employé: {}", request.getEmployeeNumber());

        ValidationResult beanValidationResult = performBeanValidation(request);
        ValidationResult businessRuleResult = businessRuleValidator.validateEmployeeData(request);
        
        ValidationResult combinedResult = beanValidationResult.combine(businessRuleResult);
        
        if (!combinedResult.isValid()) {
            combinedResult.addSuggestion(getLocalizedMessage("suggestion.check.senegal.regulations"));
        }
        
        return combinedResult;
    }

    /**
     * Valide complètement une requête de facture
     */
    public ValidationResult validateInvoiceRequest(InvoiceRequest request) {
        log.debug("Validation complète de la requête de facture");

        ValidationResult beanValidationResult = performBeanValidation(request);
        ValidationResult businessRuleResult = businessRuleValidator.validateInvoiceData(request);
        
        ValidationResult combinedResult = beanValidationResult.combine(businessRuleResult);
        
        if (!combinedResult.isValid()) {
            combinedResult.addSuggestion(getLocalizedMessage("suggestion.check.senegal.regulations"));
        }
        
        return combinedResult;
    }

    /**
     * Valide complètement une requête de transaction
     */
    public ValidationResult validateTransactionRequest(TransactionRequest request) {
        log.debug("Validation complète de la requête de transaction");

        ValidationResult beanValidationResult = performBeanValidation(request);
        ValidationResult businessRuleResult = businessRuleValidator.validateTransactionData(request);
        
        ValidationResult combinedResult = beanValidationResult.combine(businessRuleResult);
        
        if (!combinedResult.isValid()) {
            combinedResult.addSuggestion(getLocalizedMessage("suggestion.check.senegal.regulations"));
        }
        
        return combinedResult;
    }

    /**
     * Valide complètement une requête d'assiduité
     */
    public ValidationResult validateAttendanceRequest(AttendanceRequest request) {
        log.debug("Validation complète de la requête d'assiduité");

        ValidationResult beanValidationResult = performBeanValidation(request);
        ValidationResult businessRuleResult = businessRuleValidator.validateAttendanceData(request);
        
        ValidationResult combinedResult = beanValidationResult.combine(businessRuleResult);
        
        if (!combinedResult.isValid()) {
            combinedResult.addSuggestion(getLocalizedMessage("suggestion.check.senegal.regulations"));
        }
        
        return combinedResult;
    }

    /**
     * Valide et lance une exception si la validation échoue
     */
    public void validateAndThrow(Object request) {
        ValidationResult result = null;
        
        if (request instanceof PayrollRequest) {
            result = validatePayrollRequest((PayrollRequest) request);
        } else if (request instanceof EmployeeRequest) {
            result = validateEmployeeRequest((EmployeeRequest) request);
        } else if (request instanceof InvoiceRequest) {
            result = validateInvoiceRequest((InvoiceRequest) request);
        } else if (request instanceof TransactionRequest) {
            result = validateTransactionRequest((TransactionRequest) request);
        } else if (request instanceof AttendanceRequest) {
            result = validateAttendanceRequest((AttendanceRequest) request);
        } else {
            result = performBeanValidation(request);
        }
        
        if (!result.isValid()) {
            String errorMessage = String.join("; ", result.getErrors());
            throw new BusinessRuleException("VALIDATION_FAILED", errorMessage);
        }
    }

    /**
     * Effectue la validation Bean Validation standard
     */
    private <T> ValidationResult performBeanValidation(T object) {
        Set<ConstraintViolation<T>> violations = validator.validate(object);
        
        if (violations.isEmpty()) {
            return ValidationResult.success();
        }
        
        List<String> errors = violations.stream()
            .map(violation -> {
                String fieldName = violation.getPropertyPath().toString();
                String message = getLocalizedMessage(violation.getMessage());
                return String.format("%s: %s", fieldName, message);
            })
            .collect(Collectors.toList());
        
        return ValidationResult.errors(errors);
    }

    /**
     * Valide spécifiquement les règles sénégalaises
     */
    public ValidationResult validateSenegaleseBusinessRules(Object request) {
        if (request instanceof PayrollRequest) {
            return validateSenegalesePayrollRules((PayrollRequest) request);
        } else if (request instanceof EmployeeRequest) {
            return validateSenegaleseEmployeeRules((EmployeeRequest) request);
        } else if (request instanceof InvoiceRequest) {
            return validateSenegaleseInvoiceRules((InvoiceRequest) request);
        }
        
        return ValidationResult.success();
    }

    /**
     * Valide les règles de paie spécifiques au Sénégal
     */
    private ValidationResult validateSenegalesePayrollRules(PayrollRequest request) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Validation SMIG
        if (request.getBaseSalary() != null && 
            request.getBaseSalary().compareTo(java.math.BigDecimal.valueOf(60000)) < 0) {
            errors.add(getLocalizedMessage("senegal.smig.violation"));
        }
        
        // Validation des heures supplémentaires
        if (request.getOvertimeHours() != null && 
            request.getOvertimeHours().compareTo(java.math.BigDecimal.valueOf(4)) > 0) {
            errors.add(getLocalizedMessage("senegal.overtime.limit.exceeded"));
        }
        
        // Validation des déductions (max 1/3 du salaire)
        if (request.getBaseSalary() != null) {
            java.math.BigDecimal totalDeductions = java.math.BigDecimal.ZERO;
            if (request.getAdvanceDeduction() != null) totalDeductions = totalDeductions.add(request.getAdvanceDeduction());
            if (request.getLoanDeduction() != null) totalDeductions = totalDeductions.add(request.getLoanDeduction());
            if (request.getAbsenceDeduction() != null) totalDeductions = totalDeductions.add(request.getAbsenceDeduction());
            if (request.getDelayPenalty() != null) totalDeductions = totalDeductions.add(request.getDelayPenalty());
            if (request.getOtherDeductions() != null) totalDeductions = totalDeductions.add(request.getOtherDeductions());
            
            java.math.BigDecimal maxDeductions = request.getBaseSalary().divide(java.math.BigDecimal.valueOf(3), 2, java.math.RoundingMode.HALF_UP);
            if (totalDeductions.compareTo(maxDeductions) > 0) {
                errors.add(getLocalizedMessage("senegal.deduction.limit.exceeded"));
            }
        }
        
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    /**
     * Valide les règles d'employé spécifiques au Sénégal
     */
    private ValidationResult validateSenegaleseEmployeeRules(EmployeeRequest request) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Validation âge minimum
        if (request.getDateOfBirth() != null) {
            int age = java.time.LocalDate.now().getYear() - request.getDateOfBirth().getYear();
            if (age < 16) {
                errors.add(getLocalizedMessage("senegal.minimum.age.violation"));
            }
            if (age > 60) {
                warnings.add(getLocalizedMessage("senegal.retirement.age.exceeded"));
            }
        }
        
        // Validation numéro de téléphone sénégalais
        if (request.getPhoneNumber() != null && 
            !businessRuleValidator.validateSenegalPhoneNumber(request.getPhoneNumber())) {
            errors.add(getLocalizedMessage("senegal.phone.number.invalid"));
        }
        
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    /**
     * Valide les règles de facture spécifiques au Sénégal
     */
    private ValidationResult validateSenegaleseInvoiceRules(InvoiceRequest request) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Validation taux TVA sénégalais (18%)
        if (request.getVatRate() != null && 
            request.getVatRate().compareTo(java.math.BigDecimal.valueOf(0.18)) != 0) {
            warnings.add(getLocalizedMessage("senegal.vat.rate.invalid"));
        }
        
        // Validation numéro fiscal pour gros montants
        if (request.getSubtotalAmount() != null && 
            request.getSubtotalAmount().compareTo(java.math.BigDecimal.valueOf(1000000)) > 0) {
            if (request.getClientTaxNumber() == null || request.getClientTaxNumber().trim().isEmpty()) {
                errors.add("Le numéro fiscal du client est obligatoire pour les factures supérieures à 1 000 000 XOF");
            } else if (!businessRuleValidator.validateSenegalTaxNumber(request.getClientTaxNumber())) {
                errors.add(getLocalizedMessage("senegal.tax.number.invalid"));
            }
        }
        
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    /**
     * Récupère un message localisé
     */
    private String getLocalizedMessage(String messageKey) {
        try {
            return messageSource.getMessage(messageKey, null, LocaleContextHolder.getLocale());
        } catch (Exception e) {
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
            return String.format(messageKey, args);
        }
    }
}