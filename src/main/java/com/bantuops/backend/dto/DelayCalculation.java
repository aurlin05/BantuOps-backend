package com.bantuops.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * DTO pour les résultats de calcul de retard
 * Conforme aux exigences 3.1, 3.2, 3.3, 3.4 pour la gestion des retards
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DelayCalculation {

    private Long employeeId;
    private String employeeNumber;
    private String employeeName;
    
    private LocalTime scheduledStartTime;
    private LocalTime actualStartTime;
    
    private Integer delayMinutes;
    private String delayCategory; // MINOR, MODERATE, SEVERE
    
    private BigDecimal penaltyAmount;
    private String penaltyType; // WARNING, DEDUCTION, DISCIPLINARY
    
    private boolean requiresJustification;
    private boolean requiresApproval;
    
    private String applicableRule;
    private String ruleDescription;
    
    private boolean isRecurringOffender;
    private Integer delayCountThisMonth;
    
    private String recommendedAction;
    private String notes;

    /**
     * Détermine la catégorie de retard selon les standards sénégalais
     */
    public String determineDelayCategory() {
        if (delayMinutes == null || delayMinutes <= 0) {
            return "NONE";
        } else if (delayMinutes <= 15) {
            return "MINOR";
        } else if (delayMinutes <= 60) {
            return "MODERATE";
        } else {
            return "SEVERE";
        }
    }

    /**
     * Vérifie si le retard nécessite une action disciplinaire
     */
    public boolean requiresDisciplinaryAction() {
        return "SEVERE".equals(delayCategory) || isRecurringOffender;
    }

    /**
     * Calcule le pourcentage de retard par rapport à la journée de travail
     */
    public double getDelayPercentage() {
        if (delayMinutes == null || delayMinutes <= 0) {
            return 0.0;
        }
        // Basé sur une journée de 8 heures (480 minutes)
        return (delayMinutes.doubleValue() / 480.0) * 100.0;
    }
}