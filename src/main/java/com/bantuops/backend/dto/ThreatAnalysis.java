package com.bantuops.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO représentant une analyse des menaces de sécurité
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreatAnalysis {

    private String analysisId;
    private LocalDateTime analysisDate;
    private ThreatLevel overallThreatLevel;
    private double threatScore;
    
    // Patterns d'attaque identifiés
    private List<String> attackPatterns;
    
    // Utilisateurs à haut risque
    private List<String> highRiskUsers;
    
    // Vulnérabilités identifiées
    private List<String> vulnerabilities;
    
    // Recommandations d'atténuation
    private List<String> mitigationRecommendations;
    
    // Analyse des tendances
    private TrendAnalysis trendAnalysis;
    
    // Analyse géographique des menaces
    private GeographicThreatAnalysis geographicAnalysis;
    
    // Analyse temporelle des menaces
    private TemporalThreatAnalysis temporalAnalysis;
    
    // Indicateurs de compromission (IOCs)
    private List<IndicatorOfCompromise> indicatorsOfCompromise;

    /**
     * Niveaux de menace
     */
    public enum ThreatLevel {
        LOW("Faible", 1, "#28a745"),
        MEDIUM("Moyen", 2, "#ffc107"),
        HIGH("Élevé", 3, "#fd7e14"),
        CRITICAL("Critique", 4, "#dc3545");

        private final String label;
        private final int level;
        private final String color;

        ThreatLevel(String label, int level, String color) {
            this.label = label;
            this.level = level;
            this.color = color;
        }

        public String getLabel() {
            return label;
        }

        public int getLevel() {
            return level;
        }

        public String getColor() {
            return color;
        }

        public boolean isHigherThan(ThreatLevel other) {
            return this.level > other.level;
        }
    }

    /**
     * Analyse des tendances de sécurité
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendAnalysis {
        private TrendDirection overallTrend;
        private double trendPercentage;
        private Map<String, TrendDirection> threatTypeTrends;
        private Map<String, Double> threatTypePercentages;
        private List<String> emergingThreats;
        private List<String> decliningThreats;
    }

    /**
     * Direction des tendances
     */
    public enum TrendDirection {
        INCREASING("En augmentation", "↗️"),
        DECREASING("En diminution", "↘️"),
        STABLE("Stable", "→"),
        VOLATILE("Volatile", "↕️");

        private final String label;
        private final String icon;

        TrendDirection(String label, String icon) {
            this.label = label;
            this.icon = icon;
        }

        public String getLabel() {
            return label;
        }

        public String getIcon() {
            return icon;
        }
    }

    /**
     * Analyse géographique des menaces
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeographicThreatAnalysis {
        private Map<String, Integer> threatsByCountry;
        private Map<String, Integer> threatsByRegion;
        private List<String> highRiskCountries;
        private List<String> suspiciousIpRanges;
        private boolean hasInternationalThreats;
    }

    /**
     * Analyse temporelle des menaces
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemporalThreatAnalysis {
        private Map<Integer, Integer> threatsByHour;
        private Map<String, Integer> threatsByDayOfWeek;
        private List<String> peakThreatPeriods;
        private boolean hasAfterHoursActivity;
        private boolean hasWeekendActivity;
    }

    /**
     * Indicateur de compromission
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndicatorOfCompromise {
        private String type; // IP, Hash, Domain, etc.
        private String value;
        private String description;
        private ThreatLevel severity;
        private LocalDateTime firstSeen;
        private LocalDateTime lastSeen;
        private int occurrenceCount;
        private List<String> associatedEvents;
    }

    /**
     * Calcule le score de menace basé sur les alertes et événements
     */
    public double calculateThreatScore(List<SecurityAlert> alerts) {
        if (alerts == null || alerts.isEmpty()) {
            return 0.0;
        }

        double score = 0.0;
        for (SecurityAlert alert : alerts) {
            switch (alert.getSeverity()) {
                case CRITICAL:
                    score += 10.0;
                    break;
                case HIGH:
                    score += 5.0;
                    break;
                case MEDIUM:
                    score += 2.0;
                    break;
                case LOW:
                    score += 1.0;
                    break;
            }
        }

        // Normaliser le score sur 100
        return Math.min(100.0, score);
    }

    /**
     * Détermine si une action immédiate est requise
     */
    public boolean requiresImmediateAction() {
        return overallThreatLevel == ThreatLevel.CRITICAL || 
               (overallThreatLevel == ThreatLevel.HIGH && threatScore > 50.0);
    }

    /**
     * Retourne un résumé textuel de l'analyse
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append(String.format("Niveau de menace: %s (Score: %.1f/100)", 
                overallThreatLevel.getLabel(), threatScore));
        
        if (attackPatterns != null && !attackPatterns.isEmpty()) {
            summary.append(String.format(" | Patterns détectés: %d", attackPatterns.size()));
        }
        
        if (highRiskUsers != null && !highRiskUsers.isEmpty()) {
            summary.append(String.format(" | Utilisateurs à risque: %d", highRiskUsers.size()));
        }
        
        if (vulnerabilities != null && !vulnerabilities.isEmpty()) {
            summary.append(String.format(" | Vulnérabilités: %d", vulnerabilities.size()));
        }
        
        return summary.toString();
    }
}