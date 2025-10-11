package com.bantuops.backend.dto;

import com.bantuops.backend.validation.SenegaleseBusinessRule;
import com.bantuops.backend.validation.SenegalesePhoneNumber;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO EmployeeRequest avec validation des informations personnelles
 * Conforme aux exigences 3.1, 3.2, 3.3, 3.4, 3.5, 3.6 pour la validation des données RH
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@SenegaleseBusinessRule(SenegaleseBusinessRule.RuleType.EMPLOYEE)
public class EmployeeRequest {

    @NotBlank(message = "Le numéro d'employé est obligatoire")
    @Size(max = 20, message = "Le numéro d'employé ne peut pas dépasser 20 caractères")
    @Pattern(regexp = "^[A-Z0-9-]+$", message = "Le numéro d'employé ne peut contenir que des lettres majuscules, chiffres et tirets")
    private String employeeNumber;

    @NotBlank(message = "Le prénom est obligatoire")
    @Size(min = 2, max = 50, message = "Le prénom doit contenir entre 2 et 50 caractères")
    @Pattern(regexp = "^[a-zA-ZÀ-ÿ\\s'-]+$", message = "Le prénom ne peut contenir que des lettres, espaces, apostrophes et tirets")
    private String firstName;

    @NotBlank(message = "Le nom de famille est obligatoire")
    @Size(min = 2, max = 50, message = "Le nom de famille doit contenir entre 2 et 50 caractères")
    @Pattern(regexp = "^[a-zA-ZÀ-ÿ\\s'-]+$", message = "Le nom de famille ne peut contenir que des lettres, espaces, apostrophes et tirets")
    private String lastName;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "L'email doit être valide")
    @Size(max = 100, message = "L'email ne peut pas dépasser 100 caractères")
    private String email;

    @NotBlank(message = "Le numéro de téléphone est obligatoire")
    @SenegalesePhoneNumber
    private String phoneNumber;

    @NotBlank(message = "Le numéro d'identité nationale est obligatoire")
    @Pattern(regexp = "^[0-9]{13}$", message = "Le numéro d'identité nationale sénégalais doit contenir exactement 13 chiffres")
    private String nationalId;

    @NotNull(message = "La date de naissance est obligatoire")
    @Past(message = "La date de naissance doit être dans le passé")
    private LocalDate dateOfBirth;

    @Size(max = 500, message = "L'adresse ne peut pas dépasser 500 caractères")
    private String address;

    @NotBlank(message = "Le poste est obligatoire")
    @Size(max = 100, message = "Le poste ne peut pas dépasser 100 caractères")
    private String position;

    @NotBlank(message = "Le département est obligatoire")
    @Size(max = 100, message = "Le département ne peut pas dépasser 100 caractères")
    private String department;

    @NotNull(message = "La date d'embauche est obligatoire")
    @PastOrPresent(message = "La date d'embauche ne peut pas être dans le futur")
    private LocalDate hireDate;

    @NotNull(message = "Le type de contrat est obligatoire")
    private String contractType;

    @NotNull(message = "Le salaire de base est obligatoire")
    @DecimalMin(value = "0.0", inclusive = false, message = "Le salaire de base doit être positif")
    @Digits(integer = 10, fraction = 2, message = "Le salaire de base doit avoir au maximum 10 chiffres entiers et 2 décimales")
    private BigDecimal baseSalary;

    @Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$", message = "L'heure de début doit être au format HH:mm")
    private String workStartTime;

    @Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$", message = "L'heure de fin doit être au format HH:mm")
    private String workEndTime;

    @Pattern(regexp = "^(LUNDI|MARDI|MERCREDI|JEUDI|VENDREDI|SAMEDI)(,(LUNDI|MARDI|MERCREDI|JEUDI|VENDREDI|SAMEDI))*$", 
             message = "Les jours de travail doivent être séparés par des virgules et être des jours valides")
    private String workDays;

    private Boolean isActive;

    @AssertTrue(message = "L'employé doit avoir au moins 16 ans (âge minimum légal au Sénégal)")
    public boolean isValidAge() {
        if (dateOfBirth == null) {
            return true;
        }
        
        LocalDate minimumBirthDate = LocalDate.now().minusYears(16);
        return dateOfBirth.isBefore(minimumBirthDate) || dateOfBirth.isEqual(minimumBirthDate);
    }

    @AssertTrue(message = "L'employé ne peut pas avoir plus de 65 ans (âge de retraite au Sénégal)")
    public boolean isValidRetirementAge() {
        if (dateOfBirth == null) {
            return true;
        }
        
        LocalDate maximumBirthDate = LocalDate.now().minusYears(65);
        return dateOfBirth.isAfter(maximumBirthDate);
    }

    @AssertTrue(message = "La date d'embauche doit être postérieure à la date de naissance")
    public boolean isHireDateAfterBirthDate() {
        if (dateOfBirth == null || hireDate == null) {
            return true;
        }
        return hireDate.isAfter(dateOfBirth);
    }

    @AssertTrue(message = "L'employé doit avoir au moins 16 ans à la date d'embauche")
    public boolean isValidHireAge() {
        if (dateOfBirth == null || hireDate == null) {
            return true;
        }
        
        LocalDate minimumHireDate = dateOfBirth.plusYears(16);
        return hireDate.isAfter(minimumHireDate) || hireDate.isEqual(minimumHireDate);
    }

    @AssertTrue(message = "Le salaire de base doit respecter le SMIG sénégalais")
    public boolean isValidBaseSalary() {
        if (baseSalary == null) {
            return true;
        }
        
        // SMIG sénégalais (Salaire Minimum Interprofessionnel Garanti) - environ 60 000 XOF/mois
        BigDecimal smig = new BigDecimal("60000");
        return baseSalary.compareTo(smig) >= 0;
    }

    @AssertTrue(message = "L'heure de fin doit être postérieure à l'heure de début")
    public boolean isValidWorkHours() {
        if (workStartTime == null || workEndTime == null) {
            return true;
        }
        
        try {
            java.time.LocalTime startTime = java.time.LocalTime.parse(workStartTime);
            java.time.LocalTime endTime = java.time.LocalTime.parse(workEndTime);
            return endTime.isAfter(startTime);
        } catch (Exception e) {
            return false;
        }
    }

    @AssertTrue(message = "La durée de travail quotidienne ne peut pas dépasser 8 heures (législation sénégalaise)")
    public boolean isValidDailyWorkDuration() {
        if (workStartTime == null || workEndTime == null) {
            return true;
        }
        
        try {
            java.time.LocalTime startTime = java.time.LocalTime.parse(workStartTime);
            java.time.LocalTime endTime = java.time.LocalTime.parse(workEndTime);
            long hours = java.time.Duration.between(startTime, endTime).toHours();
            return hours <= 8;
        } catch (Exception e) {
            return false;
        }
    }

    @AssertTrue(message = "Un employé doit travailler au moins 1 jour par semaine")
    public boolean isValidWorkDays() {
        if (workDays == null || workDays.trim().isEmpty()) {
            return true;
        }
        
        String[] days = workDays.split(",");
        return days.length >= 1 && days.length <= 6; // Maximum 6 jours par semaine
    }

    @AssertTrue(message = "Le type de contrat doit être valide")
    public boolean isValidContractType() {
        if (contractType == null) {
            return true;
        }
        
        return contractType.equals("CDI") || 
               contractType.equals("CDD") || 
               contractType.equals("STAGE") || 
               contractType.equals("CONSULTANT") || 
               contractType.equals("FREELANCE");
    }

    @AssertTrue(message = "Le numéro d'identité nationale doit être valide selon l'algorithme sénégalais")
    public boolean isValidNationalIdChecksum() {
        if (nationalId == null || nationalId.length() != 13) {
            return true;
        }
        
        try {
            // Validation basique du format (13 chiffres)
            Long.parseLong(nationalId);
            
            // Validation de la date de naissance intégrée (positions 1-6: AAMMJJ)
            String birthYear = nationalId.substring(0, 2);
            String birthMonth = nationalId.substring(2, 4);
            String birthDay = nationalId.substring(4, 6);
            
            int year = Integer.parseInt(birthYear);
            int month = Integer.parseInt(birthMonth);
            int day = Integer.parseInt(birthDay);
            
            // Ajustement pour l'année (supposer 19XX si > 50, sinon 20XX)
            year = year > 50 ? 1900 + year : 2000 + year;
            
            // Vérification de la validité de la date
            if (month < 1 || month > 12 || day < 1 || day > 31) {
                return false;
            }
            
            // Vérification de cohérence avec la date de naissance fournie
            if (dateOfBirth != null) {
                return dateOfBirth.getYear() == year && 
                       dateOfBirth.getMonthValue() == month && 
                       dateOfBirth.getDayOfMonth() == day;
            }
            
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}