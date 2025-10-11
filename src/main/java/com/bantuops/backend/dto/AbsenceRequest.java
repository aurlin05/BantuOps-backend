package com.bantuops.backend.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO pour les demandes d'absence
 * Conforme aux exigences 3.1, 3.2, 3.3, 3.4 pour la gestion des absences
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbsenceRequest {

    @NotNull(message = "L'ID de l'employé est obligatoire")
    @Positive(message = "L'ID de l'employé doit être positif")
    private Long employeeId;

    @NotNull(message = "La date de début d'absence est obligatoire")
    @FutureOrPresent(message = "La date de début ne peut pas être dans le passé")
    private LocalDate startDate;

    @NotNull(message = "La date de fin d'absence est obligatoire")
    private LocalDate endDate;

    @NotNull(message = "Le type d'absence est obligatoire")
    private String absenceType;

    @NotBlank(message = "La raison de l'absence est obligatoire")
    @Size(max = 1000, message = "La raison ne peut pas dépasser 1000 caractères")
    private String reason;

    private Boolean isPaidAbsence;

    @Size(max = 500, message = "Les documents justificatifs ne peuvent pas dépasser 500 caractères")
    private String supportingDocuments;

    private String emergencyContact;

    @Size(max = 500, message = "Les notes additionnelles ne peuvent pas dépasser 500 caractères")
    private String additionalNotes;

    private Boolean isHalfDay;

    private String halfDayPeriod; // MORNING, AFTERNOON

    @AssertTrue(message = "La date de fin doit être postérieure ou égale à la date de début")
    public boolean isValidDateRange() {
        if (startDate == null || endDate == null) {
            return true;
        }
        return !endDate.isBefore(startDate);
    }

    @AssertTrue(message = "La période de demi-journée est requise pour les absences de demi-journée")
    public boolean isHalfDayPeriodValid() {
        if (isHalfDay == null || !isHalfDay) {
            return true;
        }
        return halfDayPeriod != null && 
               ("MORNING".equals(halfDayPeriod) || "AFTERNOON".equals(halfDayPeriod));
    }

    @AssertTrue(message = "Les demi-journées ne peuvent être que sur une seule date")
    public boolean isHalfDayDateValid() {
        if (isHalfDay == null || !isHalfDay) {
            return true;
        }
        return startDate != null && endDate != null && startDate.equals(endDate);
    }

    @AssertTrue(message = "Les documents justificatifs sont requis pour certains types d'absence")
    public boolean isSupportingDocumentRequired() {
        if (!"SICK_LEAVE".equals(absenceType) && 
            !"MATERNITY_LEAVE".equals(absenceType) && 
            !"PATERNITY_LEAVE".equals(absenceType) &&
            !"BEREAVEMENT_LEAVE".equals(absenceType)) {
            return true;
        }
        return supportingDocuments != null && !supportingDocuments.trim().isEmpty();
    }

    @AssertTrue(message = "Le contact d'urgence est requis pour les absences de plus de 3 jours")
    public boolean isEmergencyContactRequired() {
        if (startDate == null || endDate == null) {
            return true;
        }
        
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (daysBetween <= 3) {
            return true;
        }
        
        return emergencyContact != null && !emergencyContact.trim().isEmpty();
    }

    @AssertTrue(message = "La durée d'absence ne peut pas dépasser les limites légales")
    public boolean isValidAbsenceDuration() {
        if (startDate == null || endDate == null || absenceType == null) {
            return true;
        }
        
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
        
        switch (absenceType) {
            case "SICK_LEAVE":
                return daysBetween <= 180; // 6 mois maximum selon la législation sénégalaise
            case "ANNUAL_LEAVE":
                return daysBetween <= 30; // 30 jours maximum d'affilée
            case "MATERNITY_LEAVE":
                return daysBetween <= 98; // 14 semaines selon le Code du Travail sénégalais
            case "PATERNITY_LEAVE":
                return daysBetween <= 10; // 10 jours selon la législation sénégalaise
            case "BEREAVEMENT_LEAVE":
                return daysBetween <= 7; // 7 jours maximum
            case "PERSONAL_LEAVE":
                return daysBetween <= 15; // 15 jours maximum
            default:
                return daysBetween <= 365; // Limite générale d'un an
        }
    }

    @AssertTrue(message = "Les absences ne peuvent pas être planifiées les dimanches")
    public boolean isValidWorkDays() {
        if (startDate == null || endDate == null) {
            return true;
        }
        
        // Vérifier que les dates ne tombent pas uniquement sur des dimanches
        LocalDate current = startDate;
        boolean hasWorkDay = false;
        
        while (!current.isAfter(endDate)) {
            if (current.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) {
                hasWorkDay = true;
                break;
            }
            current = current.plusDays(1);
        }
        
        return hasWorkDay;
    }

    /**
     * Calcule le nombre de jours ouvrables dans la période d'absence
     */
    public long getWorkingDaysCount() {
        if (startDate == null || endDate == null) {
            return 0;
        }
        
        long workingDays = 0;
        LocalDate current = startDate;
        
        while (!current.isAfter(endDate)) {
            // Exclure les dimanches (jour de repos au Sénégal)
            if (current.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) {
                workingDays++;
            }
            current = current.plusDays(1);
        }
        
        return workingDays;
    }

    /**
     * Détermine si l'absence est éligible au congé payé selon la législation sénégalaise
     */
    public boolean isEligibleForPaidLeave() {
        return "ANNUAL_LEAVE".equals(absenceType) || 
               "SICK_LEAVE".equals(absenceType) ||
               "MATERNITY_LEAVE".equals(absenceType) ||
               "PATERNITY_LEAVE".equals(absenceType) ||
               "BEREAVEMENT_LEAVE".equals(absenceType);
    }
}