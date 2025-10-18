package com.bantuops.backend.repository;

import com.bantuops.backend.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository pour Employee avec requêtes fetch optimisées
 * Conforme aux exigences 6.1, 6.2, 6.3, 6.4, 6.5 pour les performances
 */
@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    /**
     * Trouve un employé par son numéro d'employé
     */
    Optional<Employee> findByEmployeeNumber(String employeeNumber);

    /**
     * Trouve un employé par son email chiffré
     */
    Optional<Employee> findByEmail(String email);

    /**
     * Trouve un employé par son numéro d'identité nationale
     */
    Optional<Employee> findByNationalId(String nationalId);

    /**
     * Trouve un employé avec ses enregistrements d'assiduité pour une période donnée
     * Optimisé avec LEFT JOIN FETCH pour éviter le problème N+1
     */
    @Query("SELECT DISTINCT e FROM Employee e " +
           "LEFT JOIN FETCH e.attendanceRecords ar " +
           "WHERE e.id = :employeeId " +
           "AND (ar.workDate IS NULL OR ar.workDate BETWEEN :startDate AND :endDate)")
    Optional<Employee> findByIdWithAttendanceRecords(
        @Param("employeeId") Long employeeId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Trouve un employé avec ses enregistrements de paie pour une période donnée
     */
    @Query("SELECT DISTINCT e FROM Employee e " +
           "LEFT JOIN FETCH e.payrollRecords pr " +
           "WHERE e.id = :employeeId " +
           "AND (pr.payrollPeriod IS NULL OR " +
           "     (YEAR(pr.payrollPeriod) = :year AND MONTH(pr.payrollPeriod) BETWEEN :startMonth AND :endMonth))")
    Optional<Employee> findByIdWithPayrollRecords(
        @Param("employeeId") Long employeeId,
        @Param("year") int year,
        @Param("startMonth") int startMonth,
        @Param("endMonth") int endMonth
    );

    /**
     * Trouve tous les employés actifs avec pagination
     */
    @Query("SELECT e FROM Employee e WHERE e.isActive = true ORDER BY e.employeeNumber")
    Page<Employee> findActiveEmployees(Pageable pageable);

    /**
     * Trouve les employés par département avec pagination
     */
    @Query("SELECT e FROM Employee e WHERE e.department = :department AND e.isActive = true ORDER BY e.lastName, e.firstName")
    Page<Employee> findByDepartmentAndIsActiveTrue(@Param("department") String department, Pageable pageable);

    /**
     * Trouve les employés par poste
     */
    List<Employee> findByPositionAndIsActiveTrue(String position);

    /**
     * Trouve les employés embauchés dans une période donnée
     */
    @Query("SELECT e FROM Employee e WHERE e.hireDate BETWEEN :startDate AND :endDate AND e.isActive = true ORDER BY e.hireDate DESC")
    List<Employee> findByHireDateBetweenAndIsActiveTrue(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Recherche d'employés par nom (prénom ou nom de famille)
     * Note: La recherche se fait sur les données chiffrées, donc exacte uniquement
     */
    @Query("SELECT e FROM Employee e WHERE " +
           "(LOWER(e.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           " LOWER(e.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
           "AND e.isActive = true")
    Page<Employee> searchByName(@Param("searchTerm") String searchTerm, Pageable pageable);

    /**
     * Compte le nombre d'employés actifs par département
     */
    @Query("SELECT e.department, COUNT(e) FROM Employee e WHERE e.isActive = true GROUP BY e.department")
    List<Object[]> countActiveEmployeesByDepartment();

    /**
     * Trouve les employés avec un salaire dans une fourchette donnée
     */
    @Query("SELECT e FROM Employee e WHERE e.baseSalary BETWEEN :minSalary AND :maxSalary AND e.isActive = true")
    List<Employee> findByBaseSalaryBetweenAndIsActiveTrue(
        @Param("minSalary") String minSalary, // Chiffré
        @Param("maxSalary") String maxSalary  // Chiffré
    );

    /**
     * Trouve les employés sans enregistrement d'assiduité pour une date donnée
     */
    @Query("SELECT e FROM Employee e WHERE e.isActive = true " +
           "AND e.id NOT IN (SELECT ar.employee.id FROM AttendanceRecord ar WHERE ar.workDate = :date)")
    List<Employee> findEmployeesWithoutAttendanceForDate(@Param("date") LocalDate date);

    /**
     * Trouve les employés avec des retards fréquents (plus de X retards dans le mois)
     */
    @Query("SELECT DISTINCT e FROM Employee e " +
           "JOIN e.attendanceRecords ar " +
           "WHERE ar.workDate BETWEEN :startDate AND :endDate " +
           "AND ar.delayMinutes > 0 " +
           "GROUP BY e.id " +
           "HAVING COUNT(ar.id) > :maxDelays")
    List<Employee> findEmployeesWithFrequentDelays(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        @Param("maxDelays") long maxDelays
    );

    /**
     * Requête native optimisée pour la pagination des employés actifs
     * Utilisée pour de très gros volumes
     */
    @Query(value = "SELECT * FROM employees e " +
                   "WHERE e.is_active = true " +
                   "ORDER BY e.employee_number " +
                   "LIMIT :limit OFFSET :offset", 
           nativeQuery = true)
    List<Employee> findActiveEmployeesPaginated(
        @Param("limit") int limit, 
        @Param("offset") int offset
    );

    /**
     * Compte le nombre total d'employés actifs
     */
    long countByIsActiveTrue();

    /**
     * Vérifie si un numéro d'employé existe déjà
     */
    boolean existsByEmployeeNumber(String employeeNumber);

    /**
     * Vérifie si un email existe déjà
     */
    boolean existsByEmail(String email);
    
    /**
     * Vérifie si un email existe déjà (via PersonalInfo)
     */
    boolean existsByPersonalInfoEmail(String email);

    /**
     * Vérifie si un numéro d'identité nationale existe déjà
     */
    boolean existsByNationalId(String nationalId);

    /**
     * Trouve les employés dont l'anniversaire d'embauche est aujourd'hui
     */
    @Query("SELECT e FROM Employee e WHERE " +
           "MONTH(e.hireDate) = MONTH(CURRENT_DATE) AND " +
           "DAY(e.hireDate) = DAY(CURRENT_DATE) AND " +
           "e.isActive = true")
    List<Employee> findEmployeesWithHireAnniversaryToday();

    /**
     * Trouve les employés par type de contrat
     */
    List<Employee> findByContractTypeAndIsActiveTrue(Employee.ContractType contractType);

    /**
     * Suppression logique d'un employé (désactivation)
     */
    @Query("UPDATE Employee e SET e.isActive = false WHERE e.id = :employeeId")
    void deactivateEmployee(@Param("employeeId") Long employeeId);
    
    /**
     * Recherche par nom ou prénom (via PersonalInfo)
     */
    Page<Employee> findByPersonalInfoFirstNameContainingIgnoreCaseOrPersonalInfoLastNameContainingIgnoreCase(
        String firstName, String lastName, Pageable pageable);
    
    /**
     * Trouve par département (via EmploymentInfo)
     */
    Page<Employee> findByEmploymentInfoDepartment(String department, Pageable pageable);
    
    /**
     * Trouve par statut actif (via EmploymentInfo)
     */
    Page<Employee> findByEmploymentInfoIsActive(Boolean isActive, Pageable pageable);
    
    /**
     * Compte par statut actif (via EmploymentInfo)
     */
    long countByEmploymentInfoIsActive(boolean isActive);
    
    /**
     * Compte par département
     */
    @Query("SELECT e.employmentInfo.department, COUNT(e) FROM Employee e WHERE e.employmentInfo.isActive = true GROUP BY e.employmentInfo.department")
    List<Object[]> countByDepartment();
}