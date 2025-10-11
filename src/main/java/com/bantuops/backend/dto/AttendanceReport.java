package com.bantuops.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * DTO pour les rapports d'assiduité
 * Conforme aux exigences 3.6, 3.8, 2.4, 2.5 pour les rapports RH
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceReport {

    // Informations générales du rapport
    private String reportId;
    private String reportType;
    private YearMonth reportPeriod;
    private LocalDate generatedDate;
    private String generatedBy;
    
    // Statistiques globales
    private AttendanceStatistics globalStatistics;
    
    // Statistiques par employé
    private List<EmployeeAttendanceStatistics> employeeStatistics;
    
    // Statistiques par département
    private List<DepartmentAttendanceStatistics> departmentStatistics;
    
    // Violations et alertes
    private List<AttendanceViolationSummary> violations;
    
    // Tendances et analyses
    private AttendanceTrends trends;
    
    // Recommandations
    private List<String> recommendations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttendanceStatistics {
        private int totalWorkingDays;
        private int totalEmployees;
        private long totalPresentDays;
        private long totalLateDays;
        private long totalAbsentDays;
        private long totalHalfDays;
        
        private double averageDelayMinutes;
        private double attendanceRate; // Pourcentage de présence
        private double punctualityRate; // Pourcentage de ponctualité
        
        private BigDecimal totalOvertimeHours;
        private BigDecimal totalPenaltyAmount;
        
        // Calculs dérivés
        public double getAbsenteeismRate() {
            if (totalWorkingDays == 0 || totalEmployees == 0) return 0.0;
            return (double) totalAbsentDays / (totalWorkingDays * totalEmployees) * 100;
        }
        
        public double getDelayRate() {
            if (totalWorkingDays == 0 || totalEmployees == 0) return 0.0;
            return (double) totalLateDays / (totalWorkingDays * totalEmployees) * 100;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmployeeAttendanceStatistics {
        private Long employeeId;
        private String employeeNumber;
        private String employeeName;
        private String department;
        
        private int workingDaysInPeriod;
        private int presentDays;
        private int lateDays;
        private int absentDays;
        private int halfDays;
        
        private int totalDelayMinutes;
        private double averageDelayMinutes;
        private double overtimeHours;
        
        private double attendanceRate;
        private double punctualityRate;
        
        private BigDecimal penaltyAmount;
        private BigDecimal overtimeAmount;
        
        private int violationCount;
        private String performanceRating; // EXCELLENT, GOOD, AVERAGE, POOR
        
        // Calculs dérivés
        public double calculateAttendanceRate() {
            if (workingDaysInPeriod == 0) return 0.0;
            return (double) presentDays / workingDaysInPeriod * 100;
        }
        
        public double calculatePunctualityRate() {
            if (presentDays == 0) return 0.0;
            return (double) (presentDays - lateDays) / presentDays * 100;
        }
        
        public String determinePerformanceRating() {
            double attendance = calculateAttendanceRate();
            double punctuality = calculatePunctualityRate();
            
            if (attendance >= 95 && punctuality >= 90) return "EXCELLENT";
            if (attendance >= 90 && punctuality >= 80) return "GOOD";
            if (attendance >= 80 && punctuality >= 70) return "AVERAGE";
            return "POOR";
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DepartmentAttendanceStatistics {
        private String departmentName;
        private int employeeCount;
        
        private double averageAttendanceRate;
        private double averagePunctualityRate;
        private double averageDelayMinutes;
        
        private long totalAbsences;
        private long totalDelays;
        private double totalOvertimeHours;
        
        private int violationCount;
        private String departmentRanking; // Position relative aux autres départements
        
        // Comparaison avec la moyenne de l'entreprise
        private double attendanceVsCompanyAverage;
        private double punctualityVsCompanyAverage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttendanceViolationSummary {
        private String violationType;
        private int count;
        private String severity;
        private List<String> affectedEmployees;
        private String recommendedAction;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttendanceTrends {
        private Map<String, Double> monthlyAttendanceRates;
        private Map<String, Double> monthlyPunctualityRates;
        private Map<String, Integer> monthlyViolationCounts;
        
        private String trendDirection; // IMPROVING, STABLE, DECLINING
        private String trendAnalysis;
        
        // Prédictions basées sur les tendances
        private Double predictedNextMonthAttendance;
        private String seasonalPatterns;
    }

    /**
     * Calcule le score global d'assiduité de l'entreprise
     */
    public double calculateOverallAttendanceScore() {
        if (globalStatistics == null) return 0.0;
        
        double attendanceWeight = 0.4;
        double punctualityWeight = 0.3;
        double violationWeight = 0.3;
        
        double attendanceScore = globalStatistics.getAttendanceRate();
        double punctualityScore = globalStatistics.getPunctualityRate();
        double violationScore = Math.max(0, 100 - (violations.size() * 2)); // Pénalité pour violations
        
        return (attendanceScore * attendanceWeight) + 
               (punctualityScore * punctualityWeight) + 
               (violationScore * violationWeight);
    }

    /**
     * Identifie les employés nécessitant une attention particulière
     */
    public List<EmployeeAttendanceStatistics> getEmployeesNeedingAttention() {
        if (employeeStatistics == null) return List.of();
        
        return employeeStatistics.stream()
            .filter(emp -> emp.getAttendanceRate() < 80 || 
                          emp.getPunctualityRate() < 70 || 
                          emp.getViolationCount() > 3)
            .toList();
    }

    /**
     * Identifie le département le plus performant
     */
    public DepartmentAttendanceStatistics getBestPerformingDepartment() {
        if (departmentStatistics == null || departmentStatistics.isEmpty()) return null;
        
        return departmentStatistics.stream()
            .max((d1, d2) -> Double.compare(
                d1.getAverageAttendanceRate() + d1.getAveragePunctualityRate(),
                d2.getAverageAttendanceRate() + d2.getAveragePunctualityRate()
            ))
            .orElse(null);
    }

    /**
     * Génère un résumé exécutif du rapport
     */
    public String generateExecutiveSummary() {
        if (globalStatistics == null) return "Données insuffisantes pour générer un résumé.";
        
        StringBuilder summary = new StringBuilder();
        summary.append(String.format("Rapport d'assiduité pour %s:\n", reportPeriod));
        summary.append(String.format("- Taux de présence global: %.1f%%\n", globalStatistics.getAttendanceRate()));
        summary.append(String.format("- Taux de ponctualité: %.1f%%\n", globalStatistics.getPunctualityRate()));
        summary.append(String.format("- Nombre total de violations: %d\n", violations.size()));
        
        List<EmployeeAttendanceStatistics> needingAttention = getEmployeesNeedingAttention();
        if (!needingAttention.isEmpty()) {
            summary.append(String.format("- Employés nécessitant une attention: %d\n", needingAttention.size()));
        }
        
        DepartmentAttendanceStatistics bestDept = getBestPerformingDepartment();
        if (bestDept != null) {
            summary.append(String.format("- Département le plus performant: %s\n", bestDept.getDepartmentName()));
        }
        
        return summary.toString();
    }
}