package com.bantuops.backend.repository;

import com.bantuops.backend.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.QueryHint;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * Repository optimisé pour les employés avec requêtes performantes
 */
@Repository
public interface OptimizedEmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    /**
     * Requête optimisée avec fetch join pour éviter N+1
     */
    @Query("SELECT DISTINCT e FROM Employee e " +
           "LEFT JOIN FETCH e.attendanceRecords ar " +
           "LEFT JOIN FETCH e.payrollRecords pr " +
           "WHERE e.id = :employeeId")
    @QueryHints({
        @QueryHint(name = "org.hibernate.cacheable", value = "true"),
        @QueryHint(name = "org.hibernate.cacheMode", value = "NORMAL")
    })
    Optional<Employee> findByIdWithDetails(@Param("employeeId") Long employeeId);

    /**
     * Requête optimisée pour les employés actifs avec pagination
     */
    @Query("SELECT e FROM Employee e " +
           "WHERE e.employmentInfo.isActive = true " +
           "ORDER BY e.employeeNumber ASC")
    @QueryHints({
        @QueryHint(name = "org.hibernate.cacheable", value = "true"),
        @QueryHint(name = "org.hibernate.fetchSize", value = "50")
    })
    Page<Employee> findActiveEmployeesOptimized(Pageable pageable);

    /**
     * Requête optimisée pour les employés par département
     */
    @Query("SELECT e FROM Employee e " +
           "WHERE e.employmentInfo.department = :department " +
           "AND e.employmentInfo.isActive = :isActive " +
           "ORDER BY e.personalInfo.lastName, e.personalInfo.firstName")
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    List<Employee> findByDepartmentAndActiveStatus(@Param("department") String department, 
                                                  @Param("isActive") Boolean isActive);

    /**
     * Requête optimisée avec données d'assiduité pour une période
     */
    @Query("SELECT e FROM Employee e " +
           "LEFT JOIN FETCH e.attendanceRecords ar " +
           "WHERE e.employmentInfo.isActive = true " +
           "AND (ar.workDate IS NULL OR ar.workDate BETWEEN :startDate AND :endDate) " +
           "ORDER BY e.employeeNumber")
    @QueryHints({
        @QueryHint(name = "org.hibernate.fetchSize", value = "100"),
        @QueryHint(name = "org.hibernate.readOnly", value = "true")
    })
    List<Employee> findActiveEmployeesWithAttendanceForPeriod(@Param("startDate") LocalDate startDate,
                                                             @Param("endDate") LocalDate endDate);

    /**
     * Requête native optimisée pour les statistiques d'employés
     */
    @Query(value = "SELECT " +
                   "e.department, " +
                   "COUNT(*) as total_employees, " +
                   "COUNT(CASE WHEN e.is_active = true THEN 1 END) as active_employees, " +
                   "AVG(CAST(pgp_sym_decrypt(e.base_salary::bytea, :encryptionKey) AS DECIMAL)) as avg_salary " +
                   "FROM employees e " +
                   "GROUP BY e.department " +
                   "ORDER BY e.department", 
           nativeQuery = true)
    List<Object[]> getEmployeeStatisticsByDepartment(@Param("encryptionKey") String encryptionKey);

    /**
     * Requête optimisée pour les employés avec calculs de paie récents
     */
    @Query("SELECT DISTINCT e FROM Employee e " +
           "LEFT JOIN FETCH e.payrollRecords pr " +
           "WHERE e.employmentInfo.isActive = true " +
           "AND (pr.id IS NULL OR (pr.periodYear = :year AND pr.periodMonth = :month)) " +
           "ORDER BY e.employeeNumber")
    @QueryHints(@QueryHint(name = "org.hibernate.fetchSize", value = "50"))
    List<Employee> findActiveEmployeesWithPayrollForPeriod(@Param("year") Integer year, 
                                                          @Param("month") Integer month);

    /**
     * Requête de comptage optimisée
     */
    @Query("SELECT COUNT(e) FROM Employee e " +
           "WHERE (:department IS NULL OR e.employmentInfo.department = :department) " +
           "AND (:isActive IS NULL OR e.employmentInfo.isActive = :isActive)")
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    Long countByDepartmentAndActiveStatus(@Param("department") String department, 
                                         @Param("isActive") Boolean isActive);

    /**
     * Requête optimisée pour la recherche par numéro d'employé
     */
    @Query("SELECT e FROM Employee e " +
           "WHERE e.employeeNumber LIKE :employeeNumber% " +
           "AND e.employmentInfo.isActive = true " +
           "ORDER BY e.employeeNumber")
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    List<Employee> findByEmployeeNumberStartingWith(@Param("employeeNumber") String employeeNumber);

    /**
     * Requête batch pour plusieurs IDs
     */
    @Query("SELECT e FROM Employee e " +
           "WHERE e.id IN :employeeIds " +
           "ORDER BY e.employeeNumber")
    @QueryHints(@QueryHint(name = "org.hibernate.fetchSize", value = "100"))
    List<Employee> findByIdsBatch(@Param("employeeIds") List<Long> employeeIds);

    // Spécifications pour requêtes dynamiques complexes
    class Specs {
        
        public static Specification<Employee> isActive() {
            return (root, query, criteriaBuilder) -> 
                criteriaBuilder.isTrue(root.get("employmentInfo").get("isActive"));
        }
        
        public static Specification<Employee> hasDepart

ment(String department) {
            return (root, query, criteriaBuilder) -> {
                if (department == null || department.trim().isEmpty()) {
                    return criteriaBuilder.conjunction();
                }
                return criteriaBuilder.equal(root.get("employmentInfo").get("department"), department);
            };
        }
        
        public static Specification<Employee> hasPosition(String position) {
            return (root, query, criteriaBuilder) -> {
                if (position == null || position.trim().isEmpty()) {
                    return criteriaBuilder.conjunction();
                }
                return criteriaBuilder.equal(root.get("employmentInfo").get("position"), position);
            };
        }
        
        public static Specification<Employee> hasContractType(String contractType) {
            return (root, query, criteriaBuilder) -> {
                if (contractType == null || contractType.trim().isEmpty()) {
                    return criteriaBuilder.conjunction();
                }
                return criteriaBuilder.equal(root.get("employmentInfo").get("contractType"), contractType);
            };
        }
        
        public static Specification<Employee> hiredBetween(LocalDate startDate, LocalDate endDate) {
            return (root, query, criteriaBuilder) -> {
                if (startDate == null && endDate == null) {
                    return criteriaBuilder.conjunction();
                }
                if (startDate == null) {
                    return criteriaBuilder.lessThanOrEqualTo(
                        root.get("employmentInfo").get("hireDate"), endDate);
                }
                if (endDate == null) {
                    return criteriaBuilder.greaterThanOrEqualTo(
                        root.get("employmentInfo").get("hireDate"), startDate);
                }
                return criteriaBuilder.between(
                    root.get("employmentInfo").get("hireDate"), startDate, endDate);
            };
        }
        
        public static Specification<Employee> hasAttendanceInPeriod(LocalDate startDate, LocalDate endDate) {
            return (root, query, criteriaBuilder) -> {
                if (startDate == null || endDate == null) {
                    return criteriaBuilder.conjunction();
                }
                
                // Sous-requête pour vérifier l'existence d'enregistrements d'assiduité
                var subquery = query.subquery(Long.class);
                var attendanceRoot = subquery.from(root.getModel().getBindableJavaType());
                var attendanceJoin = attendanceRoot.join("attendanceRecords");
                
                subquery.select(criteriaBuilder.count(attendanceJoin))
                       .where(criteriaBuilder.and(
                           criteriaBuilder.equal(attendanceRoot.get("id"), root.get("id")),
                           criteriaBuilder.between(attendanceJoin.get("workDate"), startDate, endDate)
                       ));
                
                return criteriaBuilder.greaterThan(subquery, 0L);
            };
        }
        
        public static Specification<Employee> hasPayrollInPeriod(YearMonth period) {
            return (root, query, criteriaBuilder) -> {
                if (period == null) {
                    return criteriaBuilder.conjunction();
                }
                
                // Sous-requête pour vérifier l'existence d'enregistrements de paie
                var subquery = query.subquery(Long.class);
                var payrollRoot = subquery.from(root.getModel().getBindableJavaType());
                var payrollJoin = payrollRoot.join("payrollRecords");
                
                subquery.select(criteriaBuilder.count(payrollJoin))
                       .where(criteriaBuilder.and(
                           criteriaBuilder.equal(payrollRoot.get("id"), root.get("id")),
                           criteriaBuilder.equal(payrollJoin.get("periodYear"), period.getYear()),
                           criteriaBuilder.equal(payrollJoin.get("periodMonth"), period.getMonthValue())
                       ));
                
                return criteriaBuilder.greaterThan(subquery, 0L);
            };
        }
        
        public static Specification<Employee> salaryBetween(Double minSalary, Double maxSalary) {
            return (root, query, criteriaBuilder) -> {
                if (minSalary == null && maxSalary == null) {
                    return criteriaBuilder.conjunction();
                }
                
                // Note: Pour les salaires chiffrés, cette spécification nécessiterait
                // une approche différente avec des requêtes natives
                var salaryPath = root.get("employmentInfo").get("baseSalary");
                
                if (minSalary == null) {
                    return criteriaBuilder.lessThanOrEqualTo(salaryPath, maxSalary);
                }
                if (maxSalary == null) {
                    return criteriaBuilder.greaterThanOrEqualTo(salaryPath, minSalary);
                }
                return criteriaBuilder.between(salaryPath, minSalary, maxSalary);
            };
        }
        
        public static Specification<Employee> nameContains(String searchTerm) {
            return (root, query, criteriaBuilder) -> {
                if (searchTerm == null || searchTerm.trim().isEmpty()) {
                    return criteriaBuilder.conjunction();
                }
                
                String likePattern = "%" + searchTerm.toLowerCase() + "%";
                
                // Note: Pour les noms chiffrés, cette recherche nécessiterait
                // une approche différente avec des requêtes natives
                return criteriaBuilder.or(
                    criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("personalInfo").get("firstName")), 
                        likePattern),
                    criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("personalInfo").get("lastName")), 
                        likePattern),
                    criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("employeeNumber")), 
                        likePattern)
                );
            };
        }
        
        public static Specification<Employee> createdBetween(LocalDate startDate, LocalDate endDate) {
            return (root, query, criteriaBuilder) -> {
                if (startDate == null && endDate == null) {
                    return criteriaBuilder.conjunction();
                }
                
                var createdAtPath = root.get("createdAt");
                
                if (startDate == null) {
                    return criteriaBuilder.lessThanOrEqualTo(createdAtPath, endDate.atStartOfDay());
                }
                if (endDate == null) {
                    return criteriaBuilder.greaterThanOrEqualTo(createdAtPath, startDate.atStartOfDay());
                }
                return criteriaBuilder.between(createdAtPath, 
                    startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay());
            };
        }
    }
}