package com.bantuops.backend.service;

import com.bantuops.backend.dto.OvertimeCalculationResult;
import com.bantuops.backend.entity.Employee;
import com.bantuops.backend.exception.PayrollCalculationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Service de calcul des heures supplémentaires selon le Code du Travail sénégalais
 * Conforme aux exigences 1.6, 3.1, 3.2 pour la gestion des heures supplémentaires
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OvertimeCalculationService {

    private final AuditService auditService;

    // Constantes selon le Code du Travail sénégalais
    private static final BigDecimal REGULAR_OVERTIME_RATE = new BigDecimal("1.25"); // 25% de majoration
    private static final BigDecimal NIGHT_OVERTIME_RATE = new BigDecimal("1.50"); // 50% de majoration
    private static final BigDecimal WEEKEND_OVERTIME_RATE = new BigDecimal("1.50"); // 50% de majoration
    private static final BigDecimal HOLIDAY_OVERTIME_RATE = new BigDecimal("2.00"); // 100% de majoration
    
    private static final LocalTime NIGHT_START = LocalTime.of(22, 0); // 22h00
    private static final LocalTime NIGHT_END = LocalTime.of(6, 0); // 06h00
    private static final BigDecimal MAX_WEEKLY_OVERTIME = new BigDecimal("20"); // 20h max par semaine
    private static final BigDecimal MAX_ANNUAL_OVERTIME = new BigDecimal("130"); // 130h max par an
    
    private static final int CALCULATION_SCALE = 2;

    /**
     * Calcule les heures supplémentaires avec majorations légales
     * Exigences: 1.6, 3.1, 3.2
     */
    @Cacheable(value = "overtime-calculations", key = "#overtimeHours + '_' + #hourlyRate + '_' + #employee?.id")
    public BigDecimal calculateOvertime(BigDecimal overtimeHours, BigDecimal hourlyRate, Employee employee) {
        log.info("Calcul des heures supplémentaires: {} heures à {} FCFA/h pour employé {}", 
                overtimeHours, hourlyRate, employee != null ? employee.getId() : "N/A");

        try {
            if (overtimeHours.compareTo(BigDecimal.ZERO) <= 0) {
                return BigDecimal.ZERO;
            }

            // Validation des limites légales
            validateOvertimeLimits(overtimeHours, employee);

            // Calcul de base des heures supplémentaires
            BigDecimal overtimeAmount = overtimeHours.multiply(hourlyRate).multiply(REGULAR_OVERTIME_RATE)
                .setScale(CALCULATION_SCALE, RoundingMode.HALF_UP);

            // Audit du calcul
            if (employee != null) {
                auditService.logOvertimeCalculation(employee.getId(), overtimeHours, overtimeAmount);
            }

            log.info("Montant des heures supplémentaires calculé: {} FCFA", overtimeAmount);
            return overtimeAmount;

        } catch (Exception e) {
            log.error("Erreur lors du calcul des heures supplémentaires: {}", e.getMessage(), e);
            throw new PayrollCalculationException("Erreur lors du calcul des heures supplémentaires", e);
        }
    }

    /**
     * Calcule les heures supplémentaires avec gestion des heures de nuit et jours fériés
     * Exigences: 1.6, 3.1, 3.2
     */
    public OvertimeCalculationResult calculateDetailedOvertime(OvertimeRequest request) {
        log.info("Calcul détaillé des heures supplémentaires pour l'employé {}", request.getEmployeeId());

        try {
            Employee employee = getEmployee(request.getEmployeeId());
            BigDecimal hourlyRate = calculateHourlyRate(employee.getBaseSalary());

            OvertimeCalculationResult result = OvertimeCalculationResult.builder()
                .employeeId(request.getEmployeeId())
                .period(request.getPeriod())
                .hourlyRate(hourlyRate)
                .build();

            // Calcul des différents types d'heures supplémentaires
            calculateRegularOvertime(result, request, hourlyRate);
            calculateNightOvertime(result, request, hourlyRate);
            calculateWeekendOvertime(result, request, hourlyRate);
            calculateHolidayOvertime(result, request, hourlyRate);

            // Calcul des primes de rendement
            calculatePerformanceBonuses(result, request, employee);

            // Calcul du total
            calculateTotalOvertime(result);

            // Validation des limites légales
            validateOvertimeResult(result, employee);

            // Audit du calcul détaillé
            auditService.logDetailedOvertimeCalculation(request.getEmployeeId(), result);

            log.info("Calcul détaillé terminé - Total: {} FCFA", result.getTotalOvertimeAmount());
            return result;

        } catch (Exception e) {
            log.error("Erreur lors du calcul détaillé des heures supplémentaires: {}", e.getMessage(), e);
            throw new PayrollCalculationException("Erreur lors du calcul détaillé", e);
        }
    }

    /**
     * Vérifie si les heures sont considérées comme heures de nuit
     * Exigences: 1.6, 3.1, 3.2
     */
    public boolean isNightHours(LocalTime startTime, LocalTime endTime) {
        // Heures de nuit: 22h00 à 06h00
        return (startTime.isAfter(NIGHT_START) || startTime.isBefore(NIGHT_END)) ||
               (endTime.isAfter(NIGHT_START) || endTime.isBefore(NIGHT_END));
    }

    /**
     * Vérifie si la date est un jour férié sénégalais
     * Exigences: 1.6, 3.1, 3.2
     */
    public boolean isSenegaleseHoliday(LocalDate date) {
        // Liste des jours fériés sénégalais (à compléter selon le calendrier officiel)
        return isNewYear(date) || 
               isIndependenceDay(date) || 
               isLaborDay(date) || 
               isAssumptionDay(date) || 
               isIslamicHoliday(date);
    }

    /**
     * Calcule les primes de rendement selon les critères de performance
     * Exigences: 1.6, 3.1, 3.2
     */
    public BigDecimal calculatePerformanceBonus(Employee employee, PerformanceMetrics metrics) {
        log.info("Calcul de la prime de rendement pour l'employé {}", employee.getId());

        try {
            BigDecimal baseBonus = BigDecimal.ZERO;

            // Prime de productivité
            if (metrics.getProductivityScore().compareTo(new BigDecimal("80")) >= 0) {
                BigDecimal productivityBonus = employee.getBaseSalary()
                    .multiply(new BigDecimal("0.05")) // 5% du salaire de base
                    .setScale(CALCULATION_SCALE, RoundingMode.HALF_UP);
                baseBonus = baseBonus.add(productivityBonus);
            }

            // Prime de qualité
            if (metrics.getQualityScore().compareTo(new BigDecimal("90")) >= 0) {
                BigDecimal qualityBonus = employee.getBaseSalary()
                    .multiply(new BigDecimal("0.03")) // 3% du salaire de base
                    .setScale(CALCULATION_SCALE, RoundingMode.HALF_UP);
                baseBonus = baseBonus.add(qualityBonus);
            }

            // Prime d'assiduité
            if (metrics.getAttendanceScore().compareTo(new BigDecimal("95")) >= 0) {
                BigDecimal attendanceBonus = employee.getBaseSalary()
                    .multiply(new BigDecimal("0.02")) // 2% du salaire de base
                    .setScale(CALCULATION_SCALE, RoundingMode.HALF_UP);
                baseBonus = baseBonus.add(attendanceBonus);
            }

            // Audit du calcul de prime
            auditService.logPerformanceBonusCalculation(employee.getId(), metrics, baseBonus);

            log.info("Prime de rendement calculée: {} FCFA pour l'employé {}", baseBonus, employee.getId());
            return baseBonus;

        } catch (Exception e) {
            log.error("Erreur lors du calcul de la prime de rendement: {}", e.getMessage(), e);
            throw new PayrollCalculationException("Erreur lors du calcul de la prime", e);
        }
    }

    // Méthodes privées

    private void validateOvertimeLimits(BigDecimal overtimeHours, Employee employee) {
        if (overtimeHours.compareTo(MAX_WEEKLY_OVERTIME) > 0) {
            log.warn("Dépassement de la limite hebdomadaire d'heures supplémentaires pour l'employé {}", 
                    employee != null ? employee.getId() : "N/A");
        }
    }

    private Employee getEmployee(Long employeeId) {
        // Cette méthode devrait récupérer l'employé depuis le repository
        // Pour l'instant, on retourne null pour éviter la dépendance circulaire
        return null;
    }

    private BigDecimal calculateHourlyRate(BigDecimal baseSalary) {
        BigDecimal hoursPerMonth = new BigDecimal("173.33"); // 40h/semaine * 52 semaines / 12 mois
        return baseSalary.divide(hoursPerMonth, CALCULATION_SCALE, RoundingMode.HALF_UP);
    }

    private void calculateRegularOvertime(OvertimeCalculationResult result, OvertimeRequest request, BigDecimal hourlyRate) {
        if (request.getRegularOvertimeHours().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal amount = request.getRegularOvertimeHours()
                .multiply(hourlyRate)
                .multiply(REGULAR_OVERTIME_RATE)
                .setScale(CALCULATION_SCALE, RoundingMode.HALF_UP);
            result.setRegularOvertimeAmount(amount);
            result.setRegularOvertimeHours(request.getRegularOvertimeHours());
        }
    }

    private void calculateNightOvertime(OvertimeCalculationResult result, OvertimeRequest request, BigDecimal hourlyRate) {
        if (request.getNightOvertimeHours().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal amount = request.getNightOvertimeHours()
                .multiply(hourlyRate)
                .multiply(NIGHT_OVERTIME_RATE)
                .setScale(CALCULATION_SCALE, RoundingMode.HALF_UP);
            result.setNightOvertimeAmount(amount);
            result.setNightOvertimeHours(request.getNightOvertimeHours());
        }
    }

    private void calculateWeekendOvertime(OvertimeCalculationResult result, OvertimeRequest request, BigDecimal hourlyRate) {
        if (request.getWeekendOvertimeHours().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal amount = request.getWeekendOvertimeHours()
                .multiply(hourlyRate)
                .multiply(WEEKEND_OVERTIME_RATE)
                .setScale(CALCULATION_SCALE, RoundingMode.HALF_UP);
            result.setWeekendOvertimeAmount(amount);
            result.setWeekendOvertimeHours(request.getWeekendOvertimeHours());
        }
    }

    private void calculateHolidayOvertime(OvertimeCalculationResult result, OvertimeRequest request, BigDecimal hourlyRate) {
        if (request.getHolidayOvertimeHours().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal amount = request.getHolidayOvertimeHours()
                .multiply(hourlyRate)
                .multiply(HOLIDAY_OVERTIME_RATE)
                .setScale(CALCULATION_SCALE, RoundingMode.HALF_UP);
            result.setHolidayOvertimeAmount(amount);
            result.setHolidayOvertimeHours(request.getHolidayOvertimeHours());
        }
    }

    private void calculatePerformanceBonuses(OvertimeCalculationResult result, OvertimeRequest request, Employee employee) {
        if (request.getPerformanceMetrics() != null) {
            BigDecimal performanceBonus = calculatePerformanceBonus(employee, request.getPerformanceMetrics());
            result.setPerformanceBonus(performanceBonus);
        }
    }

    private void calculateTotalOvertime(OvertimeCalculationResult result) {
        BigDecimal total = result.getRegularOvertimeAmount()
            .add(result.getNightOvertimeAmount())
            .add(result.getWeekendOvertimeAmount())
            .add(result.getHolidayOvertimeAmount())
            .add(result.getPerformanceBonus());
        result.setTotalOvertimeAmount(total);
    }

    private void validateOvertimeResult(OvertimeCalculationResult result, Employee employee) {
        BigDecimal totalHours = result.getRegularOvertimeHours()
            .add(result.getNightOvertimeHours())
            .add(result.getWeekendOvertimeHours())
            .add(result.getHolidayOvertimeHours());

        if (totalHours.compareTo(MAX_WEEKLY_OVERTIME) > 0) {
            log.warn("Dépassement de la limite hebdomadaire pour l'employé {}: {} heures", 
                    employee.getId(), totalHours);
        }
    }

    // Méthodes utilitaires pour les jours fériés

    private boolean isNewYear(LocalDate date) {
        return date.getMonthValue() == 1 && date.getDayOfMonth() == 1;
    }

    private boolean isIndependenceDay(LocalDate date) {
        return date.getMonthValue() == 4 && date.getDayOfMonth() == 4; // 4 avril
    }

    private boolean isLaborDay(LocalDate date) {
        return date.getMonthValue() == 5 && date.getDayOfMonth() == 1; // 1er mai
    }

    private boolean isAssumptionDay(LocalDate date) {
        return date.getMonthValue() == 8 && date.getDayOfMonth() == 15; // 15 août
    }

    private boolean isIslamicHoliday(LocalDate date) {
        // Calcul des fêtes islamiques (Tabaski, Korité, etc.)
        // À implémenter selon le calendrier islamique
        return false;
    }
}