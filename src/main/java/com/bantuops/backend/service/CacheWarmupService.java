package com.bantuops.backend.service;

import com.bantuops.backend.entity.Employee;
import com.bantuops.backend.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service pour le pré-chargement (warmup) des caches
 * Améliore les performances en chargeant les données fréquemment utilisées
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CacheWarmupService {

    private final CachedCalculationService cachedCalculationService;
    private final EmployeeRepository employeeRepository;
    private final TaxCalculationService taxCalculationService;

    /**
     * Pré-chargement automatique au démarrage de l'application
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void warmupCachesOnStartup() {
        log.info("Starting cache warmup process...");
        
        try {
            CompletableFuture<Void> employeeWarmup = warmupEmployeeCache();
            CompletableFuture<Void> taxWarmup = warmupTaxCalculationCache();
            CompletableFuture<Void> systemConfigWarmup = warmupSystemConfigCache();
            CompletableFuture<Void> attendanceRulesWarmup = warmupAttendanceRulesCache();

            // Attendre que tous les warmups soient terminés
            CompletableFuture.allOf(employeeWarmup, taxWarmup, systemConfigWarmup, attendanceRulesWarmup)
                    .thenRun(() -> log.info("Cache warmup completed successfully"))
                    .exceptionally(throwable -> {
                        log.error("Error during cache warmup", throwable);
                        return null;
                    });

        } catch (Exception e) {
            log.error("Failed to start cache warmup process", e);
        }
    }

    /**
     * Pré-chargement périodique des caches (tous les jours à 6h du matin)
     */
    @Scheduled(cron = "0 0 6 * * *")
    @Async
    public void scheduledCacheWarmup() {
        log.info("Starting scheduled cache warmup...");
        warmupCachesOnStartup();
    }

    /**
     * Pré-chargement du cache des employés actifs
     */
    @Async
    public CompletableFuture<Void> warmupEmployeeCache() {
        return CompletableFuture.runAsync(() -> {
            try {
                log.debug("Warming up employee cache...");
                
                List<Employee> activeEmployees = employeeRepository.findByIsActiveTrue();
                log.info("Found {} active employees for cache warmup", activeEmployees.size());

                for (Employee employee : activeEmployees) {
                    try {
                        // Charger les détails de l'employé dans le cache
                        cachedCalculationService.getEmployeeWithDetailsCached(employee.getId());
                        
                        // Petit délai pour éviter de surcharger le système
                        Thread.sleep(10);
                    } catch (Exception e) {
                        log.warn("Failed to warm up cache for employee {}: {}", employee.getId(), e.getMessage());
                    }
                }
                
                log.info("Employee cache warmup completed for {} employees", activeEmployees.size());
            } catch (Exception e) {
                log.error("Error during employee cache warmup", e);
                throw new RuntimeException("Employee cache warmup failed", e);
            }
        });
    }

    /**
     * Pré-chargement du cache des calculs de taxes
     */
    @Async
    public CompletableFuture<Void> warmupTaxCalculationCache() {
        return CompletableFuture.runAsync(() -> {
            try {
                log.debug("Warming up tax calculation cache...");
                
                // Salaires typiques pour le pré-chargement
                BigDecimal[] commonSalaries = {
                    BigDecimal.valueOf(60000),   // SMIG
                    BigDecimal.valueOf(100000),  // Salaire junior
                    BigDecimal.valueOf(200000),  // Salaire moyen
                    BigDecimal.valueOf(500000),  // Salaire senior
                    BigDecimal.valueOf(1000000), // Salaire cadre
                    BigDecimal.valueOf(2000000)  // Salaire direction
                };

                String[] employeeTypes = {"PERMANENT", "CONTRACT", "INTERN"};
                YearMonth currentPeriod = YearMonth.now();
                YearMonth previousPeriod = currentPeriod.minusMonths(1);

                int cacheCount = 0;
                for (BigDecimal salary : commonSalaries) {
                    for (String employeeType : employeeTypes) {
                        try {
                            // Charger pour le mois actuel et précédent
                            cachedCalculationService.calculateTaxCached(salary, currentPeriod, employeeType);
                            cachedCalculationService.calculateTaxCached(salary, previousPeriod, employeeType);
                            cacheCount += 2;
                            
                            Thread.sleep(5);
                        } catch (Exception e) {
                            log.warn("Failed to warm up tax cache for salary {} and type {}: {}", 
                                    salary, employeeType, e.getMessage());
                        }
                    }
                }
                
                log.info("Tax calculation cache warmup completed with {} entries", cacheCount);
            } catch (Exception e) {
                log.error("Error during tax calculation cache warmup", e);
                throw new RuntimeException("Tax calculation cache warmup failed", e);
            }
        });
    }

    /**
     * Pré-chargement du cache de configuration système
     */
    @Async
    public CompletableFuture<Void> warmupSystemConfigCache() {
        return CompletableFuture.runAsync(() -> {
            try {
                log.debug("Warming up system configuration cache...");
                
                String[] configKeys = {
                    "default.vat.rate",
                    "minimum.wage",
                    "max.overtime.hours",
                    "company.fiscal.year.start",
                    "default.currency",
                    "working.hours.per.day",
                    "working.days.per.week",
                    "overtime.rate.multiplier",
                    "night.shift.bonus",
                    "holiday.pay.rate"
                };

                for (String configKey : configKeys) {
                    try {
                        cachedCalculationService.getSystemConfigCached(configKey);
                        Thread.sleep(2);
                    } catch (Exception e) {
                        log.warn("Failed to warm up system config cache for key {}: {}", configKey, e.getMessage());
                    }
                }
                
                log.info("System configuration cache warmup completed for {} keys", configKeys.length);
            } catch (Exception e) {
                log.error("Error during system configuration cache warmup", e);
                throw new RuntimeException("System configuration cache warmup failed", e);
            }
        });
    }

    /**
     * Pré-chargement du cache des règles d'assiduité
     */
    @Async
    public CompletableFuture<Void> warmupAttendanceRulesCache() {
        return CompletableFuture.runAsync(() -> {
            try {
                log.debug("Warming up attendance rules cache...");
                
                String[] departments = {"IT", "HR", "FINANCE", "OPERATIONS", "SALES", "MARKETING"};
                String[] contractTypes = {"PERMANENT", "CONTRACT", "INTERN", "CONSULTANT"};

                int cacheCount = 0;
                for (String department : departments) {
                    for (String contractType : contractTypes) {
                        try {
                            cachedCalculationService.getAttendanceRulesCached(department, contractType);
                            cacheCount++;
                            Thread.sleep(5);
                        } catch (Exception e) {
                            log.warn("Failed to warm up attendance rules cache for department {} and contract {}: {}", 
                                    department, contractType, e.getMessage());
                        }
                    }
                }
                
                log.info("Attendance rules cache warmup completed with {} entries", cacheCount);
            } catch (Exception e) {
                log.error("Error during attendance rules cache warmup", e);
                throw new RuntimeException("Attendance rules cache warmup failed", e);
            }
        });
    }

    /**
     * Pré-chargement du cache des taux de change
     */
    @Async
    public CompletableFuture<Void> warmupExchangeRateCache() {
        return CompletableFuture.runAsync(() -> {
            try {
                log.debug("Warming up exchange rate cache...");
                
                String[] currencies = {"EUR", "USD", "GBP", "CAD"};
                LocalDate today = LocalDate.now();
                
                for (String currency : currencies) {
                    try {
                        // Charger les taux pour aujourd'hui et les 7 derniers jours
                        for (int i = 0; i < 7; i++) {
                            LocalDate date = today.minusDays(i);
                            cachedCalculationService.getExchangeRateCached(currency, date);
                            Thread.sleep(10);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to warm up exchange rate cache for currency {}: {}", currency, e.getMessage());
                    }
                }
                
                log.info("Exchange rate cache warmup completed for {} currencies", currencies.length);
            } catch (Exception e) {
                log.error("Error during exchange rate cache warmup", e);
                throw new RuntimeException("Exchange rate cache warmup failed", e);
            }
        });
    }

    /**
     * Pré-chargement ciblé pour un employé spécifique
     */
    @Async
    public CompletableFuture<Void> warmupEmployeeSpecificCache(Long employeeId) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.debug("Warming up cache for employee: {}", employeeId);
                
                // Charger les données de l'employé
                cachedCalculationService.getEmployeeWithDetailsCached(employeeId);
                
                // Charger les calculs de paie pour les 3 derniers mois
                YearMonth currentMonth = YearMonth.now();
                for (int i = 0; i < 3; i++) {
                    YearMonth period = currentMonth.minusMonths(i);
                    try {
                        cachedCalculationService.calculatePayrollCached(employeeId, period);
                        Thread.sleep(50);
                    } catch (Exception e) {
                        log.warn("Failed to warm up payroll cache for employee {} and period {}: {}", 
                                employeeId, period, e.getMessage());
                    }
                }
                
                // Charger les données d'assiduité pour le mois actuel
                LocalDate startOfMonth = currentMonth.atDay(1);
                LocalDate endOfMonth = currentMonth.atEndOfMonth();
                cachedCalculationService.getAttendanceRecordsCached(employeeId, startOfMonth, endOfMonth);
                
                log.info("Employee-specific cache warmup completed for employee: {}", employeeId);
            } catch (Exception e) {
                log.error("Error during employee-specific cache warmup for employee: {}", employeeId, e);
                throw new RuntimeException("Employee-specific cache warmup failed", e);
            }
        });
    }

    /**
     * Pré-chargement pour la période de paie
     */
    @Async
    public CompletableFuture<Void> warmupPayrollPeriodCache(YearMonth period) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.debug("Warming up cache for payroll period: {}", period);
                
                List<Employee> activeEmployees = employeeRepository.findByIsActiveTrue();
                
                for (Employee employee : activeEmployees) {
                    try {
                        cachedCalculationService.calculatePayrollCached(employee.getId(), period);
                        Thread.sleep(20);
                    } catch (Exception e) {
                        log.warn("Failed to warm up payroll cache for employee {} and period {}: {}", 
                                employee.getId(), period, e.getMessage());
                    }
                }
                
                log.info("Payroll period cache warmup completed for period: {} with {} employees", 
                        period, activeEmployees.size());
            } catch (Exception e) {
                log.error("Error during payroll period cache warmup for period: {}", period, e);
                throw new RuntimeException("Payroll period cache warmup failed", e);
            }
        });
    }

    /**
     * Nettoyage et rechargement complet des caches
     */
    @Async
    public CompletableFuture<Void> refreshAllCaches() {
        return CompletableFuture.runAsync(() -> {
            try {
                log.info("Starting complete cache refresh...");
                
                // Vider tous les caches
                cachedCalculationService.evictAllCaches();
                
                // Attendre un peu pour que l'éviction soit effective
                Thread.sleep(1000);
                
                // Recharger les caches
                warmupCachesOnStartup();
                
                log.info("Complete cache refresh completed");
            } catch (Exception e) {
                log.error("Error during complete cache refresh", e);
                throw new RuntimeException("Complete cache refresh failed", e);
            }
        });
    }
}