package com.bantuops.backend.dto.migration;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * DTO pour les résultats de migration des données.
 * Contient les informations détaillées sur le processus de migration.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MigrationResult {
    
    private String migrationId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean success;
    private String errorMessage;
    private Map<String, EntityMigrationResult> entityResults = new HashMap<>();
    
    /**
     * Ajoute le résultat de migration pour une entité
     */
    public void addEntityResult(String entityType, EntityMigrationResult result) {
        this.entityResults.put(entityType, result);
    }
    
    /**
     * Calcule la durée totale de la migration
     */
    public long getDurationInSeconds() {
        if (startTime != null && endTime != null) {
            return java.time.Duration.between(startTime, endTime).getSeconds();
        }
        return 0;
    }
    
    /**
     * Calcule le nombre total d'enregistrements traités
     */
    public int getTotalProcessedRecords() {
        return entityResults.values().stream()
            .mapToInt(EntityMigrationResult::getProcessedRecords)
            .sum();
    }
    
    /**
     * Calcule le nombre total d'erreurs
     */
    public int getTotalErrorRecords() {
        return entityResults.values().stream()
            .mapToInt(EntityMigrationResult::getErrorRecords)
            .sum();
    }
    
    /**
     * Calcule le taux de réussite global
     */
    public double getSuccessRate() {
        int totalRecords = getTotalRecords();
        if (totalRecords == 0) {
            return 100.0;
        }
        return (getTotalProcessedRecords() * 100.0) / totalRecords;
    }
    
    /**
     * Calcule le nombre total d'enregistrements
     */
    public int getTotalRecords() {
        return entityResults.values().stream()
            .mapToInt(EntityMigrationResult::getTotalRecords)
            .sum();
    }
    
    /**
     * Vérifie si toutes les entités ont été migrées avec succès
     */
    public boolean isCompleteSuccess() {
        return success && entityResults.values().stream()
            .allMatch(EntityMigrationResult::isSuccess);
    }
    
    /**
     * Génère un résumé textuel de la migration
     */
    public String generateSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("=== Résumé de Migration ===\n");
        summary.append("ID de Migration: ").append(migrationId).append("\n");
        summary.append("Statut: ").append(success ? "SUCCÈS" : "ÉCHEC").append("\n");
        summary.append("Durée: ").append(getDurationInSeconds()).append(" secondes\n");
        summary.append("Total des enregistrements: ").append(getTotalRecords()).append("\n");
        summary.append("Enregistrements traités: ").append(getTotalProcessedRecords()).append("\n");
        summary.append("Erreurs: ").append(getTotalErrorRecords()).append("\n");
        summary.append("Taux de réussite: ").append(String.format("%.2f%%", getSuccessRate())).append("\n");
        
        if (errorMessage != null) {
            summary.append("Message d'erreur: ").append(errorMessage).append("\n");
        }
        
        summary.append("\n=== Détails par Entité ===\n");
        entityResults.forEach((entityType, result) -> {
            summary.append(entityType).append(": ")
                .append(result.getProcessedRecords()).append("/").append(result.getTotalRecords())
                .append(" (").append(result.getErrorRecords()).append(" erreurs)\n");
        });
        
        return summary.toString();
    }
    
    /**
     * Classe interne pour les résultats de migration par entité
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EntityMigrationResult {
        private int totalRecords;
        private int processedRecords;
        private int errorRecords;
        private boolean success;
        private String errorMessage;
        
        /**
         * Calcule le taux de réussite pour cette entité
         */
        public double getSuccessRate() {
            if (totalRecords == 0) {
                return 100.0;
            }
            return (processedRecords * 100.0) / totalRecords;
        }
        
        /**
         * Vérifie si la migration de cette entité est complètement réussie
         */
        public boolean isCompleteSuccess() {
            return success && errorRecords == 0;
        }
    }
}