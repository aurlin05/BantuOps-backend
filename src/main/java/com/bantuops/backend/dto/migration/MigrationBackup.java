package com.bantuops.backend.dto.migration;

import com.bantuops.backend.entity.Employee;
import com.bantuops.backend.entity.Invoice;
import com.bantuops.backend.entity.PayrollRecord;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO pour stocker les données de sauvegarde avant migration.
 * Contient toutes les données nécessaires pour effectuer un rollback.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MigrationBackup {
    
    private String backupId;
    private LocalDateTime createdAt;
    private List<Employee> employees;
    private List<Invoice> invoices;
    private List<PayrollRecord> payrollRecords;
    private String description;
    private long sizeInBytes;
    
    /**
     * Calcule la taille totale des données sauvegardées
     */
    public int getTotalRecords() {
        int total = 0;
        if (employees != null) total += employees.size();
        if (invoices != null) total += invoices.size();
        if (payrollRecords != null) total += payrollRecords.size();
        return total;
    }
    
    /**
     * Génère une description automatique de la sauvegarde
     */
    public void generateDescription() {
        this.description = String.format(
            "Sauvegarde de migration créée le %s - %d employés, %d factures, %d enregistrements de paie",
            createdAt != null ? createdAt.toString() : "date inconnue",
            employees != null ? employees.size() : 0,
            invoices != null ? invoices.size() : 0,
            payrollRecords != null ? payrollRecords.size() : 0
        );
    }
    
    /**
     * Vérifie si la sauvegarde est valide
     */
    public boolean isValid() {
        return backupId != null && 
               createdAt != null && 
               employees != null && 
               invoices != null && 
               payrollRecords != null;
    }
}