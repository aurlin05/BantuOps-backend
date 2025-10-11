package com.bantuops.backend.service;

import com.bantuops.backend.entity.AttendanceRecord;
import com.bantuops.backend.entity.Employee;
import com.bantuops.backend.entity.PayrollAdjustment;
import com.bantuops.backend.entity.PayrollRecord;
import com.bantuops.backend.exception.BusinessRuleException;
import com.bantuops.backend.repository.AttendanceRepository;
import com.bantuops.backend.repository.EmployeeRepository;
import com.bantuops.backend.repository.PayrollRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Service de calcul des ajustements de paie liés à l'assiduité
 * Conforme aux exigences 3.7, 3.8, 1.1, 1.2 pour les déductions et ajustements
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class PayrollAdjustmentService {

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final PayrollRepository payrollRepository;
    private final AuditService auditService;

    // Configuration des règles de calcul
    @Value("${bantuops.payroll.working-days-per-month:22}")
    private int workingDaysPerMonth;

    @Value("${bantuops.payroll.working-hours-per-day:8}")
    private int workingHoursPerDay;

    @Value("${bantuops.payroll.delay-penalty-rate:0.5}")
    private double delayPenaltyRate; // Pourcentage du salaire horaire

    @Value("${bantuops.payroll.absence-deduction-full:true}")
    private boolean fullDayDeductionForAbsence;

    @Value("${bantuops.payroll.overtime-rate-normal:1.25}")
    private double overtimeRateNormal; // 125% pour les heures supplémentaires normales

    @Value("${bantuops.payroll.overtime-rate-night:1.5}")
    private double overtimeRateNight; // 150% pour les heures de nuit

    @Value("${bantuops.payroll.overtime-rate-weekend:1.75}")
    private double overtimeRateWeekend; // 175% pour les weekends

    /**
     * Calcule les ajustements d'assiduité pour un employé sur une période
     * Exigences: 3.7, 3.8, 1.1, 1.2
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public List<PayrollAdjustment> calculateAttendanceAdjustments(Long employeeId, YearMonth period) {
        log.info("Calcul des ajustements d'assiduité pour l'employé ID: {}, période: {}", employeeId, period);

        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new BusinessRuleException("Employé non trouvé avec l'ID: " + employeeId));

        // Récupération des enregistrements d'assiduité pour la période
        LocalDate startDate = period.atDay(1);
        LocalDate endDate = period.atEndOfMonth();
        List<AttendanceRecord> attendanceRecords = attendanceRepository
            .findByEmployeeIdAndWorkDateBetween(employeeId, startDate, endDate);

        List<PayrollAdjustment> adjustments = new ArrayList<>();

        // Calcul des déductions pour retards
        calculateDelayPenalties(employee, attendanceRecords, adjustments, period);

        // Calcul des déductions pour absences
        calculateAbsenceDeductions(employee, attendanceRecords, adjustments, period);

        // Calcul des ajustements pour heures supplémentaires
        calculateOvertimeAdjustments(employee, attendanceRecords, adjustments, period);

        // Calcul des ajustements pour demi-journées
        calculateHalfDayAdjustments(employee, attendanceRecords, adjustments, period);

        log.info("Calcul terminé. {} ajustements calculés pour l'employé ID: {}", 
                adjustments.size(), employeeId);

        return adjustments;
    }

    /**
     * Applique les pénalités de retard selon les règles configurées
     * Exigences: 3.7, 3.8
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public PayrollAdjustment applyDelayPenalties(Long employeeId, YearMonth period, 
                                               List<AttendanceRecord> delayRecords) {
        log.info("Application des pénalités de retard pour l'employé ID: {}, période: {}", employeeId, period);

        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new BusinessRuleException("Employé non trouvé avec l'ID: " + employeeId));

        // Calcul du total des minutes de retard
        int totalDelayMinutes = delayRecords.stream()
            .filter(record -> record.getDelayMinutes() != null)
            .mapToInt(AttendanceRecord::getDelayMinutes)
            .sum();

        if (totalDelayMinutes == 0) {
            return null;
        }

        // Calcul de la pénalité
        BigDecimal hourlySalary = calculateHourlySalary(employee.getBaseSalary());
        BigDecimal penaltyPerMinute = hourlySalary
            .multiply(BigDecimal.valueOf(delayPenaltyRate))
            .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        
        BigDecimal totalPenalty = penaltyPerMinute
            .multiply(BigDecimal.valueOf(totalDelayMinutes));

        // Création de l'ajustement
        PayrollAdjustment adjustment = PayrollAdjustment.builder()
            .adjustmentType(PayrollAdjustment.AdjustmentType.ATTENDANCE_PENALTY)
            .description(String.format("Pénalité pour retards - %d minutes total", totalDelayMinutes))
            .amount(totalPenalty.negate()) // Négatif car c'est une déduction
            .reason(String.format("Retards cumulés: %d minutes sur %d jours", 
                   totalDelayMinutes, delayRecords.size()))
            .build();

        log.info("Pénalité de retard calculée: {} FCFA pour l'employé ID: {}", totalPenalty, employeeId);
        return adjustment;
    }

    /**
     * Calcule les déductions pour absences selon les types d'absence
     * Exigences: 3.7, 3.8
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public PayrollAdjustment calculateAbsenceDeductions(Long employeeId, YearMonth period,
                                                      List<AttendanceRecord> absenceRecords) {
        log.info("Calcul des déductions d'absence pour l'employé ID: {}, période: {}", employeeId, period);

        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new BusinessRuleException("Employé non trouvé avec l'ID: " + employeeId));

        // Filtrer les absences non payées
        List<AttendanceRecord> unpaidAbsences = absenceRecords.stream()
            .filter(record -> record.getAttendanceType() == AttendanceRecord.AttendanceType.ABSENT)
            .filter(record -> record.getIsPaidAbsence() == null || !record.getIsPaidAbsence())
            .toList();

        if (unpaidAbsences.isEmpty()) {
            return null;
        }

        // Calcul du salaire journalier
        BigDecimal dailySalary = employee.getBaseSalary()
            .divide(BigDecimal.valueOf(workingDaysPerMonth), 2, RoundingMode.HALF_UP);

        // Calcul de la déduction totale
        BigDecimal totalDeduction = BigDecimal.ZERO;
        for (AttendanceRecord absence : unpaidAbsences) {
            if (absence.getAttendanceType() == AttendanceRecord.AttendanceType.HALF_DAY) {
                totalDeduction = totalDeduction.add(dailySalary.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP));
            } else {
                totalDeduction = totalDeduction.add(dailySalary);
            }
        }

        // Création de l'ajustement
        PayrollAdjustment adjustment = PayrollAdjustment.builder()
            .adjustmentType(PayrollAdjustment.AdjustmentType.DEDUCTION)
            .description(String.format("Déduction pour absences non payées - %d jours", unpaidAbsences.size()))
            .amount(totalDeduction.negate()) // Négatif car c'est une déduction
            .reason(String.format("Absences non payées: %d jours complets", unpaidAbsences.size()))
            .build();

        log.info("Déduction d'absence calculée: {} FCFA pour l'employé ID: {}", totalDeduction, employeeId);
        return adjustment;
    }

    /**
     * Calcule les ajustements pour heures supplémentaires
     * Exigences: 1.1, 1.2
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public List<PayrollAdjustment> calculateOvertimeAdjustments(Long employeeId, YearMonth period,
                                                              List<AttendanceRecord> overtimeRecords) {
        log.info("Calcul des ajustements d'heures supplémentaires pour l'employé ID: {}, période: {}", 
                employeeId, period);

        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new BusinessRuleException("Employé non trouvé avec l'ID: " + employeeId));

        List<PayrollAdjustment> adjustments = new ArrayList<>();
        BigDecimal hourlySalary = calculateHourlySalary(employee.getBaseSalary());

        // Grouper les heures supplémentaires par type
        double normalOvertimeHours = 0;
        double nightOvertimeHours = 0;
        double weekendOvertimeHours = 0;

        for (AttendanceRecord record : overtimeRecords) {
            if (record.getOvertimeHours() != null && record.getOvertimeHours() > 0) {
                if (isWeekend(record.getWorkDate())) {
                    weekendOvertimeHours += record.getOvertimeHours();
                } else if (isNightShift(record)) {
                    nightOvertimeHours += record.getOvertimeHours();
                } else {
                    normalOvertimeHours += record.getOvertimeHours();
                }
            }
        }

        // Calcul des ajustements par type
        if (normalOvertimeHours > 0) {
            BigDecimal normalOvertimeAmount = hourlySalary
                .multiply(BigDecimal.valueOf(overtimeRateNormal))
                .multiply(BigDecimal.valueOf(normalOvertimeHours));

            adjustments.add(PayrollAdjustment.builder()
                .adjustmentType(PayrollAdjustment.AdjustmentType.OVERTIME_ADJUSTMENT)
                .description(String.format("Heures supplémentaires normales - %.1fh", normalOvertimeHours))
                .amount(normalOvertimeAmount)
                .reason(String.format("Heures supplémentaires à %.0f%% - %.1fh", 
                       (overtimeRateNormal - 1) * 100, normalOvertimeHours))
                .build());
        }

        if (nightOvertimeHours > 0) {
            BigDecimal nightOvertimeAmount = hourlySalary
                .multiply(BigDecimal.valueOf(overtimeRateNight))
                .multiply(BigDecimal.valueOf(nightOvertimeHours));

            adjustments.add(PayrollAdjustment.builder()
                .adjustmentType(PayrollAdjustment.AdjustmentType.OVERTIME_ADJUSTMENT)
                .description(String.format("Heures supplémentaires de nuit - %.1fh", nightOvertimeHours))
                .amount(nightOvertimeAmount)
                .reason(String.format("Heures supplémentaires de nuit à %.0f%% - %.1fh", 
                       (overtimeRateNight - 1) * 100, nightOvertimeHours))
                .build());
        }

        if (weekendOvertimeHours > 0) {
            BigDecimal weekendOvertimeAmount = hourlySalary
                .multiply(BigDecimal.valueOf(overtimeRateWeekend))
                .multiply(BigDecimal.valueOf(weekendOvertimeHours));

            adjustments.add(PayrollAdjustment.builder()
                .adjustmentType(PayrollAdjustment.AdjustmentType.OVERTIME_ADJUSTMENT)
                .description(String.format("Heures supplémentaires weekend - %.1fh", weekendOvertimeHours))
                .amount(weekendOvertimeAmount)
                .reason(String.format("Heures supplémentaires weekend à %.0f%% - %.1fh", 
                       (overtimeRateWeekend - 1) * 100, weekendOvertimeHours))
                .build());
        }

        log.info("Ajustements d'heures supplémentaires calculés: {} ajustements pour l'employé ID: {}", 
                adjustments.size(), employeeId);
        return adjustments;
    }

    // Méthodes privées utilitaires

    private void calculateDelayPenalties(Employee employee, List<AttendanceRecord> attendanceRecords,
                                       List<PayrollAdjustment> adjustments, YearMonth period) {
        List<AttendanceRecord> delayRecords = attendanceRecords.stream()
            .filter(record -> record.getDelayMinutes() != null && record.getDelayMinutes() > 0)
            .toList();

        if (!delayRecords.isEmpty()) {
            PayrollAdjustment delayAdjustment = applyDelayPenalties(employee.getId(), period, delayRecords);
            if (delayAdjustment != null) {
                adjustments.add(delayAdjustment);
            }
        }
    }

    private void calculateAbsenceDeductions(Employee employee, List<AttendanceRecord> attendanceRecords,
                                          List<PayrollAdjustment> adjustments, YearMonth period) {
        List<AttendanceRecord> absenceRecords = attendanceRecords.stream()
            .filter(record -> record.getAttendanceType() == AttendanceRecord.AttendanceType.ABSENT ||
                            record.getAttendanceType() == AttendanceRecord.AttendanceType.UNAUTHORIZED_ABSENCE)
            .toList();

        if (!absenceRecords.isEmpty()) {
            PayrollAdjustment absenceAdjustment = calculateAbsenceDeductions(employee.getId(), period, absenceRecords);
            if (absenceAdjustment != null) {
                adjustments.add(absenceAdjustment);
            }
        }
    }

    private void calculateOvertimeAdjustments(Employee employee, List<AttendanceRecord> attendanceRecords,
                                            List<PayrollAdjustment> adjustments, YearMonth period) {
        List<AttendanceRecord> overtimeRecords = attendanceRecords.stream()
            .filter(record -> record.getOvertimeHours() != null && record.getOvertimeHours() > 0)
            .toList();

        if (!overtimeRecords.isEmpty()) {
            List<PayrollAdjustment> overtimeAdjustments = calculateOvertimeAdjustments(
                employee.getId(), period, overtimeRecords);
            adjustments.addAll(overtimeAdjustments);
        }
    }

    private void calculateHalfDayAdjustments(Employee employee, List<AttendanceRecord> attendanceRecords,
                                           List<PayrollAdjustment> adjustments, YearMonth period) {
        List<AttendanceRecord> halfDayRecords = attendanceRecords.stream()
            .filter(record -> record.getAttendanceType() == AttendanceRecord.AttendanceType.HALF_DAY)
            .filter(record -> record.getIsPaidAbsence() == null || !record.getIsPaidAbsence())
            .toList();

        if (!halfDayRecords.isEmpty()) {
            BigDecimal dailySalary = employee.getBaseSalary()
                .divide(BigDecimal.valueOf(workingDaysPerMonth), 2, RoundingMode.HALF_UP);
            BigDecimal halfDayDeduction = dailySalary
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(halfDayRecords.size()));

            PayrollAdjustment halfDayAdjustment = PayrollAdjustment.builder()
                .adjustmentType(PayrollAdjustment.AdjustmentType.DEDUCTION)
                .description(String.format("Déduction demi-journées - %d occurrences", halfDayRecords.size()))
                .amount(halfDayDeduction.negate())
                .reason(String.format("Demi-journées non payées: %d occurrences", halfDayRecords.size()))
                .build();

            adjustments.add(halfDayAdjustment);
        }
    }

    private BigDecimal calculateHourlySalary(BigDecimal baseSalary) {
        return baseSalary
            .divide(BigDecimal.valueOf(workingDaysPerMonth), 2, RoundingMode.HALF_UP)
            .divide(BigDecimal.valueOf(workingHoursPerDay), 2, RoundingMode.HALF_UP);
    }

    private boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek().getValue() == 6 || date.getDayOfWeek().getValue() == 7; // Samedi ou Dimanche
    }

    private boolean isNightShift(AttendanceRecord record) {
        if (record.getActualStartTime() == null) {
            return false;
        }
        // Considérer comme travail de nuit si commence après 22h ou avant 6h
        return record.getActualStartTime().isAfter(java.time.LocalTime.of(22, 0)) ||
               record.getActualStartTime().isBefore(java.time.LocalTime.of(6, 0));
    }
}