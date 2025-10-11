package com.bantuops.backend.repository;

import com.bantuops.backend.entity.PayrollRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * Repository pour PayrollRecord avec agrégations complexes
 * Conforme aux exigences 6.1, 6.2, 6.3, 6.4, 6.5 pour l'optimisation des calculs de paie
 */
@Repository
public interface PayrollRepository extends JpaRepository<PayrollRecord, Long>, JpaSpecificationExecutor<PayrollRecord> {

    /**
     * Trouve l'enregistrement de paie d'un employé pour une période donnée
     */
    Optional<PayrollRecord> findByEmployeeIdAndPayrollPeriod(Long employeeId, YearMonth payrollPeriod);

    /**
     * Trouve les enregistrements de paie d'un employé avec ses ajustements
     */
    @Query("SELECT DISTINCT pr FROM PayrollRecord pr " +
           "LEFT JOIN FETCH pr.adjustments " +
           "WHERE pr.employee.id = :employeeId " +
           "ORDER BY pr.payrollPeriod DESC")
    List<PayrollRecord> findByEmployeeIdWithAdjustments(@Param("employeeId") Long employeeId);

    /**
     * Trouve les enregistrements de paie pour une période avec pagination
     */
    Page<PayrollRecord> findByPayrollPeriodOrderByEmployeeEmployeeNumberAsc(YearMonth payrollPeriod, Pageable pageable);

    /**
     * Trouve les enregistrements de paie par statut
     */
    List<PayrollRecord> findByStatusOrderByPayrollPeriodDesc(PayrollRecord.PayrollStatus status);

    /**
     * Trouve les enregistrements de paie par statut avec pagination
     */
    Page<PayrollRecord> findByStatusOrderByPayrollPeriodDesc(PayrollRecord.PayrollStatus status, Pageable pageable);

    /**
     * Calcule la masse salariale totale pour une période
     */
    @Query("SELECT SUM(pr.grossSalary) FROM PayrollRecord pr " +
           "WHERE pr.payrollPeriod = :period " +
           "AND pr.status IN ('PROCESSED', 'PAID')")
    BigDecimal calculateTotalPayrollForPeriod(@Param("period") YearMonth period);

    /**
     * Calcule les charges sociales totales pour une période
     */
    @Query("SELECT SUM(pr.totalSocialContributions) FROM PayrollRecord pr " +
           "WHERE pr.payrollPeriod = :period " +
           "AND pr.status IN ('PROCESSED', 'PAID')")
    BigDecimal calculateTotalSocialContributionsForPeriod(@Param("period") YearMonth period);

    /**
     * Calcule les impôts sur le revenu collectés pour une période
     */
    @Query("SELECT SUM(pr.incomeTax) FROM PayrollRecord pr " +
           "WHERE pr.payrollPeriod = :period " +
           "AND pr.status IN ('PROCESSED', 'PAID')")
    BigDecimal calculateTotalIncomeTaxForPeriod(@Param("period") YearMonth period);

    /**
     * Statistiques de paie par département pour une période
     */
    @Query("SELECT e.department, " +
           "COUNT(pr) as employeeCount, " +
           "SUM(pr.grossSalary) as totalGrossSalary, " +
           "SUM(pr.netSalary) as totalNetSalary, " +
           "AVG(pr.grossSalary) as avgGrossSalary, " +
           "SUM(pr.overtimeAmount) as totalOvertime " +
           "FROM PayrollRecord pr " +
           "JOIN pr.employee e " +
           "WHERE pr.payrollPeriod = :period " +
           "AND pr.status IN ('PROCESSED', 'PAID') " +
           "GROUP BY e.department " +
           "ORDER BY totalGrossSalary DESC")
    List<Object[]> getPayrollStatsByDepartmentForPeriod(@Param("period") YearMonth period);

    /**
     * Analyse des heures supplémentaires par employé pour une période
     */
    @Query("SELECT pr.employee.id, " +
           "pr.employee.firstName, " +
           "pr.employee.lastName, " +
           "pr.overtimeHours, " +
           "pr.overtimeAmount " +
           "FROM PayrollRecord pr " +
           "WHERE pr.payrollPeriod = :period " +
           "AND pr.overtimeHours > 0 " +
           "AND pr.status IN ('PROCESSED', 'PAID') " +
           "ORDER BY pr.overtimeHours DESC")
    List<Object[]> getOvertimeAnalysisForPeriod(@Param("period") YearMonth period);

    /**
     * Trouve les employés avec les salaires les plus élevés pour une période
     */
    @Query("SELECT pr.employee, pr.grossSalary " +
           "FROM PayrollRecord pr " +
           "WHERE pr.payrollPeriod = :period " +
           "AND pr.status IN ('PROCESSED', 'PAID') " +
           "ORDER BY pr.grossSalary DESC")
    List<Object[]> getTopEarnersByPeriod(@Param("period") YearMonth period, Pageable pageable);

    /**
     * Calcule l'évolution des salaires d'un employé
     */
    @Query("SELECT pr.payrollPeriod, pr.grossSalary, pr.netSalary " +
           "FROM PayrollRecord pr " +
           "WHERE pr.employee.id = :employeeId " +
           "AND pr.status IN ('PROCESSED', 'PAID') " +
           "ORDER BY pr.payrollPeriod DESC")
    List<Object[]> getSalaryEvolutionForEmployee(@Param("employeeId") Long employeeId);

    /**
     * Analyse des déductions par type pour une période
     */
    @Query("SELECT " +
           "SUM(pr.incomeTax) as totalIncomeTax, " +
           "SUM(pr.ipresContribution) as totalIpres, " +
           "SUM(pr.cssContribution) as totalCss, " +
           "SUM(pr.advanceDeduction) as totalAdvances, " +
           "SUM(pr.absenceDeduction) as totalAbsenceDeductions " +
           "FROM PayrollRecord pr " +
           "WHERE pr.payrollPeriod = :period " +
           "AND pr.status IN ('PROCESSED', 'PAID')")
    Object[] getDeductionAnalysisForPeriod(@Param("period") YearMonth period);

    /**
     * Trouve les enregistrements avec des ajustements pour une période
     */
    @Query("SELECT DISTINCT pr FROM PayrollRecord pr " +
           "JOIN pr.adjustments adj " +
           "WHERE pr.payrollPeriod = :period " +
           "ORDER BY pr.employee.employeeNumber")
    List<PayrollRecord> findRecordsWithAdjustmentsForPeriod(@Param("period") YearMonth period);

    /**
     * Calcule les coûts employeur totaux (salaire + charges) pour une période
     */
    @Query("SELECT SUM(pr.grossSalary + pr.totalSocialContributions) FROM PayrollRecord pr " +
           "WHERE pr.payrollPeriod = :period " +
           "AND pr.status IN ('PROCESSED', 'PAID')")
    BigDecimal calculateTotalEmployerCostForPeriod(@Param("period") YearMonth period);

    /**
     * Analyse comparative des périodes de paie
     */
    @Query("SELECT pr.payrollPeriod, " +
           "COUNT(pr) as employeeCount, " +
           "SUM(pr.grossSalary) as totalGross, " +
           "SUM(pr.netSalary) as totalNet, " +
           "SUM(pr.overtimeAmount) as totalOvertime " +
           "FROM PayrollRecord pr " +
           "WHERE pr.payrollPeriod BETWEEN :startPeriod AND :endPeriod " +
           "AND pr.status IN ('PROCESSED', 'PAID') " +
           "GROUP BY pr.payrollPeriod " +
           "ORDER BY pr.payrollPeriod DESC")
    List<Object[]> getPayrollComparisonBetweenPeriods(
        @Param("startPeriod") YearMonth startPeriod,
        @Param("endPeriod") YearMonth endPeriod
    );

    /**
     * Trouve les enregistrements en attente de traitement
     */
    @Query("SELECT pr FROM PayrollRecord pr " +
           "WHERE pr.status IN ('DRAFT', 'CALCULATED', 'REVIEWED') " +
           "ORDER BY pr.payrollPeriod ASC, pr.employee.employeeNumber ASC")
    List<PayrollRecord> findPendingPayrollRecords();

    /**
     * Vérifie si un enregistrement de paie existe pour un employé et une période
     */
    boolean existsByEmployeeIdAndPayrollPeriod(Long employeeId, YearMonth payrollPeriod);

    /**
     * Compte les enregistrements par statut pour une période
     */
    @Query("SELECT pr.status, COUNT(pr) FROM PayrollRecord pr " +
           "WHERE pr.payrollPeriod = :period " +
           "GROUP BY pr.status")
    List<Object[]> countRecordsByStatusForPeriod(@Param("period") YearMonth period);

    /**
     * Calcule les primes totales distribuées pour une période
     */
    @Query("SELECT SUM(pr.performanceBonus + pr.otherAllowances) FROM PayrollRecord pr " +
           "WHERE pr.payrollPeriod = :period " +
           "AND pr.status IN ('PROCESSED', 'PAID')")
    BigDecimal calculateTotalBonusesForPeriod(@Param("period") YearMonth period);

    /**
     * Analyse des retards et absences impactant la paie
     */
    @Query("SELECT pr.employee.id, " +
           "pr.employee.firstName, " +
           "pr.employee.lastName, " +
           "pr.absenceDeduction, " +
           "pr.delayPenalty " +
           "FROM PayrollRecord pr " +
           "WHERE pr.payrollPeriod = :period " +
           "AND (pr.absenceDeduction > 0 OR pr.delayPenalty > 0) " +
           "AND pr.status IN ('PROCESSED', 'PAID') " +
           "ORDER BY (pr.absenceDeduction + pr.delayPenalty) DESC")
    List<Object[]> getAttendanceImpactAnalysisForPeriod(@Param("period") YearMonth period);

    /**
     * Requête native optimisée pour les rapports annuels
     */
    @Query(value = "SELECT " +
                   "EXTRACT(MONTH FROM payroll_period) as month, " +
                   "COUNT(*) as employee_count, " +
                   "SUM(gross_salary) as total_gross, " +
                   "SUM(net_salary) as total_net, " +
                   "SUM(income_tax) as total_tax, " +
                   "SUM(total_social_contributions) as total_social " +
                   "FROM payroll_records " +
                   "WHERE EXTRACT(YEAR FROM payroll_period) = :year " +
                   "AND status IN ('PROCESSED', 'PAID') " +
                   "GROUP BY EXTRACT(MONTH FROM payroll_period) " +
                   "ORDER BY month", 
           nativeQuery = true)
    List<Object[]> getAnnualPayrollSummary(@Param("year") int year);

    /**
     * Calcule les indicateurs RH pour le tableau de bord
     */
    @Query("SELECT " +
           "COUNT(DISTINCT pr.employee.id) as activeEmployees, " +
           "AVG(pr.grossSalary) as avgGrossSalary, " +
           "AVG(pr.netSalary) as avgNetSalary, " +
           "SUM(pr.overtimeHours) as totalOvertimeHours, " +
           "COUNT(CASE WHEN pr.overtimeHours > 0 THEN 1 END) as employeesWithOvertime " +
           "FROM PayrollRecord pr " +
           "WHERE pr.payrollPeriod = :period " +
           "AND pr.status IN ('PROCESSED', 'PAID')")
    Object[] getHRDashboardMetricsForPeriod(@Param("period") YearMonth period);

    /**
     * Trouve les enregistrements modifiés récemment (pour audit)
     */
    @Query("SELECT pr FROM PayrollRecord pr " +
           "WHERE pr.updatedAt > pr.createdAt " +
           "AND pr.updatedAt >= :since " +
           "ORDER BY pr.updatedAt DESC")
    List<PayrollRecord> findRecentlyModifiedRecords(@Param("since") YearMonth since);
}