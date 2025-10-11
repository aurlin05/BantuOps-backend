package com.bantuops.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO pour les données d'assiduité simplifiées
 * Utilisé pour l'enregistrement rapide d'assiduité
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceData {

    private LocalDate workDate;
    private LocalTime actualStartTime;
    private LocalTime actualEndTime;
    private String attendanceType;
    private String justification;
    private Double totalHoursWorked;
    private Double overtimeHours;
    private String location; // Lieu de pointage
    private String deviceId; // ID du dispositif de pointage
    private String ipAddress; // Adresse IP pour traçabilité

    /**
     * Calcule automatiquement les heures travaillées
     */
    public Double calculateTotalHours() {
        if (actualStartTime == null || actualEndTime == null) {
            return null;
        }
        
        long minutes = java.time.Duration.between(actualStartTime, actualEndTime).toMinutes();
        return minutes / 60.0;
    }

    /**
     * Détermine automatiquement le type d'assiduité basé sur les heures
     */
    public String determineAttendanceType(LocalTime scheduledStartTime) {
        if (actualStartTime == null) {
            return "ABSENT";
        }
        
        if (scheduledStartTime != null && actualStartTime.isAfter(scheduledStartTime)) {
            return "LATE";
        }
        
        return "PRESENT";
    }
}