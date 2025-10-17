package com.bantuops.backend.dto.migration;

/**
 * Énumération des statuts possibles pour une migration.
 */
public enum MigrationStatus {
    /**
     * Migration non démarrée
     */
    NOT_STARTED,
    
    /**
     * Migration en cours d'exécution
     */
    IN_PROGRESS,
    
    /**
     * Migration terminée avec succès
     */
    COMPLETED,
    
    /**
     * Migration échouée
     */
    FAILED,
    
    /**
     * Migration annulée
     */
    CANCELLED,
    
    /**
     * Migration en cours de rollback
     */
    ROLLING_BACK,
    
    /**
     * Rollback terminé
     */
    ROLLED_BACK,
    
    /**
     * Statut non trouvé (pour les requêtes avec ID invalide)
     */
    NOT_FOUND
}