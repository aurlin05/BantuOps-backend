package com.bantuops.backend.dto.migration;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * DTO pour les résultats de rollback des données.
 * Contient les informations détaillées sur le processus de rollback.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RollbackResult {
    
    private String backupId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean success;
    private String errorMessage;
    private Map<String, EntityRollbackResult> entityResults = new HashMap<>();
    
    /**
     * Ajoute le résultat de rollback pour une entité
     */
    public void addEntityResult(String entityType, EntityRollbackResult result) {
        this.entityResults.put(entityType, result);
    }
    
    /**
     * Calcule la durée totale du rollback
     */
    public long getDurationInSeconds() {
        if (startTime != null && endTime != null) {
            return java.time.Duration.between(startTime, endTime).getSeconds();
        }
        return 0;
    }
    
    /**
     * Calcule le nombre total d'enregistrements restaurés
     */
    public int getTotalRestoredRecords() {
        return entityResults.values().stream()
            .mapToInt(EntityRollbackResult::getRestoredRecords)
            .sum();
    }
    
    /**
     * Calcule le nombre total d'erreurs
     */
    public int getTotalErrorRecords() {
        return entityResults.values().stream()
            .mapToInt(EntityRollbackResult::getErrorRecords)
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
        return (getTotalRestoredRecords() * 100.0) / totalRecords;
    }
    
    /**
     * Calcule le nombre total d'enregistrements
     */
    public int getTotalRecords() {
        return entityResults.values().stream()
            .mapToInt(EntityRollbackResult::getTotalRecords)
            .sum();
    }
    
    /**
     * Vérifie si toutes les entités ont été restaurées avec succès
     */
    public boolean isCompleteSuccess() {
        return success && entityResults.values().stream()
            .allMatch(EntityRollbackResult::isSuccess);
    }
    
    /**
     * Génère un résumé textuel du rollback
     */
    public String generateSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("=== Résumé de Rollback ===\n");
        summary.append("ID de Sauvegarde: ").append(backupId).append("\n");
        summary.append("Statut: ").append(success ? "SUCCÈS" : "ÉCHEC").append("\n");
        summary.append("Durée: ").append(getDurationInSeconds()).append(" secondes\n");
        summary.append("Total des enregistrements: ").append(getTotalRecords()).append("\n");
        summary.append("Enregistrements restaurés: ").append(getTotalRestoredRecords()).append("\n");
        summary.append("Erreurs: ").append(getTotalErrorRecords()).append("\n");
        summary.append("Taux de réussite: ").append(String.format("%.2f%%", getSuccessRate())).append("\n");
        
        if (errorMessage != null) {
            summary.append("Message d'erreur: ").append(errorMessage).append("\n");
        }
        
        summary.append("\n=== Détails par Entité ===\n");
        entityResults.forEach((entityType, result) -> {
            summary.append(entityType).append(": ")
                .append(result.getRestoredRecords()).append("/").append(result.getTotalRecords())
                .append(" (").append(result.getErrorRecords()).append(" erreurs)\n");
        });
        
        return summary.toString();
    }
    
    /**
     * Classe interne pour les résultats de rollback par entité
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EntityRollbackResult {
        private int totalRecords;
        private int restoredRecords;
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
            return (restoredRecords * 100.0) / totalRecords;
        }
        
        /**
         * Vérifie si le rollback de cette entité est complètement réussi
         */
        public boolean isCompleteSuccess() {
            return success && errorRecords == 0;
        }
    }
}