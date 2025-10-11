package com.bantuops.backend.service;

import com.bantuops.backend.dto.AuditReportResponse;
import com.bantuops.backend.entity.AuditLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service d'export de données d'audit avec chiffrement
 * Conforme aux exigences 7.6, 2.4, 2.5 pour l'export sécurisé
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditDataExporter {

    private final DataEncryptionService dataEncryptionService;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter FILENAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * Exporte les données d'audit au format JSON chiffré
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    public byte[] exportAuditDataAsEncryptedJson(AuditReportResponse reportData) {
        log.info("Export des données d'audit - Rapport ID: {}", reportData.getReportId());

        try {
            // Sérialisation en JSON
            String jsonData = objectMapper.writeValueAsString(reportData);
            
            // Chiffrement des données
            String encryptedData = dataEncryptionService.encrypt(jsonData);
            
            log.info("Données d'audit exportées et chiffrées - Taille: {} bytes", encryptedData.length());
            
            return encryptedData.getBytes();

        } catch (Exception e) {
            log.error("Erreur lors de l'export des données d'audit: {}", e.getMessage(), e);
            throw new RuntimeException("Échec de l'export des données d'audit", e);
        }
    }

    /**
     * Exporte les données d'audit au format CSV
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    public byte[] exportAuditDataAsCsv(List<AuditLog> auditLogs) {
        log.info("Export des logs d'audit au format CSV - {} entrées", auditLogs.size());

        try {
            StringBuilder csvBuilder = new StringBuilder();
            
            // En-têtes CSV
            csvBuilder.append("Timestamp,User ID,Action,Entity Type,Entity ID,IP Address,User Agent,Old Values,New Values\n");
            
            // Données
            for (AuditLog log : auditLogs) {
                csvBuilder.append(formatCsvRow(
                    log.getTimestamp() != null ? log.getTimestamp().toString() : "",
                    log.getUserId() != null ? log.getUserId() : "",
                    log.getAction() != null ? log.getAction().toString() : "",
                    log.getEntityType() != null ? log.getEntityType() : "",
                    log.getEntityId() != null ? log.getEntityId().toString() : "",
                    log.getIpAddress() != null ? log.getIpAddress() : "",
                    log.getUserAgent() != null ? log.getUserAgent() : "",
                    log.getOldValues() != null ? sanitizeForCsv(log.getOldValues()) : "",
                    log.getNewValues() != null ? sanitizeForCsv(log.getNewValues()) : ""
                ));
                csvBuilder.append("\n");
            }
            
            String csvData = csvBuilder.toString();
            log.info("Export CSV terminé - Taille: {} bytes", csvData.length());
            
            return csvData.getBytes();

        } catch (Exception e) {
            log.error("Erreur lors de l'export CSV: {}", e.getMessage(), e);
            throw new RuntimeException("Échec de l'export CSV", e);
        }
    }

    /**
     * Exporte les données d'audit dans une archive ZIP chiffrée
     */
    @PreAuthorize("hasRole('ADMIN')")
    public byte[] exportAuditDataAsEncryptedZip(AuditReportResponse reportData, List<AuditLog> auditLogs) {
        log.info("Export des données d'audit en archive ZIP chiffrée");

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ZipOutputStream zos = new ZipOutputStream(baos);

            // Ajout du rapport JSON
            String reportJson = objectMapper.writeValueAsString(reportData);
            String encryptedReport = dataEncryptionService.encrypt(reportJson);
            addToZip(zos, "audit_report_" + getCurrentTimestamp() + ".json.enc", encryptedReport.getBytes());

            // Ajout des logs CSV
            byte[] csvData = exportAuditDataAsCsv(auditLogs);
            String encryptedCsv = dataEncryptionService.encrypt(new String(csvData));
            addToZip(zos, "audit_logs_" + getCurrentTimestamp() + ".csv.enc", encryptedCsv.getBytes());

            // Ajout des métadonnées
            String metadata = createMetadata(reportData, auditLogs.size());
            addToZip(zos, "metadata.json", metadata.getBytes());

            zos.close();
            byte[] zipData = baos.toByteArray();
            
            log.info("Archive ZIP créée - Taille: {} bytes", zipData.length);
            
            return zipData;

        } catch (Exception e) {
            log.error("Erreur lors de la création de l'archive ZIP: {}", e.getMessage(), e);
            throw new RuntimeException("Échec de la création de l'archive ZIP", e);
        }
    }

    /**
     * Exporte uniquement les données sensibles avec chiffrement renforcé
     */
    @PreAuthorize("hasRole('ADMIN')")
    public byte[] exportSensitiveDataOnly(List<AuditLog> auditLogs) {
        log.info("Export des données sensibles uniquement");

        try {
            // Filtrer les logs contenant des données sensibles
            List<AuditLog> sensitiveLogs = auditLogs.stream()
                    .filter(AuditLog::isSensitiveData)
                    .toList();

            if (sensitiveLogs.isEmpty()) {
                log.info("Aucune donnée sensible trouvée dans les logs");
                return new byte[0];
            }

            // Créer un objet de données sensibles
            Map<String, Object> sensitiveData = Map.of(
                    "exportTimestamp", LocalDateTime.now(),
                    "totalSensitiveLogs", sensitiveLogs.size(),
                    "sensitiveLogs", sensitiveLogs,
                    "exportedBy", getCurrentUserId(),
                    "securityLevel", "CONFIDENTIAL"
            );

            // Double chiffrement pour les données sensibles
            String jsonData = objectMapper.writeValueAsString(sensitiveData);
            String firstEncryption = dataEncryptionService.encrypt(jsonData);
            String doubleEncryption = dataEncryptionService.encrypt(firstEncryption);

            log.info("Export des données sensibles terminé - {} logs exportés", sensitiveLogs.size());
            
            return doubleEncryption.getBytes();

        } catch (Exception e) {
            log.error("Erreur lors de l'export des données sensibles: {}", e.getMessage(), e);
            throw new RuntimeException("Échec de l'export des données sensibles", e);
        }
    }

    /**
     * Génère un nom de fichier sécurisé pour l'export
     */
    public String generateSecureFilename(String prefix, String extension) {
        String timestamp = getCurrentTimestamp();
        String userId = getCurrentUserId();
        return String.format("%s_%s_%s.%s", prefix, timestamp, userId, extension);
    }

    // ==================== MÉTHODES UTILITAIRES ====================

    private void addToZip(ZipOutputStream zos, String filename, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(filename);
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }

    private String createMetadata(AuditReportResponse reportData, int logCount) {
        try {
            Map<String, Object> metadata = Map.of(
                    "exportTimestamp", LocalDateTime.now(),
                    "reportId", reportData.getReportId(),
                    "reportPeriod", Map.of(
                            "startDate", reportData.getStartDate(),
                            "endDate", reportData.getEndDate()
                    ),
                    "totalLogs", logCount,
                    "exportedBy", getCurrentUserId(),
                    "encryptionUsed", true,
                    "format", "JSON/CSV",
                    "version", "1.0"
            );
            
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            log.error("Erreur lors de la création des métadonnées: {}", e.getMessage(), e);
            return "{}";
        }
    }

    private String formatCsvRow(String... values) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                row.append(",");
            }
            // Échapper les guillemets et entourer de guillemets si nécessaire
            String value = values[i].replace("\"", "\"\"");
            if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
                row.append("\"").append(value).append("\"");
            } else {
                row.append(value);
            }
        }
        return row.toString();
    }

    private String sanitizeForCsv(String value) {
        if (value == null) return "";
        // Remplacer les retours à la ligne par des espaces
        return value.replace("\n", " ").replace("\r", " ");
    }

    private String getCurrentTimestamp() {
        return LocalDateTime.now().format(FILENAME_FORMATTER);
    }

    private String getCurrentUserId() {
        // TODO: Récupérer depuis le contexte de sécurité Spring
        return "SYSTEM";
    }
}