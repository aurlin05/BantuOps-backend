package com.bantuops.backend.service;

import com.bantuops.backend.dto.PayrollRequest;
import com.bantuops.backend.entity.Employee;
import com.bantuops.backend.entity.PayrollRecord;
import com.bantuops.backend.exception.BusinessRuleException;
import com.bantuops.backend.exception.PayrollCalculationException;
import com.bantuops.backend.repository.EmployeeRepository;
import com.bantuops.backend.repository.PayrollRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Optional;

/**
 * Service de calcul de paie avec méthodes sécurisées selon la législation sénégalaise
 * Conforme aux exigences 1.1, 1.2, 1.3, 3.1, 3.2 pour les calculs de paie
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class PayrollCalculationService {

    private final EmployeeRepository employeeRepository;
    private final PayrollRepository payrollRepository;
    private final TaxCalculationService taxCalculationService;
    private final OvertimeCalculationService overtimeCalculationService;
    private final PayslipGenerationService payslipGenerationService;
    private final BusinessRuleValidator businessRuleValidator;
    private final AuditService auditService;

    // Constantes pour les calculs sénégalais
    private static final BigDecimal HOURS_PER_MONTH = new BigDecimal("173.33"); // 40h/semaine * 52 semaines / 12 mois
    private static final BigDecimal OVERTIME_THRESHOLD = new BigDecimal("173.33");
    private static final int CALCULATION_SCALE = 2;

    /**
     * Calcule la paie complète d'un employé pour une période donnée
     * Exigences: 1.1, 1.2, 1.3, 3.1, 3.2
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public PayrollResult calculatePayroll(Long employeeId, YearMonth period) {
        log.info("Début du calcul de paie pour l'employé {} période {}", employeeId, period);
        
        try {
            // Validation des permissions et des données
            Employee employee = validateAndGetEmployee(employeeId);
            validatePayrollPeriod(period);
            checkExistingPayroll(employeeId, period);

            // Création du résultat de calcul
            PayrollResult result = PayrollResult.builder()
                .employeeId(employeeId)
                .period(period)
                .baseSalary(employee.getBaseSalary())
                .build();

            // Calcul du salaire de base
            calculateBaseSalary(result, employee);

            // Calcul des heures supplémentaires
            calculateOvertimeAmount(result);

            // Calcul des primes et indemnités
            calculateAllowances(result);

            // Calcul du salaire brut
            calculateGrossSalary(result);

            // Calcul des taxes et cotisations sociales
            calculateTaxesAndContributions(result, employee);

            // Calcul des déductions
            calculateDeductions(result);

            // Calcul du salaire net
            calculateNetSalary(result);

            // Validation finale
            validateCalculationResult(result);

            // Audit du calcul
            auditService.logPayrollCalculation(employeeId, period, result);

            log.info("Calcul de paie terminé avec succès pour l'employé {} période {}", employeeId, period);
            return result;

        } catch (Exception e) {
            log.error("Erreur lors du calcul de paie pour l'employé {} période {}: {}", 
                     employeeId, period, e.getMessage(), e);
            throw new PayrollCalculationException("Erreur lors du calcul de paie", e);
        }
    }

    /**
     * Calcule le salaire selon les règles de base sénégalaises
     * Exigences: 1.1, 1.2, 3.1, 3.2
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public PayrollResult calculateSalary(Employee employee, WorkingHours hours) {
        log.info("Calcul du salaire pour l'employé {} avec {} heures", 
                employee.getId(), hours.getTotalHours());

        try {
            PayrollResult result = PayrollResult.builder()
                .employeeId(employee.getId())
                .baseSalary(employee.getBaseSalary())
                .regularHours(hours.getRegularHours())
                .overtimeHours(hours.getOvertimeHours())
                .build();

            // Calcul du salaire horaire
            BigDecimal hourlyRate = calculateHourlyRate(employee.getBaseSalary());
            result.setHourlyRate(hourlyRate);

            // Calcul du salaire pour les heures normales
            BigDecimal regularSalary = hourlyRate.multiply(hours.getRegularHours())
                .setScale(CALCULATION_SCALE, RoundingMode.HALF_UP);
            result.setRegularSalary(regularSalary);

            // Calcul des heures supplémentaires
            if (hours.getOvertimeHours().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal overtimeAmount = overtimeCalculationService.calculateOvertime(
                    hours.getOvertimeHours(), hourlyRate, employee);
                result.setOvertimeAmount(overtimeAmount);
            }

            // Calcul du salaire brut
            BigDecimal grossSalary = regularSalary.add(result.getOvertimeAmount());
            result.setGrossSalary(grossSalary);

            return result;

        } catch (Exception e) {
            log.error("Erreur lors du calcul du salaire pour l'employé {}: {}", 
                     employee.getId(), e.getMessage(), e);
            throw new PayrollCalculationException("Erreur lors du calcul du salaire", e);
        }
    }

    /**
     * Génère un bulletin de paie avec signature numérique
     * Exigences: 1.3, 2.3, 2.4
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public PayslipDocument generatePayslip(PayrollResult result) {
        log.info("Génération du bulletin de paie pour l'employé {} période {}", 
                result.getEmployeeId(), result.getPeriod());

        try {
            // Validation du résultat de calcul
            validatePayrollResult(result);

            // Génération du bulletin
            PayslipDocument payslip = payslipGenerationService.generatePayslip(result);

            // Audit de la génération
            auditService.logPayslipGeneration(result.getEmployeeId(), result.getPeriod());

            log.info("Bulletin de paie généré avec succès pour l'employé {} période {}", 
                    result.getEmployeeId(), result.getPeriod());
            return payslip;

        } catch (Exception e) {
            log.error("Erreur lors de la génération du bulletin pour l'employé {} période {}: {}", 
                     result.getEmployeeId(), result.getPeriod(), e.getMessage(), e);
            throw new PayrollCalculationException("Erreur lors de la génération du bulletin", e);
        }
    }

    /**
     * Sauvegarde un enregistrement de paie calculé
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public PayrollRecord savePayrollRecord(PayrollResult result) {
        log.info("Sauvegarde de l'enregistrement de paie pour l'employé {} période {}", 
                result.getEmployeeId(), result.getPeriod());

        try {
            Employee employee = employeeRepository.findById(result.getEmployeeId())
                .orElseThrow(() -> new BusinessRuleException("Employé non trouvé"));

            PayrollRecord record = PayrollRecord.builder()
                .employee(employee)
                .payrollPeriod(result.getPeriod())
                .baseSalary(result.getBaseSalary())
                .grossSalary(result.getGrossSalary())
                .netSalary(result.getNetSalary())
                .regularHours(result.getRegularHours())
                .overtimeHours(result.getOvertimeHours())
                .overtimeAmount(result.getOvertimeAmount())
                .performanceBonus(result.getPerformanceBonus())
                .transportAllowance(result.getTransportAllowance())
                .mealAllowance(result.getMealAllowance())
                .housingAllowance(result.getHousingAllowance())
                .otherAllowances(result.getOtherAllowances())
                .incomeTax(result.getIncomeTax())
                .ipresContribution(result.getIpresContribution())
                .cssContribution(result.getCssContribution())
                .familyAllowanceContribution(result.getFamilyAllowanceContribution())
                .advanceDeduction(result.getAdvanceDeduction())
                .loanDeduction(result.getLoanDeduction())
                .absenceDeduction(result.getAbsenceDeduction())
                .delayPenalty(result.getDelayPenalty())
                .otherDeductions(result.getOtherDeductions())
                .totalAllowances(result.getTotalAllowances())
                .totalDeductions(result.getTotalDeductions())
                .totalSocialContributions(result.getTotalSocialContributions())
                .status(PayrollRecord.PayrollStatus.CALCULATED)
                .processedDate(LocalDateTime.now())
                .build();

            PayrollRecord savedRecord = payrollRepository.save(record);
            
            // Audit de la sauvegarde
            auditService.logPayrollRecordSaved(savedRecord.getId(), result.getEmployeeId());

            log.info("Enregistrement de paie sauvegardé avec succès ID: {}", savedRecord.getId());
            return savedRecord;

        } catch (Exception e) {
            log.error("Erreur lors de la sauvegarde de l'enregistrement de paie: {}", e.getMessage(), e);
            throw new PayrollCalculationException("Erreur lors de la sauvegarde", e);
        }
    }

    // Méthodes privées de calcul

    private Employee validateAndGetEmployee(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new BusinessRuleException("Employé non trouvé avec l'ID: " + employeeId));
        
        if (!employee.isCurrentlyEmployed()) {
            throw new BusinessRuleException("L'employé n'est plus actif");
        }
        
        return employee;
    }

    private void validatePayrollPeriod(YearMonth period) {
        YearMonth currentMonth = YearMonth.now();
        if (period.isAfter(currentMonth)) {
            throw new BusinessRuleException("La période de paie ne peut pas être dans le futur");
        }
    }

    private void checkExistingPayroll(Long employeeId, YearMonth period) {
        Optional<PayrollRecord> existing = payrollRepository.findByEmployeeIdAndPayrollPeriod(employeeId, period);
        if (existing.isPresent() && existing.get().isProcessed()) {
            throw new BusinessRuleException("Une paie existe déjà pour cette période et est déjà traitée");
        }
    }

    private void calculateBaseSalary(PayrollResult result, Employee employee) {
        result.setBaseSalary(employee.getBaseSalary());
        result.setHourlyRate(calculateHourlyRate(employee.getBaseSalary()));
    }

    private BigDecimal calculateHourlyRate(BigDecimal baseSalary) {
        return baseSalary.divide(HOURS_PER_MONTH, CALCULATION_SCALE, RoundingMode.HALF_UP);
    }

    private void calculateOvertimeAmount(PayrollResult result) {
        if (result.getOvertimeHours() != null && result.getOvertimeHours().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal overtimeAmount = overtimeCalculationService.calculateOvertime(
                result.getOvertimeHours(), result.getHourlyRate(), null);
            result.setOvertimeAmount(overtimeAmount);
        } else {
            result.setOvertimeAmount(BigDecimal.ZERO);
        }
    }

    private void calculateAllowances(PayrollResult result) {
        BigDecimal totalAllowances = BigDecimal.ZERO;
        
        if (result.getPerformanceBonus() != null) {
            totalAllowances = totalAllowances.add(result.getPerformanceBonus());
        }
        if (result.getTransportAllowance() != null) {
            totalAllowances = totalAllowances.add(result.getTransportAllowance());
        }
        if (result.getMealAllowance() != null) {
            totalAllowances = totalAllowances.add(result.getMealAllowance());
        }
        if (result.getHousingAllowance() != null) {
            totalAllowances = totalAllowances.add(result.getHousingAllowance());
        }
        if (result.getOtherAllowances() != null) {
            totalAllowances = totalAllowances.add(result.getOtherAllowances());
        }
        
        result.setTotalAllowances(totalAllowances);
    }

    private void calculateGrossSalary(PayrollResult result) {
        BigDecimal grossSalary = result.getBaseSalary()
            .add(result.getOvertimeAmount())
            .add(result.getTotalAllowances());
        result.setGrossSalary(grossSalary);
    }

    private void calculateTaxesAndContributions(PayrollResult result, Employee employee) {
        // Calcul des taxes selon la législation sénégalaise
        TaxCalculationResult taxResult = taxCalculationService.calculateTaxes(
            result.getGrossSalary(), employee);
        
        result.setIncomeTax(taxResult.getIncomeTax());
        result.setIpresContribution(taxResult.getIpresContribution());
        result.setCssContribution(taxResult.getCssContribution());
        result.setFamilyAllowanceContribution(taxResult.getFamilyAllowanceContribution());
        
        BigDecimal totalSocialContributions = taxResult.getIpresContribution()
            .add(taxResult.getCssContribution())
            .add(taxResult.getFamilyAllowanceContribution());
        result.setTotalSocialContributions(totalSocialContributions);
    }

    private void calculateDeductions(PayrollResult result) {
        BigDecimal totalDeductions = BigDecimal.ZERO;
        
        if (result.getAdvanceDeduction() != null) {
            totalDeductions = totalDeductions.add(result.getAdvanceDeduction());
        }
        if (result.getLoanDeduction() != null) {
            totalDeductions = totalDeductions.add(result.getLoanDeduction());
        }
        if (result.getAbsenceDeduction() != null) {
            totalDeductions = totalDeductions.add(result.getAbsenceDeduction());
        }
        if (result.getDelayPenalty() != null) {
            totalDeductions = totalDeductions.add(result.getDelayPenalty());
        }
        if (result.getOtherDeductions() != null) {
            totalDeductions = totalDeductions.add(result.getOtherDeductions());
        }
        
        result.setTotalDeductions(totalDeductions);
    }

    private void calculateNetSalary(PayrollResult result) {
        BigDecimal netSalary = result.getGrossSalary()
            .subtract(result.getIncomeTax())
            .subtract(result.getTotalSocialContributions())
            .subtract(result.getTotalDeductions());
        
        if (netSalary.compareTo(BigDecimal.ZERO) < 0) {
            throw new PayrollCalculationException("Le salaire net ne peut pas être négatif");
        }
        
        result.setNetSalary(netSalary);
    }

    private void validateCalculationResult(PayrollResult result) {
        businessRuleValidator.validatePayrollCalculation(result);
    }

    private void validatePayrollResult(PayrollResult result) {
        if (result == null) {
            throw new IllegalArgumentException("Le résultat de paie ne peut pas être null");
        }
        if (result.getEmployeeId() == null) {
            throw new IllegalArgumentException("L'ID de l'employé est obligatoire");
        }
        if (result.getPeriod() == null) {
            throw new IllegalArgumentException("La période est obligatoire");
        }
        if (result.getNetSalary() == null || result.getNetSalary().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Le salaire net doit être positif");
        }
    }
}