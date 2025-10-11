package com.bantuops.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.YearMonth;

/**
 * DTO pour les documents de bulletin de paie
 * Conforme aux exigences 1.3, 2.3, 2.4
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayslipDocument {

    private String documentId;
    private Long employeeId;
    private String employeeName;
    private String employeeNumber;
    private YearMonth period;

    // Contenu du document
    private String htmlContent;
    private byte[] pdfContent;
    private String digitalSignature;

    // Métadonnées
    private LocalDateTime generatedDate;
    private LocalDateTime finalizedDate;
    private String generatedBy;
    private PayslipStatus status;

    // Sécurité
    private String checksum;
    private boolean isEncrypted;
    private String encryptionAlgorithm;

    // Informations de distribution
    private boolean isDistributed;
    private LocalDateTime distributedDate;
    private String distributionMethod;

    /**
     * Vérifie si le document est valide
     */
    public boolean isValid() {
        return documentId != null &&
               employeeId != null &&
               period != null &&
               pdfContent != null &&
               digitalSignature != null &&
               status != null;
    }

    /**
     * Vérifie si le document est finalisé
     */
    public boolean isFinalized() {
        return status == PayslipStatus.FINALIZED;
    }

    /**
     * Calcule la taille du document en bytes
     */
    public long getDocumentSize() {
        return pdfContent != null ? pdfContent.length : 0;
    }

    /**
     * Enum pour le statut du bulletin
     */
    public enum PayslipStatus {
        DRAFT("Brouillon"),
        GENERATED("Généré"),
        SIGNED("Signé"),
        FINALIZED("Finalisé"),
        DISTRIBUTED("Distribué"),
        ARCHIVED("Archivé");

        private final String description;

        PayslipStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}