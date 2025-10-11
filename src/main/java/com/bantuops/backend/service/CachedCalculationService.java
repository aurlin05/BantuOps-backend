package com.bantuops.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CachedCalculationService {

    /**
     * Cache tax calculations for frequently accessed salary ranges
     */
    @Cacheable(value = "tax-rates", key = "#salary + '_' + #period.toString()")
    public BigDecimal calculateTaxRate(BigDecimal salary, YearMonth period) {
        log.debug("Calculating tax rate for salary: {} in period: {}", salary, period);
        
        // Senegalese tax brackets (simplified)
        if (salary.compareTo(new BigDecimal("30000")) <= 0) {
            return BigDecimal.ZERO; // No tax for low income
        } else if (salary.compareTo(new BigDecimal("50000")) <= 0) {
            return new BigDecimal("0.10"); // 10% tax
        } else if (salary.compareTo(new BigDecimal("100000")) <= 0) {
            return new BigDecimal("0.15"); // 15% tax
        } else if (salary.compareTo(new BigDecimal("200000")) <= 0) {
            return new BigDecimal("0.20"); // 20% tax
        } else {
            return new BigDecimal("0.25"); // 25% tax for high income
        }
    }

    /**
     * Cache social contribution rates
     */
    @Cacheable(value = "tax-rates", key = "'social_contrib_' + #salary")
    public Map<String, BigDecimal> calculateSocialContributions(BigDecimal salary) {
        log.debug("Calculating social contributions for salary: {}", salary);
        
        return Map.of(
            "IPRES", salary.multiply(new BigDecimal("0.06")), // 6% IPRES
            "CSS", salary.multiply(new BigDecimal("0.07")),   // 7% CSS
            "FNR", salary.multiply(new BigDecimal("0.03"))    // 3% FNR
        );
    }

    /**
     * Cache employee data with frequent access
     */
    @Cacheable(value = "employees", key = "#employeeId")
    public Object getEmployeeData(Long employeeId) {
        log.debug("Fetching employee data from cache for ID: {}", employeeId);
        // This will be implemented when we have the Employee entity
        return null;
    }

    /**
     * Cache payroll calculations
     */
    @Cacheable(value = "payroll-calculations", key = "#employeeId + '_' + #period.toString()")
    public Object getPayrollCalculation(Long employeeId, YearMonth period) {
        log.debug("Fetching payroll calculation from cache for employee: {} in period: {}", employeeId, period);
        // This will be implemented when we have the PayrollCalculationService
        return null;
    }

    /**
     * Cache attendance rules
     */
    @Cacheable(value = "attendance-rules", key = "'company_rules'")
    public Map<String, Object> getAttendanceRules() {
        log.debug("Fetching attendance rules from cache");
        
        return Map.of(
            "lateThresholdMinutes", 15,
            "halfDayThresholdMinutes", 240, // 4 hours
            "maxLatenessPenaltyPercent", 0.05, // 5% salary deduction
            "workingHoursPerDay", 8,
            "workingDaysPerWeek", 5
        );
    }

    /**
     * Cache user permissions
     */
    @Cacheable(value = "user-permissions", key = "#userId")
    public Object getUserPermissions(Long userId) {
        log.debug("Fetching user permissions from cache for user: {}", userId);
        // This will be implemented when we have the User service
        return null;
    }

    /**
     * Update cached employee data
     */
    @CachePut(value = "employees", key = "#employeeId")
    public Object updateEmployeeCache(Long employeeId, Object employeeData) {
        log.debug("Updating employee cache for ID: {}", employeeId);
        return employeeData;
    }

    /**
     * Evict employee cache when data changes
     */
    @CacheEvict(value = "employees", key = "#employeeId")
    public void evictEmployeeCache(Long employeeId) {
        log.debug("Evicting employee cache for ID: {}", employeeId);
    }

    /**
     * Evict payroll cache for an employee
     */
    @CacheEvict(value = "payroll-calculations", allEntries = true, condition = "#employeeId != null")
    public void evictPayrollCache(Long employeeId) {
        log.debug("Evicting payroll cache for employee: {}", employeeId);
    }

    /**
     * Evict all payroll calculations cache
     */
    @CacheEvict(value = "payroll-calculations", allEntries = true)
    public void evictAllPayrollCache() {
        log.debug("Evicting all payroll calculations cache");
    }

    /**
     * Evict user permissions cache
     */
    @CacheEvict(value = "user-permissions", key = "#userId")
    public void evictUserPermissionsCache(Long userId) {
        log.debug("Evicting user permissions cache for user: {}", userId);
    }

    /**
     * Evict attendance rules cache (when rules are updated)
     */
    @CacheEvict(value = "attendance-rules", allEntries = true)
    public void evictAttendanceRulesCache() {
        log.debug("Evicting attendance rules cache");
    }

    /**
     * Cache frequent calculations with short TTL for real-time data
     */
    @Cacheable(value = "frequent-calculations", key = "'dashboard_metrics_' + #userId")
    public Map<String, Object> getDashboardMetrics(Long userId) {
        log.debug("Calculating dashboard metrics for user: {}", userId);
        
        // This would typically fetch real-time data from database
        return Map.of(
            "totalEmployees", 0,
            "activePayrolls", 0,
            "pendingInvoices", 0,
            "monthlyRevenue", BigDecimal.ZERO,
            "lastUpdated", System.currentTimeMillis()
        );
    }

    /**
     * Cache business rules with medium TTL
     */
    @Cacheable(value = "business-rules", key = "'senegal_tax_brackets'")
    public Map<String, Object> getSenegalTaxBrackets() {
        log.debug("Fetching Senegal tax brackets from cache");
        
        return Map.of(
            "brackets", Map.of(
                "0-30000", BigDecimal.ZERO,
                "30001-50000", new BigDecimal("0.10"),
                "50001-100000", new BigDecimal("0.15"),
                "100001-200000", new BigDecimal("0.20"),
                "200001+", new BigDecimal("0.25")
            ),
            "lastUpdated", System.currentTimeMillis()
        );
    }

    /**
     * Cache VAT rates for Senegal
     */
    @Cacheable(value = "business-rules", key = "'senegal_vat_rates'")
    public Map<String, BigDecimal> getSenegalVATRates() {
        log.debug("Fetching Senegal VAT rates from cache");
        
        return Map.of(
            "standard", new BigDecimal("0.18"), // 18% standard VAT
            "reduced", new BigDecimal("0.10"),  // 10% reduced VAT for some goods
            "exempt", BigDecimal.ZERO           // 0% for exempt goods
        );
    }

    /**
     * Cache overtime calculation rules
     */
    @Cacheable(value = "business-rules", key = "'overtime_rules'")
    public Map<String, Object> getOvertimeRules() {
        log.debug("Fetching overtime rules from cache");
        
        return Map.of(
            "normalHoursPerDay", 8,
            "normalHoursPerWeek", 40,
            "overtimeMultiplier", new BigDecimal("1.5"), // 150% for overtime
            "nightShiftMultiplier", new BigDecimal("1.25"), // 125% for night shift
            "weekendMultiplier", new BigDecimal("2.0"), // 200% for weekend work
            "holidayMultiplier", new BigDecimal("2.5") // 250% for holiday work
        );
    }

    /**
     * Cache frequently accessed calculation results
     */
    @Cacheable(value = "frequent-calculations", key = "'monthly_summary_' + #employeeId + '_' + #period.toString()")
    public Map<String, Object> getMonthlySummary(Long employeeId, YearMonth period) {
        log.debug("Calculating monthly summary for employee: {} in period: {}", employeeId, period);
        
        // This would typically aggregate data from multiple sources
        return Map.of(
            "employeeId", employeeId,
            "period", period.toString(),
            "totalWorkingDays", 22,
            "actualWorkingDays", 0,
            "totalAbsences", 0,
            "totalLateMinutes", 0,
            "overtimeHours", BigDecimal.ZERO,
            "grossSalary", BigDecimal.ZERO,
            "netSalary", BigDecimal.ZERO
        );
    }

    /**
     * Evict frequent calculations cache
     */
    @CacheEvict(value = "frequent-calculations", allEntries = true)
    public void evictFrequentCalculationsCache() {
        log.debug("Evicting frequent calculations cache");
    }

    /**
     * Evict business rules cache
     */
    @CacheEvict(value = "business-rules", allEntries = true)
    public void evictBusinessRulesCache() {
        log.debug("Evicting business rules cache");
    }

    /**
     * Warm up cache with frequently accessed data
     */
    public void warmUpCache() {
        log.info("Starting cache warm-up process");
        
        try {
            // Pre-load attendance rules
            getAttendanceRules();
            
            // Pre-load business rules
            getSenegalTaxBrackets();
            getSenegalVATRates();
            getOvertimeRules();
            
            // Pre-calculate common tax rates
            YearMonth currentPeriod = YearMonth.now();
            BigDecimal[] commonSalaries = {
                new BigDecimal("50000"),
                new BigDecimal("100000"),
                new BigDecimal("150000"),
                new BigDecimal("200000"),
                new BigDecimal("300000")
            };
            
            for (BigDecimal salary : commonSalaries) {
                calculateTaxRate(salary, currentPeriod);
                calculateSocialContributions(salary);
            }
            
            log.info("Cache warm-up completed successfully");
        } catch (Exception e) {
            log.error("Cache warm-up failed", e);
        }
    }
}