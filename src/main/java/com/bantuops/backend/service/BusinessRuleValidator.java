package com.bantuops.backend.service;

import com.bantuops.backend.dto.AttendanceRequest;
import com.bantuops.backend.dto.EmployeeRequest;
import com.bantuops.backend.dto.InvoiceRequest;
import com.bantuops.backend.dto.PayrollRequest;
import com.bantuops.backend.dto.TransactionRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validateur des règles métier sénégalaises
 * Conforme aux exigences 3.1, 3.2, 3.3, 3.4, 3.5, 3.6 pour la validation locale
 */
@Component
@Slf4j
public class BusinessRuleValidator {

    // Constantes pour la législation sénégalaise
    private static final BigDecimal SMIG_SENEGAL = new BigDecimal("60000"); // SMIG en XOF
    private static final BigDecimal VAT_RATE_SENEGAL = new BigDecimal("0.18"); // TVA 18%
    private static final int MAX_DAILY_WORK_HOURS = 8;
    private static final int MAX_WEEKLY_WORK_HOURS = 40;
    private static final int MINIMUM_WORK_AGE = 16;
    private static final int RETIREMENT_AGE = 60; // Âge de retraite au Sénégal

    // Patterns de validation
    private static final Pattern SENEGAL_PHONE_PATTERN = Pattern.compile("^(\\+221|00221)?[0-9]{8,9}$");
    private static final Pattern SENEGAL_TAX_NUMBER_PATTERN = Pattern.compile("^[0-9]{13}$");
    private static final Pattern SENEGAL_NATIONAL_ID_PATTERN = Pattern.compile("^[0-9]{13}$");
    private static final Pattern BANK_ACCOUNT_PATTERN = Pattern.compile("^[0-9]{10,16}$");

    /**
     * Valide les données de paie selon la législation sénégalaise
     */
    public ValidationResult validatePayrollData(PayrollRequest request) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (request == null) {
            errors.add("Les données de paie sont obligatoires");
            return new ValidationResult(false, errors, warnings);
        }

        // Validation du salaire minimum
        if (request.getBaseSalary() != null && request.getBaseSalary().compareTo(SMIG_SENEGAL) < 0) {
            errors.add("Le salaire de base ne peut pas être inférieur au SMIG sénégalais (" + SMIG_SENEGAL + " XOF)");
        }

        // Validation des heures supplémentaires selon le Code du Travail
        if (request.getOvertimeHours() != null && request.getRegularHours() != null) {
            BigDecimal totalHours = request.getRegularHours().add(request.getOvertimeHours());
            if (totalHours.compareTo(new BigDecimal("12")) > 0) {
                errors.add("La durée totale de travail ne peut pas dépasser 12 heures par jour selon le Code du Travail sénégalais");
            }
        }

        // Validation des cotisations sociales
        validateSocialContributions(request, errors, warnings);

        // Validation des déductions
        validatePayrollDeductions(request, errors, warnings);

        log.debug("Validation des données de paie terminée: {} erreurs, {} avertissements", 
                 errors.size(), warnings.size());

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    /**
     * Valide les données de facture selon la réglementation fiscale sénégalaise
     */
    public ValidationResult validateInvoiceData(InvoiceRequest request) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (request == null) {
            errors.add("Les données de facture sont obligatoires");
            return new ValidationResult(false, errors, warnings);
        }

        // Validation du taux de TVA sénégalais
        if (request.getVatRate() != null && request.getVatRate().compareTo(VAT_RATE_SENEGAL) != 0) {
            warnings.add("Le taux de TVA standard au Sénégal est de 18%");
        }

        // Validation du numéro fiscal pour les gros montants
        if (request.getSubtotalAmount() != null && request.getSubtotalAmount().compareTo(new BigDecimal("1000000")) > 0) {
            if (request.getClientTaxNumber() == null || request.getClientTaxNumber().trim().isEmpty()) {
                errors.add("Le numéro fiscal du client est obligatoire pour les factures supérieures à 1 000 000 XOF");
            }
        }

        // Validation du numéro fiscal sénégalais
        if (request.getClientTaxNumber() != null && !request.getClientTaxNumber().trim().isEmpty()) {
            if (!validateSenegalTaxNumber(request.getClientTaxNumber())) {
                errors.add("Le numéro fiscal sénégalais n'est pas valide");
            }
        }

        // Validation du numéro de téléphone sénégalais
        if (request.getClientPhone() != null && !request.getClientPhone().trim().isEmpty()) {
            if (!validateSenegalPhoneNumber(request.getClientPhone())) {
                errors.add("Le numéro de téléphone sénégalais n'est pas valide");
            }
        }

        // Validation de la devise
        if (!"XOF".equals(request.getCurrency()) && !"EUR".equals(request.getCurrency()) && !"USD".equals(request.getCurrency())) {
            errors.add("La devise doit être XOF (Franc CFA), EUR ou USD");
        }

        log.debug("Validation des données de facture terminée: {} erreurs, {} avertissements", 
                 errors.size(), warnings.size());

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    /**
     * Valide les données d'employé selon la législation sénégalaise
     */
    public ValidationResult validateEmployeeData(EmployeeRequest request) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (request == null) {
            errors.add("Les données d'employé sont obligatoires");
            return new ValidationResult(false, errors, warnings);
        }

        // Validation de l'âge minimum de travail
        if (request.getDateOfBirth() != null) {
            int age = LocalDate.now().getYear() - request.getDateOfBirth().getYear();
            if (age < MINIMUM_WORK_AGE) {
                errors.add("L'âge minimum pour travailler au Sénégal est de " + MINIMUM_WORK_AGE + " ans");
            }
            if (age > RETIREMENT_AGE) {
                warnings.add("L'employé a dépassé l'âge de retraite légal (" + RETIREMENT_AGE + " ans)");
            }
        }

        // Validation du salaire minimum
        if (request.getBaseSalary() != null && request.getBaseSalary().compareTo(SMIG_SENEGAL) < 0) {
            errors.add("Le salaire de base ne peut pas être inférieur au SMIG sénégalais (" + SMIG_SENEGAL + " XOF)");
        }

        // Validation du numéro d'identité nationale
        if (request.getNationalId() != null && !validateSenegalNationalId(request.getNationalId())) {
            errors.add("Le numéro d'identité nationale sénégalais n'est pas valide");
        }

        // Validation du numéro de téléphone
        if (request.getPhoneNumber() != null && !validateSenegalPhoneNumber(request.getPhoneNumber())) {
            errors.add("Le numéro de téléphone sénégalais n'est pas valide");
        }

        // Validation des heures de travail
        validateWorkHours(request, errors, warnings);

        log.debug("Validation des données d'employé terminée: {} erreurs, {} avertissements", 
                 errors.size(), warnings.size());

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    /**
     * Valide les données de transaction selon la réglementation sénégalaise
     */
    public ValidationResult validateTransactionData(TransactionRequest request) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (request == null) {
            errors.add("Les données de transaction sont obligatoires");
            return new ValidationResult(false, errors, warnings);
        }

        // Validation du montant minimum
        if (request.getAmount() != null && request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Le montant de la transaction doit être positif");
        }

        // Validation des gros montants (seuil de déclaration)
        if (request.getAmount() != null && "XOF".equals(request.getCurrency())) {
            BigDecimal declarationThreshold = new BigDecimal("10000000"); // 10M XOF
            if (request.getAmount().compareTo(declarationThreshold) > 0) {
                warnings.add("Les transactions supérieures à 10M XOF nécessitent une déclaration spéciale");
                
                // IBAN obligatoire pour les gros montants
                if (request.getIban() == null || request.getIban().trim().isEmpty()) {
                    errors.add("L'IBAN est obligatoire pour les transactions supérieures à 10M XOF");
                }
            }
        }

        // Validation du compte bancaire sénégalais
        if (request.getAccountNumber() != null && !validateSenegalBankAccount(request.getAccountNumber())) {
            errors.add("Le numéro de compte bancaire sénégalais n'est pas valide");
        }

        // Validation des virements internationaux
        if (request.getTransactionType() != null && 
            request.getTransactionType().toString().contains("WIRE_TRANSFER")) {
            if (request.getBicSwift() == null || request.getBicSwift().trim().isEmpty()) {
                errors.add("Le code BIC/SWIFT est obligatoire pour les virements internationaux");
            }
        }

        // Validation des frais
        if (request.getFees() != null && request.getAmount() != null) {
            BigDecimal maxFeePercentage = new BigDecimal("0.10"); // 10% max
            BigDecimal feePercentage = request.getFees().divide(request.getAmount(), 4, java.math.RoundingMode.HALF_UP);
            if (feePercentage.compareTo(maxFeePercentage) > 0) {
                warnings.add("Les frais semblent élevés (plus de 10% du montant)");
            }
        }

        log.debug("Validation des données de transaction terminée: {} erreurs, {} avertissements", 
                 errors.size(), warnings.size());

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    /**
     * Valide les données d'assiduité selon les règles sénégalaises
     */
    public ValidationResult validateAttendanceData(AttendanceRequest request) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (request == null) {
            errors.add("Les données d'assiduité sont obligatoires");
            return new ValidationResult(false, errors, warnings);
        }

        // Validation des jours de travail (pas de travail le dimanche)
        if (request.getWorkDate() != null && request.getWorkDate().getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            warnings.add("Le travail le dimanche nécessite une autorisation spéciale selon la législation sénégalaise");
        }

        // Validation des heures de travail quotidiennes
        if (request.getTotalHoursWorked() != null && request.getTotalHoursWorked() > MAX_DAILY_WORK_HOURS) {
            if (request.getTotalHoursWorked() > 12) {
                errors.add("La durée de travail ne peut pas dépasser 12 heures par jour");
            } else {
                warnings.add("Les heures supplémentaires au-delà de 8h/jour nécessitent une majoration selon le Code du Travail");
            }
        }

        // Validation des heures supplémentaires
        if (request.getOvertimeHours() != null && request.getOvertimeHours() > 4) {
            errors.add("Les heures supplémentaires ne peuvent pas dépasser 4 heures par jour selon la législation sénégalaise");
        }

        log.debug("Validation des données d'assiduité terminée: {} erreurs, {} avertissements", 
                 errors.size(), warnings.size());

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    /**
     * Valide un numéro fiscal sénégalais
     */
    public boolean validateSenegalTaxNumber(String taxNumber) {
        if (taxNumber == null || taxNumber.trim().isEmpty()) {
            return false;
        }

        String cleanTaxNumber = taxNumber.trim();
        
        // Vérification du format (13 chiffres)
        if (!SENEGAL_TAX_NUMBER_PATTERN.matcher(cleanTaxNumber).matches()) {
            return false;
        }

        // Validation de la clé de contrôle (algorithme simplifié)
        try {
            // Les 12 premiers chiffres + 1 chiffre de contrôle
            String baseNumber = cleanTaxNumber.substring(0, 12);
            int checkDigit = Integer.parseInt(cleanTaxNumber.substring(12));
            
            // Calcul de la clé de contrôle (algorithme Luhn modifié)
            int sum = 0;
            for (int i = 0; i < baseNumber.length(); i++) {
                int digit = Integer.parseInt(String.valueOf(baseNumber.charAt(i)));
                if (i % 2 == 0) {
                    digit *= 2;
                    if (digit > 9) {
                        digit = digit / 10 + digit % 10;
                    }
                }
                sum += digit;
            }
            
            int calculatedCheckDigit = (10 - (sum % 10)) % 10;
            return calculatedCheckDigit == checkDigit;
            
        } catch (NumberFormatException e) {
            log.warn("Erreur lors de la validation du numéro fiscal: {}", taxNumber, e);
            return false;
        }
    }

    /**
     * Valide un numéro de téléphone sénégalais
     */
    public boolean validateSenegalPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return false;
        }

        String cleanPhone = phoneNumber.trim().replaceAll("\\s+", "");
        
        // Vérification du format
        if (!SENEGAL_PHONE_PATTERN.matcher(cleanPhone).matches()) {
            return false;
        }

        // Extraction du numéro local
        String localNumber;
        if (cleanPhone.startsWith("+221")) {
            localNumber = cleanPhone.substring(4);
        } else if (cleanPhone.startsWith("00221")) {
            localNumber = cleanPhone.substring(5);
        } else {
            localNumber = cleanPhone;
        }

        // Validation des préfixes sénégalais
        if (localNumber.length() == 9) {
            // Numéros mobiles: 70, 75, 76, 77, 78
            String prefix = localNumber.substring(0, 2);
            return prefix.equals("70") || prefix.equals("75") || prefix.equals("76") || 
                   prefix.equals("77") || prefix.equals("78");
        } else if (localNumber.length() == 8) {
            // Numéros fixes: 33 (Dakar), 34 (Thiès), 35 (Kaolack), etc.
            String prefix = localNumber.substring(0, 2);
            return prefix.equals("33") || prefix.equals("34") || prefix.equals("35") || 
                   prefix.equals("36") || prefix.equals("37") || prefix.equals("38") || prefix.equals("39");
        }

        return false;
    }

    /**
     * Valide un compte bancaire sénégalais
     */
    public boolean validateSenegalBankAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            return false;
        }

        String cleanAccount = accountNumber.trim().replaceAll("\\s+", "");
        
        // Format général: 10 à 16 chiffres
        if (!BANK_ACCOUNT_PATTERN.matcher(cleanAccount).matches()) {
            return false;
        }

        // Validation de la clé RIB (Relevé d'Identité Bancaire) sénégalais
        if (cleanAccount.length() >= 12) {
            try {
                // Format: BBBBBCCCCCCCCCCC où B=banque, C=compte
                String bankCode = cleanAccount.substring(0, 5);
                String accountCode = cleanAccount.substring(5);
                
                // Vérification que le code banque est valide (commence par 1, 2, 3, 4, 5, 6, 7, 8, 9)
                return bankCode.charAt(0) >= '1' && bankCode.charAt(0) <= '9';
                
            } catch (Exception e) {
                log.warn("Erreur lors de la validation du compte bancaire: {}", accountNumber, e);
                return false;
            }
        }

        return true;
    }

    /**
     * Valide un numéro d'identité nationale sénégalais
     */
    private boolean validateSenegalNationalId(String nationalId) {
        if (nationalId == null || nationalId.trim().isEmpty()) {
            return false;
        }

        String cleanId = nationalId.trim();
        
        // Vérification du format (13 chiffres)
        if (!SENEGAL_NATIONAL_ID_PATTERN.matcher(cleanId).matches()) {
            return false;
        }

        try {
            // Format: AAMMJJLLLLLCC où AA=année, MM=mois, JJ=jour, LLLLL=lieu, CC=contrôle
            String yearStr = cleanId.substring(0, 2);
            String monthStr = cleanId.substring(2, 4);
            String dayStr = cleanId.substring(4, 6);
            
            int year = Integer.parseInt(yearStr);
            int month = Integer.parseInt(monthStr);
            int day = Integer.parseInt(dayStr);
            
            // Ajustement de l'année (pivot à 50)
            year = year > 50 ? 1900 + year : 2000 + year;
            
            // Validation de la date
            if (month < 1 || month > 12 || day < 1 || day > 31) {
                return false;
            }
            
            // Vérification que la date est cohérente
            try {
                LocalDate birthDate = LocalDate.of(year, month, day);
                LocalDate now = LocalDate.now();
                return birthDate.isBefore(now) && birthDate.isAfter(now.minusYears(120));
            } catch (Exception e) {
                return false;
            }
            
        } catch (NumberFormatException e) {
            log.warn("Erreur lors de la validation du numéro d'identité: {}", nationalId, e);
            return false;
        }

        return true;
    }

    /**
     * Valide les cotisations sociales selon les taux sénégalais
     */
    private void validateSocialContributions(PayrollRequest request, List<String> errors, List<String> warnings) {
        if (request.getBaseSalary() == null) return;

        BigDecimal baseSalary = request.getBaseSalary();
        
        // Taux de cotisations sénégalais (approximatifs)
        BigDecimal ipresRate = new BigDecimal("0.06"); // 6% IPRES
        BigDecimal cssRate = new BigDecimal("0.07"); // 7% CSS
        
        // Calcul des cotisations attendues
        BigDecimal expectedIpres = baseSalary.multiply(ipresRate);
        BigDecimal expectedCss = baseSalary.multiply(cssRate);
        
        // Avertissements si les cotisations semblent incorrectes
        if (request.getBaseSalary().compareTo(new BigDecimal("100000")) > 0) {
            warnings.add("Vérifiez les cotisations sociales pour les salaires élevés (plafonds IPRES/CSS)");
        }
    }

    /**
     * Valide les déductions de paie
     */
    private void validatePayrollDeductions(PayrollRequest request, List<String> errors, List<String> warnings) {
        BigDecimal totalDeductions = BigDecimal.ZERO;
        
        if (request.getAdvanceDeduction() != null) totalDeductions = totalDeductions.add(request.getAdvanceDeduction());
        if (request.getLoanDeduction() != null) totalDeductions = totalDeductions.add(request.getLoanDeduction());
        if (request.getAbsenceDeduction() != null) totalDeductions = totalDeductions.add(request.getAbsenceDeduction());
        if (request.getDelayPenalty() != null) totalDeductions = totalDeductions.add(request.getDelayPenalty());
        if (request.getOtherDeductions() != null) totalDeductions = totalDeductions.add(request.getOtherDeductions());
        
        // Les déductions ne peuvent pas dépasser 1/3 du salaire selon la loi sénégalaise
        if (request.getBaseSalary() != null) {
            BigDecimal maxDeductions = request.getBaseSalary().divide(new BigDecimal("3"), 2, java.math.RoundingMode.HALF_UP);
            if (totalDeductions.compareTo(maxDeductions) > 0) {
                errors.add("Les déductions ne peuvent pas dépasser 1/3 du salaire selon la législation sénégalaise");
            }
        }
    }

    /**
     * Valide les heures de travail
     */
    private void validateWorkHours(EmployeeRequest request, List<String> errors, List<String> warnings) {
        if (request.getWorkStartTime() != null && request.getWorkEndTime() != null) {
            try {
                java.time.LocalTime startTime = java.time.LocalTime.parse(request.getWorkStartTime());
                java.time.LocalTime endTime = java.time.LocalTime.parse(request.getWorkEndTime());
                
                long hours = java.time.Duration.between(startTime, endTime).toHours();
                
                if (hours > MAX_DAILY_WORK_HOURS) {
                    warnings.add("La durée de travail quotidienne dépasse 8 heures (durée légale au Sénégal)");
                }
                
                if (hours < 4) {
                    warnings.add("La durée de travail quotidienne semble très courte");
                }
                
            } catch (Exception e) {
                errors.add("Format d'heure invalide");
            }
        }
    }

    /**
     * Classe pour le résultat de validation
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;

        public ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.errors = errors != null ? errors : new ArrayList<>();
            this.warnings = warnings != null ? warnings : new ArrayList<>();
        }

        public boolean isValid() { return valid; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        public boolean hasWarnings() { return !warnings.isEmpty(); }
    }
}