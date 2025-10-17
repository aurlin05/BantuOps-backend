package com.bantuops.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service pour l'optimisation des requêtes de base de données
 * Fournit des requêtes optimisées et des stratégies de pagination efficaces
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseQueryOptimizer {

    private final EntityManager entityManager;
    
    // Cache des requêtes préparées pour éviter la recompilation
    private final Map<String, String> preparedQueries = new ConcurrentHashMap<>();

    /**
     * Requête optimisée pour récupérer les employés avec pagination
     */
    public List<Object[]> findEmployeesOptimized(Pageable pageable, String department, Boolean isActive) {
        String queryKey = "employees_optimized";
        String jpql = preparedQueries.computeIfAbsent(queryKey, k -> 
            "SELECT e.id, e.employeeNumber, e.personalInfo.firstName, e.personalInfo.lastName, " +
            "e.employmentInfo.position, e.employmentInfo.department, e.employmentInfo.baseSalary, " +
            "e.employmentInfo.isActive, e.createdAt " +
            "FROM Employee e " +
            "WHERE (:department IS NULL OR e.employmentInfo.department = :department) " +
            "AND (:isActive IS NULL OR e.employmentInfo.isActive = :isActive) " +
            "ORDER BY e.employeeNumber ASC"
        );

        TypedQuery<Object[]> query = entityManager.createQuery(jpql, Object[].class);
        query.setParameter("department", department);
        query.setParameter("isActive", isActive);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        log.debug("Executing optimized employee query with pagination: offset={}, size={}", 
                 pageable.getOffset(), pageable.getPageSize());
        
        return query.getResultList();
    }

    /**
     * Requête optimisée pour les calculs de paie en masse
     */
    public List<Object[]> findPayrollDataForBulkCalculation(YearMonth period, List<Long> employeeIds) {
        String queryKey = "payroll_bulk_data";
        String jpql = preparedQueries.computeIfAbsent(queryKey, k ->
            "SELECT e.id, e.employeeNumber, e.personalInfo.firstName, e.personalInfo.lastName, " +
            "e.employmentInfo.baseSalary, e.employmentInfo.contractType, e.employmentInfo.position, " +
            "COUNT(ar.id) as attendanceCount, " +
            "SUM(CASE WHEN ar.type = 'LATE' THEN ar.delayMinutes ELSE 0 END) as totalDelayMinutes, " +
            "SUM(CASE WHEN ar.type = 'ABSENT' THEN 1 ELSE 0 END) as absentDays " +
            "FROM Employee e " +
            "LEFT JOIN e.attendanceRecords ar ON ar.workDate BETWEEN :startDate AND :endDate " +
            "WHERE e.id IN :employeeIds AND e.employmentInfo.isActive = true " +
            "GROUP BY e.id, e.employeeNumber, e.personalInfo.firstName, e.personalInfo.lastName, " +
            "e.employmentInfo.baseSalary, e.employmentInfo.contractType, e.employmentInfo.position " +
            "ORDER BY e.employeeNumber"
        );

        TypedQuery<Object[]> query = entityManager.createQuery(jpql, Object[].class);
        query.setParameter("employeeIds", employeeIds);
        query.setParameter("startDate", period.atDay(1));
        query.setParameter("endDate", period.atEndOfMonth());

        log.debug("Executing bulk payroll data query for {} employees in period {}", 
                 employeeIds.size(), period);
        
        return query.getResultList();
    }

    /**
     * Requête native optimisée pour les rapports financiers
     */
    public List<Object[]> generateFinancialReportData(LocalDate startDate, LocalDate endDate) {
        String nativeQuery = 
            "SELECT " +
            "    DATE_TRUNC('month', i.created_at) as month, " +
            "    COUNT(i.id) as invoice_count, " +
            "    SUM(CAST(pgp_sym_decrypt(i.total_amount::bytea, :encryptionKey) AS DECIMAL)) as total_amount, " +
            "    SUM(CAST(pgp_sym_decrypt(i.vat_amount::bytea, :encryptionKey) AS DECIMAL)) as total_vat, " +
            "    AVG(CAST(pgp_sym_decrypt(i.total_amount::bytea, :encryptionKey) AS DECIMAL)) as avg_amount " +
            "FROM invoices i " +
            "WHERE i.created_at BETWEEN :startDate AND :endDate " +
            "    AND i.status = 'PAID' " +
            "GROUP BY DATE_TRUNC('month', i.created_at) " +
            "ORDER BY month DESC";

        Query query = entityManager.createNativeQuery(nativeQuery);
        query.setParameter("startDate", startDate);
        query.setParameter("endDate", endDate);
        query.setParameter("encryptionKey", getEncryptionKey());

        log.debug("Executing financial report query from {} to {}", startDate, endDate);
        
        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();
        return results;
    }

    /**
     * Requête optimisée pour les statistiques d'assiduité
     */
    public List<Object[]> getAttendanceStatistics(LocalDate startDate, LocalDate endDate, String department) {
        String jpql = 
            "SELECT e.employmentInfo.department, " +
            "COUNT(ar.id) as total_records, " +
            "SUM(CASE WHEN ar.type = 'PRESENT' THEN 1 ELSE 0 END) as present_days, " +
            "SUM(CASE WHEN ar.type = 'LATE' THEN 1 ELSE 0 END) as late_days, " +
            "SUM(CASE WHEN ar.type = 'ABSENT' THEN 1 ELSE 0 END) as absent_days, " +
            "AVG(ar.delayMinutes) as avg_delay_minutes " +
            "FROM Employee e " +
            "JOIN e.attendanceRecords ar " +
            "WHERE ar.workDate BETWEEN :startDate AND :endDate " +
            "AND (:department IS NULL OR e.employmentInfo.department = :department) " +
            "AND e.employmentInfo.isActive = true " +
            "GROUP BY e.employmentInfo.department " +
            "ORDER BY e.employmentInfo.department";

        TypedQuery<Object[]> query = entityManager.createQuery(jpql, Object[].class);
        query.setParameter("startDate", startDate);
        query.setParameter("endDate", endDate);
        query.setParameter("department", department);

        log.debug("Executing attendance statistics query for department: {}", department);
        
        return query.getResultList();
    }

    /**
     * Requête optimisée avec index hints pour les gros volumes
     */
    public List<Object[]> findLargeDatasetOptimized(String entityType, Map<String, Object> filters, 
                                                   Pageable pageable) {
        StringBuilder queryBuilder = new StringBuilder();
        
        switch (entityType.toUpperCase()) {
            case "PAYROLL":
                queryBuilder.append(buildPayrollLargeDatasetQuery(filters));
                break;
            case "ATTENDANCE":
                queryBuilder.append(buildAttendanceLargeDatasetQuery(filters));
                break;
            case "INVOICE":
                queryBuilder.append(buildInvoiceLargeDatasetQuery(filters));
                break;
            default:
                throw new IllegalArgumentException("Unsupported entity type: " + entityType);
        }

        Query query = entityManager.createNativeQuery(queryBuilder.toString());
        
        // Définir les paramètres dynamiquement
        filters.forEach(query::setParameter);
        
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        log.debug("Executing large dataset query for entity: {} with {} filters", 
                 entityType, filters.size());
        
        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();
        return results;
    }

    /**
     * Requête de comptage optimisée pour la pagination
     */
    public Long countOptimized(String entityType, Map<String, Object> filters) {
        String countQuery = buildCountQuery(entityType, filters);
        
        Query query = entityManager.createQuery(countQuery);
        filters.forEach(query::setParameter);
        
        return (Long) query.getSingleResult();
    }

    /**
     * Requête d'agrégation optimisée pour les tableaux de bord
     */
    public List<Object[]> getDashboardAggregates(YearMonth period) {
        String nativeQuery = 
            "WITH monthly_stats AS ( " +
            "    SELECT " +
            "        COUNT(DISTINCT e.id) as active_employees, " +
            "        SUM(CASE WHEN pr.id IS NOT NULL THEN " +
            "            CAST(pgp_sym_decrypt(pr.net_salary::bytea, :encryptionKey) AS DECIMAL) " +
            "            ELSE 0 END) as total_payroll, " +
            "        COUNT(DISTINCT i.id) as total_invoices, " +
            "        SUM(CASE WHEN i.id IS NOT NULL THEN " +
            "            CAST(pgp_sym_decrypt(i.total_amount::bytea, :encryptionKey) AS DECIMAL) " +
            "            ELSE 0 END) as total_revenue " +
            "    FROM employees e " +
            "    LEFT JOIN payroll_records pr ON pr.employee_id = e.id " +
            "        AND pr.period_year = :year AND pr.period_month = :month " +
            "    LEFT JOIN invoices i ON DATE_TRUNC('month', i.created_at) = :periodStart " +
            "    WHERE e.is_active = true " +
            ") " +
            "SELECT * FROM monthly_stats";

        Query query = entityManager.createNativeQuery(nativeQuery);
        query.setParameter("year", period.getYear());
        query.setParameter("month", period.getMonthValue());
        query.setParameter("periodStart", period.atDay(1));
        query.setParameter("encryptionKey", getEncryptionKey());

        log.debug("Executing dashboard aggregates query for period: {}", period);
        
        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();
        return results;
    }

    /**
     * Requête de recherche full-text optimisée
     */
    public List<Object[]> searchEmployeesFullText(String searchTerm, Pageable pageable) {
        String nativeQuery = 
            "SELECT e.id, e.employee_number, " +
            "pgp_sym_decrypt(e.first_name::bytea, :encryptionKey) as first_name, " +
            "pgp_sym_decrypt(e.last_name::bytea, :encryptionKey) as last_name, " +
            "e.position, e.department, " +
            "ts_rank(to_tsvector('french', " +
            "    COALESCE(pgp_sym_decrypt(e.first_name::bytea, :encryptionKey), '') || ' ' || " +
            "    COALESCE(pgp_sym_decrypt(e.last_name::bytea, :encryptionKey), '') || ' ' || " +
            "    COALESCE(e.employee_number, '') || ' ' || " +
            "    COALESCE(e.position, '') || ' ' || " +
            "    COALESCE(e.department, '') " +
            "), plainto_tsquery('french', :searchTerm)) as rank " +
            "FROM employees e " +
            "WHERE to_tsvector('french', " +
            "    COALESCE(pgp_sym_decrypt(e.first_name::bytea, :encryptionKey), '') || ' ' || " +
            "    COALESCE(pgp_sym_decrypt(e.last_name::bytea, :encryptionKey), '') || ' ' || " +
            "    COALESCE(e.employee_number, '') || ' ' || " +
            "    COALESCE(e.position, '') || ' ' || " +
            "    COALESCE(e.department, '') " +
            ") @@ plainto_tsquery('french', :searchTerm) " +
            "AND e.is_active = true " +
            "ORDER BY rank DESC, e.employee_number ASC " +
            "LIMIT :limit OFFSET :offset";

        Query query = entityManager.createNativeQuery(nativeQuery);
        query.setParameter("searchTerm", searchTerm);
        query.setParameter("encryptionKey", getEncryptionKey());
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        log.debug("Executing full-text search for term: '{}' with pagination", searchTerm);
        
        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();
        return results;
    }

    /**
     * Construction de requête pour les données de paie volumineuses
     */
    private String buildPayrollLargeDatasetQuery(Map<String, Object> filters) {
        return "SELECT /*+ USE_INDEX(payroll_records, idx_payroll_period_employee) */ " +
               "pr.id, pr.employee_id, pr.period_year, pr.period_month, " +
               "pgp_sym_decrypt(pr.gross_salary::bytea, :encryptionKey) as gross_salary, " +
               "pgp_sym_decrypt(pr.net_salary::bytea, :encryptionKey) as net_salary, " +
               "pr.created_at " +
               "FROM payroll_records pr " +
               "WHERE (:startYear IS NULL OR pr.period_year >= :startYear) " +
               "AND (:endYear IS NULL OR pr.period_year <= :endYear) " +
               "AND (:employeeId IS NULL OR pr.employee_id = :employeeId) " +
               "ORDER BY pr.period_year DESC, pr.period_month DESC, pr.employee_id";
    }

    /**
     * Construction de requête pour les données d'assiduité volumineuses
     */
    private String buildAttendanceLargeDatasetQuery(Map<String, Object> filters) {
        return "SELECT /*+ USE_INDEX(attendance_records, idx_attendance_date_employee) */ " +
               "ar.id, ar.employee_id, ar.work_date, ar.type, ar.delay_minutes, " +
               "ar.scheduled_start_time, ar.actual_start_time " +
               "FROM attendance_records ar " +
               "WHERE (:startDate IS NULL OR ar.work_date >= :startDate) " +
               "AND (:endDate IS NULL OR ar.work_date <= :endDate) " +
               "AND (:employeeId IS NULL OR ar.employee_id = :employeeId) " +
               "AND (:type IS NULL OR ar.type = :type) " +
               "ORDER BY ar.work_date DESC, ar.employee_id";
    }

    /**
     * Construction de requête pour les factures volumineuses
     */
    private String buildInvoiceLargeDatasetQuery(Map<String, Object> filters) {
        return "SELECT /*+ USE_INDEX(invoices, idx_invoices_created_status) */ " +
               "i.id, i.invoice_number, " +
               "pgp_sym_decrypt(i.total_amount::bytea, :encryptionKey) as total_amount, " +
               "i.status, i.created_at " +
               "FROM invoices i " +
               "WHERE (:startDate IS NULL OR i.created_at >= :startDate) " +
               "AND (:endDate IS NULL OR i.created_at <= :endDate) " +
               "AND (:status IS NULL OR i.status = :status) " +
               "ORDER BY i.created_at DESC";
    }

    /**
     * Construction de requête de comptage
     */
    private String buildCountQuery(String entityType, Map<String, Object> filters) {
        switch (entityType.toUpperCase()) {
            case "EMPLOYEE":
                return "SELECT COUNT(e) FROM Employee e WHERE " +
                       "(:department IS NULL OR e.employmentInfo.department = :department) " +
                       "AND (:isActive IS NULL OR e.employmentInfo.isActive = :isActive)";
            case "PAYROLL":
                return "SELECT COUNT(pr) FROM PayrollRecord pr WHERE " +
                       "(:startYear IS NULL OR pr.periodYear >= :startYear) " +
                       "AND (:endYear IS NULL OR pr.periodYear <= :endYear) " +
                       "AND (:employeeId IS NULL OR pr.employee.id = :employeeId)";
            case "ATTENDANCE":
                return "SELECT COUNT(ar) FROM AttendanceRecord ar WHERE " +
                       "(:startDate IS NULL OR ar.workDate >= :startDate) " +
                       "AND (:endDate IS NULL OR ar.workDate <= :endDate) " +
                       "AND (:employeeId IS NULL OR ar.employee.id = :employeeId)";
            default:
                throw new IllegalArgumentException("Unsupported entity type for count: " + entityType);
        }
    }

    /**
     * Création de Pageable optimisé pour les gros volumes
     */
    public Pageable createOptimizedPageable(int page, int size, String sortBy, String sortDirection) {
        // Limiter la taille de page pour éviter les timeouts
        int optimizedSize = Math.min(size, 1000);
        
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDirection) ? 
            Sort.Direction.DESC : Sort.Direction.ASC;
        
        Sort sort = Sort.by(direction, sortBy);
        
        return PageRequest.of(page, optimizedSize, sort);
    }

    /**
     * Optimisation des requêtes avec hints de performance
     */
    public void optimizeQueryExecution() {
        // Mise à jour des statistiques de la base de données
        entityManager.createNativeQuery("ANALYZE employees, payroll_records, attendance_records, invoices")
                    .executeUpdate();
        
        log.info("Database statistics updated for query optimization");
    }

    /**
     * Récupération de la clé de chiffrement
     */
    private String getEncryptionKey() {
        // Dans un vrai système, cela viendrait d'un service sécurisé
        return System.getenv("BANTUOPS_ENCRYPTION_KEY");
    }

    /**
     * Nettoyage du cache des requêtes préparées
     */
    public void clearPreparedQueryCache() {
        preparedQueries.clear();
        log.info("Prepared query cache cleared");
    }

    /**
     * Statistiques des requêtes pour monitoring
     */
    public Map<String, Object> getQueryStatistics() {
        return Map.of(
            "prepared.queries.count", preparedQueries.size(),
            "cache.hit.ratio", "N/A", // Nécessiterait l'intégration avec les métriques JPA
            "avg.query.time", "N/A",
            "slow.queries.count", "N/A"
        );
    }
}