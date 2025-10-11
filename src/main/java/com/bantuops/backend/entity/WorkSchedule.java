package com.bantuops.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/**
 * Entité pour les horaires de travail
 * Conforme aux exigences 3.1, 3.2 pour la gestion des horaires
 */
@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkSchedule {

    @Column(name = "work_start_time")
    private LocalTime startTime;

    @Column(name = "work_end_time")
    private LocalTime endTime;

    @Column(name = "break_start_time")
    private LocalTime breakStartTime;

    @Column(name = "break_end_time")
    private LocalTime breakEndTime;

    @Column(name = "work_days")
    private String workDays; // Jours de travail séparés par des virgules (ex: "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY")

    @Column(name = "weekly_hours")
    private Double weeklyHours;

    @Column(name = "is_flexible")
    @Builder.Default
    private Boolean isFlexible = false;

    @Column(name = "flexibility_minutes")
    private Integer flexibilityMinutes; // Tolérance en minutes pour les horaires flexibles

    /**
     * Calcule les heures de travail quotidiennes
     */
    public Double getDailyHours() {
        if (startTime == null || endTime == null) {
            return null;
        }
        
        long totalMinutes = java.time.Duration.between(startTime, endTime).toMinutes();
        
        // Soustraire la pause si définie
        if (breakStartTime != null && breakEndTime != null) {
            long breakMinutes = java.time.Duration.between(breakStartTime, breakEndTime).toMinutes();
            totalMinutes -= breakMinutes;
        }
        
        return totalMinutes / 60.0;
    }

    /**
     * Vérifie si un jour est un jour de travail
     */
    public boolean isWorkDay(java.time.DayOfWeek dayOfWeek) {
        if (workDays == null || workDays.isEmpty()) {
            return false;
        }
        
        return workDays.contains(dayOfWeek.name());
    }

    /**
     * Calcule l'heure de fin effective en tenant compte de la pause
     */
    public LocalTime getEffectiveEndTime() {
        if (endTime == null) {
            return null;
        }
        
        // Si il y a une pause, l'heure de fin effective est décalée
        if (breakStartTime != null && breakEndTime != null) {
            long breakMinutes = java.time.Duration.between(breakStartTime, breakEndTime).toMinutes();
            return endTime.plusMinutes(breakMinutes);
        }
        
        return endTime;
    }

    /**
     * Vérifie si l'horaire est dans la plage de flexibilité
     */
    public boolean isWithinFlexibility(LocalTime actualTime, LocalTime scheduledTime) {
        if (!isFlexible || flexibilityMinutes == null || scheduledTime == null || actualTime == null) {
            return false;
        }
        
        LocalTime earliestTime = scheduledTime.minusMinutes(flexibilityMinutes);
        LocalTime latestTime = scheduledTime.plusMinutes(flexibilityMinutes);
        
        return !actualTime.isBefore(earliestTime) && !actualTime.isAfter(latestTime);
    }
}