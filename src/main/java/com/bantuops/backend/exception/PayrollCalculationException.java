package com.bantuops.backend.exception;

/**
 * Exception spécifique aux calculs de paie
 * Conforme aux exigences de gestion d'erreurs métier
 */
public class PayrollCalculationException extends BusinessException {

    public PayrollCalculationException(String message) {
        super("PAYROLL_CALCULATION_ERROR", message);
    }

    public PayrollCalculationException(String message, Throwable cause) {
        super("PAYROLL_CALCULATION_ERROR", message, cause);
    }

    public PayrollCalculationException(String message, Object... args) {
        super("PAYROLL_CALCULATION_ERROR", String.format(message, args));
    }
}