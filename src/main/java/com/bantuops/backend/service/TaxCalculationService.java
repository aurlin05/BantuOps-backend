package com.bantuops.backend.service;

import com.bantuops.backend.dto.TaxCalculationResult;
import com.bantuops.backend.dto.TaxExemption;
import com.bantuops.backend.dto.TaxRates;
import com.bantuops.backend.entity.Employee;
import com.bantuops.backend.exception.PayrollCalculationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;

/**
 * Service de calcul des taxes selon la législation sénégalaise
 * Conforme aux exigences 1.2, 1.4, 3.1, 3.2 pour les calculs fiscaux
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaxCalculationService {

    private final AuditService auditService;

    // Constantes fiscales sénégalaises (2024)
    private static final BigDecimal IPRES_RATE = new BigDecimal("0.06"); // 6% IPRES
    private static final BigDecimal CSS_RATE = new BigDecimal("0.07"); // 7% CSS
    private static final BigDecimal FAMILY_ALLOWANCE_RATE = new BigDecimal("0.07"); // 7% Allocations familiales
    private static final BigDecimal MINIMUM_TAXABLE_INCOME = new BigDecimal("30000"); // Seuil d'imposition
    private static final int CALCULATION_SCALE = 2;

    // Barèmes d'impôt sur le revenu sénégalais (progressif)
    private static final TaxBracket[] TAX_BRACKETS = {
        new TaxBracket(BigDecimal.ZERO, new BigDecimal("630000"), BigDecimal.ZERO, BigDecimal.ZERO),
        new TaxBracket(new BigDecimal("630001"), new BigDecimal("1500000"), new BigDecimal("0.20"), BigDecimal.ZERO),
        new TaxBracket(new BigDecimal("1500001"), new BigDecimal("4000000"), new BigDecimal("0.30"), new BigDecimal("174000")),
        new TaxBracket(new BigDecimal("4000001"), new BigDecimal("8000000"), new BigDecimal("0.35"), new BigDecimal("374000")),
        new TaxBracket(new BigDecimal("8000001"), BigDecimal.valueOf(Long.MAX_VALUE), new BigDecimal("0.40"), new BigDecimal("774000"))
    };

    /**
     * Calcule les taxes selon la législation sénégalaise
     * Exigences: 1.2, 1.4, 3.1, 3.2
     */
    @Cacheable(value = "tax-calculations", key = "#grossSalary + '_' + #employee.id + '_' + T(java.time.YearMonth).now()")
    public TaxCalculationResult calculateTaxes(BigDecimal grossSalary, Employee employee) {
        log.info("Calcul des taxes pour l'employé {} avec salaire brut {}", 
                employee.getId(), grossSalary);

        try {
            // Calcul du salaire annuel pour l'impôt
            BigDecimal annualSalary = grossSalary.multiply(new BigDecimal("12"));

            // Calcul de l'impôt sur le revenu
            BigDecimal incomeTax = calculateIncomeTax(annualSalary).divide(new BigDecimal("12"), CALCULATION_SCALE, RoundingMode.HALF_UP);

            // Calcul des cotisations sociales
            BigDecimal ipresContribution = calculateIpresContribution(grossSalary);
            BigDecimal cssContribution = calculateCssContribution(grossSalary);
            BigDecimal familyAllowanceContribution = calculateFamilyAllowanceContribution(grossSalary);

            TaxCalculationResult result = TaxCalculationResult.builder()
                .grossSalary(grossSalary)
                .annualSalary(annualSalary)
                .incomeTax(incomeTax)
                .ipresContribution(ipresContribution)
                .cssContribution(cssContribution)
                .familyAllowanceContribution(familyAllowanceContribution)
                .totalSocialContributions(ipresContribution.add(cssContribution).add(familyAllowanceContribution))
                .totalTaxes(incomeTax.add(ipresContribution).add(cssContribution).add(familyAllowanceContribution))
                .build();

            // Audit du calcul
            auditService.logTaxCalculation(employee.getId(), grossSalary, result);

            log.info("Calcul des taxes terminé pour l'employé {} - Impôt: {}, Cotisations: {}", 
                    employee.getId(), incomeTax, result.getTotalSocialContributions());

            return result;

        } catch (Exception e) {
            log.error("Erreur lors du calcul des taxes pour l'employé {}: {}", 
                     employee.getId(), e.getMessage(), e);
            throw new PayrollCalculationException("Erreur lors du calcul des taxes", e);
        }
    }

    /**
     * Calcule l'impôt sur le revenu avec tranches progressives
     * Exigences: 1.2, 3.1, 3.2
     */
    public BigDecimal calculateIncomeTax(BigDecimal annualSalary) {
        log.debug("Calcul de l'impôt sur le revenu pour salaire annuel: {}", annualSalary);

        if (annualSalary.compareTo(MINIMUM_TAXABLE_INCOME) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal remainingIncome = annualSalary;

        for (TaxBracket bracket : TAX_BRACKETS) {
            if (remainingIncome.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal taxableInBracket = calculateTaxableAmountInBracket(remainingIncome, bracket);
            if (taxableInBracket.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal taxInBracket = taxableInBracket.multiply(bracket.getRate())
                    .add(bracket.getFixedAmount())
                    .setScale(CALCULATION_SCALE, RoundingMode.HALF_UP);
                totalTax = totalTax.add(taxInBracket);
                remainingIncome = remainingIncome.subtract(taxableInBracket);
            }
        }

        return totalTax.setScale(CALCULATION_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Calcule les cotisations sociales IPRES avec taux sénégalais
     * Exigences: 1.4, 3.1, 3.2
     */
    public BigDecimal calculateIpresContribution(BigDecimal grossSalary) {
        log.debug("Calcul de la cotisation IPRES pour salaire: {}", grossSalary);

        // Plafond IPRES (à ajuster selon la réglementation)
        BigDecimal ipresMaxSalary = new BigDecimal("1800000"); // Plafond mensuel
        BigDecimal taxableSalary = grossSalary.min(ipresMaxSalary);

        return taxableSalary.multiply(IPRES_RATE)
            .setScale(CALCULATION_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Calcule les cotisations CSS avec taux sénégalais
     * Exigences: 1.4, 3.1, 3.2
     */
    public BigDecimal calculateCssContribution(BigDecimal grossSalary) {
        log.debug("Calcul de la cotisation CSS pour salaire: {}", grossSalary);

        // Plafond CSS (à ajuster selon la réglementation)
        BigDecimal cssMaxSalary = new BigDecimal("1800000"); // Plafond mensuel
        BigDecimal taxableSalary = grossSalary.min(cssMaxSalary);

        return taxableSalary.multiply(CSS_RATE)
            .setScale(CALCULATION_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Calcule les cotisations d'allocations familiales
     * Exigences: 1.4, 3.1, 3.2
     */
    public BigDecimal calculateFamilyAllowanceContribution(BigDecimal grossSalary) {
        log.debug("Calcul de la cotisation allocations familiales pour salaire: {}", grossSalary);

        // Plafond allocations familiales
        BigDecimal maxSalary = new BigDecimal("1800000"); // Plafond mensuel
        BigDecimal taxableSalary = grossSalary.min(maxSalary);

        return taxableSalary.multiply(FAMILY_ALLOWANCE_RATE)
            .setScale(CALCULATION_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Gère les exonérations fiscales selon les critères sénégalais
     * Exigences: 1.2, 3.1, 3.2
     */
    public TaxExemption calculateTaxExemptions(Employee employee, BigDecimal grossSalary) {
        log.debug("Calcul des exonérations fiscales pour l'employé {}", employee.getId());

        TaxExemption exemption = TaxExemption.builder()
            .employeeId(employee.getId())
            .grossSalary(grossSalary)
            .build();

        // Exonération pour les nouveaux diplômés (exemple)
        if (isNewGraduateExemption(employee)) {
            exemption.setNewGraduateExemption(calculateNewGraduateExemption(grossSalary));
        }

        // Exonération pour les investissements (exemple)
        if (hasInvestmentExemption(employee)) {
            exemption.setInvestmentExemption(calculateInvestmentExemption(grossSalary));
        }

        // Calcul du total des exonérations
        BigDecimal totalExemption = exemption.getNewGraduateExemption()
            .add(exemption.getInvestmentExemption());
        exemption.setTotalExemption(totalExemption);

        return exemption;
    }

    /**
     * Vérifie les taux de taxes en vigueur
     */
    public TaxRates getCurrentTaxRates() {
        return TaxRates.builder()
            .ipresRate(IPRES_RATE)
            .cssRate(CSS_RATE)
            .familyAllowanceRate(FAMILY_ALLOWANCE_RATE)
            .minimumTaxableIncome(MINIMUM_TAXABLE_INCOME)
            .taxBrackets(TAX_BRACKETS)
            .build();
    }

    // Méthodes privées

    private BigDecimal calculateTaxableAmountInBracket(BigDecimal income, TaxBracket bracket) {
        if (income.compareTo(bracket.getMinIncome()) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal maxTaxableInBracket = bracket.getMaxIncome().subtract(bracket.getMinIncome());
        return income.min(maxTaxableInBracket);
    }

    private boolean isNewGraduateExemption(Employee employee) {
        // Logique pour déterminer si l'employé bénéficie de l'exonération nouveaux diplômés
        // À implémenter selon les critères spécifiques
        return false;
    }

    private BigDecimal calculateNewGraduateExemption(BigDecimal grossSalary) {
        // Calcul de l'exonération pour nouveaux diplômés
        return BigDecimal.ZERO;
    }

    private boolean hasInvestmentExemption(Employee employee) {
        // Logique pour déterminer si l'employé a des investissements exonérés
        return false;
    }

    private BigDecimal calculateInvestmentExemption(BigDecimal grossSalary) {
        // Calcul de l'exonération pour investissements
        return BigDecimal.ZERO;
    }

    /**
     * Classe interne pour les tranches d'imposition
     */
    public static class TaxBracket {
        private final BigDecimal minIncome;
        private final BigDecimal maxIncome;
        private final BigDecimal rate;
        private final BigDecimal fixedAmount;

        public TaxBracket(BigDecimal minIncome, BigDecimal maxIncome, BigDecimal rate, BigDecimal fixedAmount) {
            this.minIncome = minIncome;
            this.maxIncome = maxIncome;
            this.rate = rate;
            this.fixedAmount = fixedAmount;
        }

        public BigDecimal getMinIncome() { return minIncome; }
        public BigDecimal getMaxIncome() { return maxIncome; }
        public BigDecimal getRate() { return rate; }
        public BigDecimal getFixedAmount() { return fixedAmount; }
    }
}