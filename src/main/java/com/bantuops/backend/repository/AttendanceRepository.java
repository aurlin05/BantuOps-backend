package com.bantuops.backend.repository;

import com.bantuops.backend.entity.AttendanceRecord;
import com.bantuops.backend.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository pour AttendanceRecord avec filtres de performance
 * Conforme aux exigences 6.1, 6.2, 6.3, 6.4, 6.5 pour l'optimisation des requêtes d'assiduité
 */
@Repository
public interface AttendanceRepository extends JpaRepository<AttendanceRecord, Long>, JpaSpecificationExecutor<AttendanceRecord> {

    /**
     * Trouve l'enregistrement d'assiduité d'un employé pour une date donnée
     */
    Optional<AttendanceRecord> findByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);

    /**
     * Trouve les enregistrements d'assiduité d'un employé pour une période
     */
    @Query("SELECT ar FROM AttendanceRecord ar " +
           "WHERE ar.employee.id = :employeeId " +
           "AND ar.workDate BETWEEN :startDate AND :endDate " +
           "ORDER BY ar.workDate DESC")
    List<AttendanceRecord> findByEmployeeIdAndWorkDateBetween(
        @Param("employeeId") Long employeeId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Trouve les enregistrements d'assiduité avec pagination pour un employé
     */
    Page<AttendanceRecord> findByEmployeeIdOrderByWorkDateDesc(Long employeeId, Pageable pageable);

    /**
     * Trouve tous les retards pour une période donnée
     */
    @Query("SELECT ar FROM AttendanceRecord ar " +
           "WHERE ar.workDate BETWEEN :startDate AND :endDate " +
           "AND ar.delayMinutes > 0 " +
           "ORDER BY ar.workDate DESC, ar.delayMinutes DESC")
    List<AttendanceRecord> findDelaysInPeriod(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Trouve les absences pour une période donnée
     */
    @Query("SELECT ar FROM AttendanceRecord ar " +
           "WHERE ar.workDate BETWEEN :startDate AND :endDate " +
           "AND ar.attendanceType IN ('ABSENT', 'UNAUTHORIZED_ABSENCE') " +
           "ORDER BY ar.workDate DESC")
    List<AttendanceRecord> findAbsencesInPeriod(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Trouve les enregistrements en attente d'approbation
     */
    @Query("SELECT ar FROM AttendanceRecord ar " +
           "WHERE ar.status = 'PENDING' " +
           "ORDER BY ar.workDate ASC")
    List<AttendanceRecord> findPendingApprovals();

    /**
     * Trouve les enregistrements en attente d'approbation avec pagination
     */
    Page<AttendanceRecord> findByStatusOrderByWorkDateAsc(AttendanceRecord.AttendanceStatus status, Pageable pageable);

    /**
     * Statistiques d'assiduité par employé pour une période
     */
    @Query("SELECT ar.employee.id, " +
           "COUNT(ar) as totalDays, " +
           "SUM(CASE WHEN ar.delayMinutes > 0 THEN 1 ELSE 0 END) as delayDays, " +
           "SUM(CASE WHEN ar.attendanceType = 'ABSENT' THEN 1 ELSE 0 END) as absentDays, " +
           "AVG(ar.delayMinutes) as avgDelayMinutes, " +
           "SUM(ar.totalHoursWorked) as totalHoursWorked " +
           "FROM AttendanceRecord ar " +
           "WHERE ar.workDate BETWEEN :startDate AND :endDate " +
           "GROUP BY ar.employee.id")
    List<Object[]> getAttendanceStatsByEmployeeForPeriod(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Trouve les employés avec le plus de retards dans une période
     */
    @Query("SELECT ar.employee, COUNT(ar) as delayCount " +
           "FROM AttendanceRecord ar " +
           "WHERE ar.workDate BETWEEN :startDate AND :endDate " +
           "AND ar.delayMinutes > 0 " +
           "GROUP BY ar.employee " +
           "ORDER BY delayCount DESC")
    List<Object[]> findEmployeesWithMostDelays(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        Pageable pageable
    );

    /**
     * Calcule les heures supplémentaires par employé pour une période
     */
    @Query("SELECT ar.employee.id, SUM(ar.overtimeHours) " +
           "FROM AttendanceRecord ar " +
           "WHERE ar.workDate BETWEEN :startDate AND :endDate " +
           "AND ar.overtimeHours > 0 " +
           "GROUP BY ar.employee.id")
    List<Object[]> calculateOvertimeByEmployeeForPeriod(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Trouve les enregistrements par type d'assiduité pour une période
     */
    @Query("SELECT ar FROM AttendanceRecord ar " +
           "WHERE ar.workDate BETWEEN :startDate AND :endDate " +
           "AND ar.attendanceType = :attendanceType " +
           "ORDER BY ar.workDate DESC")
    List<AttendanceRecord> findByAttendanceTypeInPeriod(
        @Param("attendanceType") AttendanceRecord.AttendanceType attendanceType,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Trouve les enregistrements avec justification requise
     */
    @Query("SELECT ar FROM AttendanceRecord ar " +
           "WHERE ar.attendanceType IN ('LATE', 'ABSENT', 'UNAUTHORIZED_ABSENCE') " +
           "AND (ar.justification IS NULL OR ar.justification = '') " +
           "AND ar.workDate BETWEEN :startDate AND :endDate " +
           "ORDER BY ar.workDate DESC")
    List<AttendanceRecord> findRecordsRequiringJustification(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Compte les jours travaillés par employé pour une période
     */
    @Query("SELECT COUNT(ar) FROM AttendanceRecord ar " +
           "WHERE ar.employee.id = :employeeId " +
           "AND ar.workDate BETWEEN :startDate AND :endDate " +
           "AND ar.attendanceType IN ('PRESENT', 'LATE')")
    long countWorkedDaysByEmployeeInPeriod(
        @Param("employeeId") Long employeeId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Trouve les enregistrements avec des heures supplémentaires
     */
    @Query("SELECT ar FROM AttendanceRecord ar " +
           "WHERE ar.workDate BETWEEN :startDate AND :endDate " +
           "AND ar.overtimeHours > 0 " +
           "ORDER BY ar.overtimeHours DESC")
    List<AttendanceRecord> findRecordsWithOvertime(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Statistiques globales d'assiduité pour une période
     */
    @Query("SELECT " +
           "COUNT(ar) as totalRecords, " +
           "SUM(CASE WHEN ar.attendanceType = 'PRESENT' THEN 1 ELSE 0 END) as presentDays, " +
           "SUM(CASE WHEN ar.delayMinutes > 0 THEN 1 ELSE 0 END) as lateDays, " +
           "SUM(CASE WHEN ar.attendanceType = 'ABSENT' THEN 1 ELSE 0 END) as absentDays, " +
           "AVG(ar.delayMinutes) as avgDelayMinutes, " +
           "SUM(ar.overtimeHours) as totalOvertimeHours " +
           "FROM AttendanceRecord ar " +
           "WHERE ar.workDate BETWEEN :startDate AND :endDate")
    Object[] getGlobalAttendanceStatsForPeriod(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Trouve les enregistrements d'un département pour une période
     */
    @Query("SELECT ar FROM AttendanceRecord ar " +
           "JOIN ar.employee e " +
           "WHERE e.department = :department " +
           "AND ar.workDate BETWEEN :startDate AND :endDate " +
           "ORDER BY ar.workDate DESC")
    List<AttendanceRecord> findByDepartmentInPeriod(
        @Param("department") String department,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Vérifie s'il existe déjà un enregistrement pour un employé à une date
     */
    boolean existsByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);

    /**
     * Trouve les enregistrements modifiés récemment (pour audit)
     */
    @Query("SELECT ar FROM AttendanceRecord ar " +
           "WHERE ar.updatedAt > ar.createdAt " +
           "AND ar.updatedAt >= :since " +
           "ORDER BY ar.updatedAt DESC")
    List<AttendanceRecord> findRecentlyModifiedRecords(@Param("since") LocalDate since);

    /**
     * Requête native optimisée pour les rapports de performance
     */
    @Query(value = "SELECT e.department, " +
                   "COUNT(*) as total_records, " +
                   "AVG(ar.delay_minutes) as avg_delay, " +
                   "SUM(CASE WHEN ar.attendance_type = 'ABSENT' THEN 1 ELSE 0 END) as total_absences " +
                   "FROM attendance_records ar " +
                   "JOIN employees e ON ar.employee_id = e.id " +
                   "WHERE ar.work_date BETWEEN :startDate AND :endDate " +
                   "GROUP BY e.department " +
                   "ORDER BY avg_delay DESC", 
           nativeQuery = true)
    List<Object[]> getDepartmentAttendanceStats(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
}