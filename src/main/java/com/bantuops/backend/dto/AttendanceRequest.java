package com.bantuops.backend.dto;

import com.bantuops.backend.validation.SenegaleseBusinessRule;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO AttendanceRequest avec règles métier
 * Conforme aux exigences 3.1, 3.2, 3.3, 3.4, 3.5, 3.6 pour la validation d'assiduité
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@SenegaleseBusinessRule(SenegaleseBusinessRule.RuleType.ATTENDANCE)
public class AttendanceRequest {

    @NotNull(message = "L'ID de l'employé est obligatoire")
    @Positive(message = "L'ID de l'employé doit être positif")
    private Long employeeId;

    @NotNull(message = "La date de travail est obligatoire")
    @PastOrPresent(message = "La date de travail ne peut pas être dans le futur")
    private LocalDate workDate;

    @NotNull(message = "L'heure de début prévue est obligatoire")
    private LocalTime scheduledStartTime;

    @NotNull(message = "L'heure de fin prévue est obligatoire")
    private LocalTime scheduledEndTime;

    private LocalTime actualStartTime;

    private LocalTime actualEndTime;

    @NotNull(message = "Le type d'assiduité est obligatoire")
    private String attendanceType;

    @Min(value = 0, message = "Les minutes de retard ne peuvent pas être négatives")
    @Max(value = 480, message = "Les minutes de retard ne peuvent pas dépasser 8 heures (480 minutes)")
    private Integer delayMinutes;

    @Min(value = 0, message = "Les minutes de départ anticipé ne peuvent pas être négatives")
    @Max(value = 480, message = "Les minutes de départ anticipé ne peuvent pas dépasser 8 heures (480 minutes)")
    private Integer earlyDepartureMinutes;

    @DecimalMin(value = "0.0", message = "Les heures travaillées ne peuvent pas être négatives")
    @DecimalMax(value = "24.0", message = "Les heures travaillées ne peuvent pas dépasser 24 heures par jour")
    private Double totalHoursWorked;

    @DecimalMin(value = "0.0", message = "Les heures supplémentaires ne peuvent pas être négatives")
    @DecimalMax(value = "12.0", message = "Les heures supplémentaires ne peuvent pas dépasser 12 heures par jour")
    private Double overtimeHours;

    @Size(max = 500, message = "La justification ne peut pas dépasser 500 caractères")
    private String justification;

    private String absenceType;

    private Boolean isPaidAbsence;

    @AssertTrue(message = "L'heure de fin prévue doit être postérieure à l'heure de début prévue")
    public boolean isValidScheduledTimes() {
        if (scheduledStartTime == null || scheduledEndTime == null) {
            return true;
        }
        return scheduledEndTime.isAfter(scheduledStartTime);
    }

    @AssertTrue(message = "L'heure de fin réelle doit être postérieure à l'heure de début réelle")
    public boolean isValidActualTimes() {
        if (actualStartTime == null || actualEndTime == null) {
            return true;
        }
        return actualEndTime.isAfter(actualStartTime);
    }

    @AssertTrue(message = "Les heures supplémentaires nécessitent des heures travaillées")
    public boolean isValidOvertimeHours() {
        if (overtimeHours == null || overtimeHours == 0.0) {
            return true;
        }
        return totalHoursWorked != null && totalHoursWorked > 0.0;
    }

    @AssertTrue(message = "Les heures supplémentaires ne peuvent pas dépasser les heures travaillées")
    public boolean isOvertimeNotExceedingTotal() {
        if (overtimeHours == null || totalHoursWorked == null) {
            return true;
        }
        return overtimeHours <= totalHoursWorked;
    }

    @AssertTrue(message = "Une justification est requise pour les retards de plus de 15 minutes")
    public boolean isJustificationRequiredForDelays() {
        if (delayMinutes == null || delayMinutes <= 15) {
            return true;
        }
        return justification != null && !justification.trim().isEmpty();
    }

    @AssertTrue(message = "Une justification est requise pour les absences")
    public boolean isJustificationRequiredForAbsences() {
        if (!"ABSENT".equals(attendanceType) && !"UNAUTHORIZED_ABSENCE".equals(attendanceType)) {
            return true;
        }
        return justification != null && !justification.trim().isEmpty();
    }

    @AssertTrue(message = "Le type d'absence est requis pour les absences")
    public boolean isAbsenceTypeRequired() {
        if (!"ABSENT".equals(attendanceType) && 
            !"SICK_LEAVE".equals(attendanceType) && 
            !"VACATION".equals(attendanceType) &&
            !"AUTHORIZED_ABSENCE".equals(attendanceType) &&
            !"UNAUTHORIZED_ABSENCE".equals(attendanceType)) {
            return true;
        }
        return absenceType != null && !absenceType.trim().isEmpty();
    }

    @AssertTrue(message = "Les heures réelles sont requises pour les présences")
    public boolean isActualTimesRequiredForPresence() {
        if (!"PRESENT".equals(attendanceType) && !"LATE".equals(attendanceType)) {
            return true;
        }
        return actualStartTime != null && actualEndTime != null;
    }

    @AssertTrue(message = "Les minutes de retard doivent être cohérentes avec les heures réelles")
    public boolean isDelayConsistentWithActualTimes() {
        if (delayMinutes == null || delayMinutes == 0 || 
            scheduledStartTime == null || actualStartTime == null) {
            return true;
        }
        
        // Calculer le retard réel en minutes
        long actualDelayMinutes = java.time.Duration.between(scheduledStartTime, actualStartTime).toMinutes();
        
        // Tolérance de 1 minute pour les arrondis
        return Math.abs(actualDelayMinutes - delayMinutes) <= 1;
    }

    @AssertTrue(message = "Les minutes de départ anticipé doivent être cohérentes avec les heures réelles")
    public boolean isEarlyDepartureConsistentWithActualTimes() {
        if (earlyDepartureMinutes == null || earlyDepartureMinutes == 0 || 
            scheduledEndTime == null || actualEndTime == null) {
            return true;
        }
        
        // Calculer le départ anticipé réel en minutes
        long actualEarlyMinutes = java.time.Duration.between(actualEndTime, scheduledEndTime).toMinutes();
        
        // Tolérance de 1 minute pour les arrondis
        return Math.abs(actualEarlyMinutes - earlyDepartureMinutes) <= 1;
    }

    @AssertTrue(message = "La date de travail ne peut pas être un dimanche (jour de repos légal au Sénégal)")
    public boolean isValidWorkDay() {
        if (workDate == null) {
            return true;
        }
        
        // Au Sénégal, le dimanche est généralement jour de repos
        return workDate.getDayOfWeek() != java.time.DayOfWeek.SUNDAY;
    }

    @AssertTrue(message = "Les heures de travail doivent respecter la législation sénégalaise (max 8h/jour)")
    public boolean isValidDailyWorkHours() {
        if (totalHoursWorked == null) {
            return true;
        }
        
        // Selon le Code du Travail sénégalais, la durée normale est de 8h/jour
        // Les heures supplémentaires sont autorisées mais limitées
        return totalHoursWorked <= 12.0; // 8h normales + 4h supplémentaires max
    }

    @AssertTrue(message = "Le statut de congé payé est requis pour certains types d'absence")
    public boolean isPaidAbsenceStatusRequired() {
        if (!"SICK_LEAVE".equals(absenceType) && 
            !"ANNUAL_LEAVE".equals(absenceType) && 
            !"MATERNITY_LEAVE".equals(absenceType) &&
            !"PATERNITY_LEAVE".equals(absenceType)) {
            return true;
        }
        return isPaidAbsence != null;
    }
}