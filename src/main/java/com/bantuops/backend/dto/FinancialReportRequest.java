package com.bantuops.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO pour les demandes de rapports financiers
 * Conforme aux exigences 2.4, 2.5, 3.5, 3.6 pour la génération de rapports sécurisés
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialReportRequest {

    @NotNull(message = "La date de début est obligatoire")
    @PastOrPresent(message = "La date de début ne peut pas être dans le futur")
    private LocalDate startDate;

    @NotNull(message = "La date de fin est obligatoire")
    @PastOrPresent(message = "La date de fin ne peut pas être dans le futur")
    private LocalDate endDate;

    @Builder.Default
    private ReportType reportType = ReportType.COMPREHENSIVE;

    @Builder.Default
    private List<ReportSection> sections = List.of(ReportSection.values());

    private String currency;

    private Boolean includeDetails;

    private Boolean includeCharts;

    public enum ReportType {
        REVENUE("Rapport de chiffre d'affaires"),
        VAT("Rapport de TVA"),
        OUTSTANDING("Rapport des impayés"),
        COMPREHENSIVE("Rapport complet"),
        CASH_FLOW("Rapport de trésorerie"),
        CLIENT_ANALYSIS("Analyse clientèle");

        private final String description;

        ReportType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum ReportSection {
        SUMMARY("Résumé exécutif"),
        REVENUE_ANALYSIS("Analyse du chiffre d'affaires"),
        VAT_SUMMARY("Résumé TVA"),
        OUTSTANDING_INVOICES("Factures impayées"),
        CLIENT_RANKING("Classement clients"),
        PAYMENT_DELAYS("Analyse des retards de paiement"),
        MONTHLY_TRENDS("Tendances mensuelles"),
        PERFORMANCE_METRICS("Métriques de performance");

        private final String description;

        ReportSection(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}