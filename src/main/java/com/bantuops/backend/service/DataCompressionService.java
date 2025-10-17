package com.bantuops.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.*;

/**
 * Service de compression de données pour optimiser le stockage et les transferts
 * Supporte différents algorithmes de compression selon les besoins
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataCompressionService {

    private final PerformanceMonitoringService performanceMonitoringService;
    
    // Cache des données compressées
    private final Map<String, CompressedData> compressionCache = new ConcurrentHashMap<>();
    
    // Statistiques de compression
    private final Map<String, CompressionStats> compressionStats = new ConcurrentHashMap<>();

    /**
     * Compression de données JSON avec GZIP
     */
    public CompressedData compressJson(String jsonData, String identifier) {
        long startTime = System.currentTimeMillis();
        
        try {
            byte[] originalBytes = jsonData.getBytes(StandardCharsets.UTF_8);
            byte[] compressedBytes = compressWithGzip(originalBytes);
            
            long compressionTime = System.currentTimeMillis() - startTime;
            double compressionRatio = (double) compressedBytes.length / originalBytes.length;
            
            CompressedData result = new CompressedData(
                identifier,
                compressedBytes,
                originalBytes.length,
                compressedBytes.length,
                "GZIP",
                LocalDateTime.now(),
                compressionTime
            );
            
            // Mettre en cache si la compression est significative
            if (compressionRatio < 0.8) { // Plus de 20% de réduction
                compressionCache.put(identifier, result);
            }
            
            // Enregistrer les statistiques
            updateCompressionStats("JSON_GZIP", originalBytes.length, compressedBytes.length, compressionTime);
            
            log.debug("JSON compressed - ID: {}, Original: {} bytes, Compressed: {} bytes, Ratio: {:.2f}%, Time: {}ms",
                     identifier, originalBytes.length, compressedBytes.length, compressionRatio * 100, compressionTime);
            
            return result;
            
        } catch (Exception e) {
            log.error("Erreur lors de la compression JSON pour {}: {}", identifier, e.getMessage(), e);
            throw new DataCompressionException("Échec de la compression JSON", e);
        }
    }

    /**
     * Décompression de données JSON
     */
    public String decompressJson(String identifier) {
        CompressedData cachedData = compressionCache.get(identifier);
        if (cachedData == null) {
            throw new DataCompressionException("Données compressées non trouvées pour l'identifiant: " + identifier);
        }
        
        return decompressJson(cachedData);
    }
    
    public String decompressJson(CompressedData compressedData) {
        long startTime = System.currentTimeMillis();
        
        try {
            byte[] decompressedBytes = decompressWithGzip(compressedData.getCompressedData());
            String result = new String(decompressedBytes, StandardCharsets.UTF_8);
            
            long decompressionTime = System.currentTimeMillis() - startTime;
            
            log.debug("JSON decompressed - ID: {}, Size: {} bytes, Time: {}ms",
                     compressedData.getIdentifier(), decompressedBytes.length, decompressionTime);
            
            return result;
            
        } catch (Exception e) {
            log.error("Erreur lors de la décompression JSON pour {}: {}", 
                     compressedData.getIdentifier(), e.getMessage(), e);
            throw new DataCompressionException("Échec de la décompression JSON", e);
        }
    }

    /**
     * Compression de données binaires avec différents algorithmes
     */
    public CompressedData compressBinary(byte[] data, String identifier, CompressionAlgorithm algorithm) {
        long startTime = System.currentTimeMillis();
        
        try {
            byte[] compressedBytes;
            String algorithmName;
            
            switch (algorithm) {
                case GZIP:
                    compressedBytes = compressWithGzip(data);
                    algorithmName = "GZIP";
                    break;
                case DEFLATE:
                    compressedBytes = compressWithDeflate(data);
                    algorithmName = "DEFLATE";
                    break;
                case LZ4:
                    compressedBytes = compressWithLZ4(data);
                    algorithmName = "LZ4";
                    break;
                default:
                    throw new IllegalArgumentException("Algorithme de compression non supporté: " + algorithm);
            }
            
            long compressionTime = System.currentTimeMillis() - startTime;
            double compressionRatio = (double) compressedBytes.length / data.length;
            
            CompressedData result = new CompressedData(
                identifier,
                compressedBytes,
                data.length,
                compressedBytes.length,
                algorithmName,
                LocalDateTime.now(),
                compressionTime
            );
            
            // Mettre en cache si efficace
            if (compressionRatio < 0.9) {
                compressionCache.put(identifier, result);
            }
            
            // Enregistrer les statistiques
            updateCompressionStats("BINARY_" + algorithmName, data.length, compressedBytes.length, compressionTime);
            
            log.debug("Binary data compressed - ID: {}, Algorithm: {}, Original: {} bytes, Compressed: {} bytes, Ratio: {:.2f}%, Time: {}ms",
                     identifier, algorithmName, data.length, compressedBytes.length, compressionRatio * 100, compressionTime);
            
            return result;
            
        } catch (Exception e) {
            log.error("Erreur lors de la compression binaire pour {}: {}", identifier, e.getMessage(), e);
            throw new DataCompressionException("Échec de la compression binaire", e);
        }
    }

    /**
     * Décompression de données binaires
     */
    public byte[] decompressBinary(CompressedData compressedData) {
        long startTime = System.currentTimeMillis();
        
        try {
            byte[] decompressedBytes;
            
            switch (compressedData.getAlgorithm()) {
                case "GZIP":
                    decompressedBytes = decompressWithGzip(compressedData.getCompressedData());
                    break;
                case "DEFLATE":
                    decompressedBytes = decompressWithDeflate(compressedData.getCompressedData());
                    break;
                case "LZ4":
                    decompressedBytes = decompressWithLZ4(compressedData.getCompressedData());
                    break;
                default:
                    throw new IllegalArgumentException("Algorithme de décompression non supporté: " + compressedData.getAlgorithm());
            }
            
            long decompressionTime = System.currentTimeMillis() - startTime;
            
            log.debug("Binary data decompressed - ID: {}, Algorithm: {}, Size: {} bytes, Time: {}ms",
                     compressedData.getIdentifier(), compressedData.getAlgorithm(), 
                     decompressedBytes.length, decompressionTime);
            
            return decompressedBytes;
            
        } catch (Exception e) {
            log.error("Erreur lors de la décompression binaire pour {}: {}", 
                     compressedData.getIdentifier(), e.getMessage(), e);
            throw new DataCompressionException("Échec de la décompression binaire", e);
        }
    }

    /**
     * Compression de rapports volumineux avec optimisation
     */
    public CompressedData compressReport(String reportData, String reportId, ReportType reportType) {
        // Choisir l'algorithme optimal selon le type de rapport
        CompressionAlgorithm algorithm = selectOptimalAlgorithm(reportType, reportData.length());
        
        // Pré-traitement des données selon le type
        String processedData = preprocessReportData(reportData, reportType);
        
        return compressJson(processedData, reportId + "_" + reportType.name());
    }

    /**
     * Compression en lot pour les gros volumes
     */
    public List<CompressedData> compressBatch(Map<String, String> dataMap, CompressionAlgorithm algorithm) {
        List<CompressedData> results = new ArrayList<>();
        long totalStartTime = System.currentTimeMillis();
        
        log.info("Début de la compression en lot - {} éléments avec {}", dataMap.size(), algorithm);
        
        dataMap.entrySet().parallelStream().forEach(entry -> {
            try {
                byte[] data = entry.getValue().getBytes(StandardCharsets.UTF_8);
                CompressedData compressed = compressBinary(data, entry.getKey(), algorithm);
                synchronized (results) {
                    results.add(compressed);
                }
            } catch (Exception e) {
                log.error("Erreur lors de la compression de {}: {}", entry.getKey(), e.getMessage());
            }
        });
        
        long totalTime = System.currentTimeMillis() - totalStartTime;
        
        log.info("Compression en lot terminée - {} éléments traités en {}ms", 
                results.size(), totalTime);
        
        return results;
    }

    /**
     * Nettoyage du cache avec stratégie LRU
     */
    public void cleanupCache() {
        if (compressionCache.size() > 1000) { // Limite du cache
            List<Map.Entry<String, CompressedData>> entries = new ArrayList<>(compressionCache.entrySet());
            
            // Trier par date de création (plus ancien en premier)
            entries.sort((e1, e2) -> e1.getValue().getCreatedAt().compareTo(e2.getValue().getCreatedAt()));
            
            // Supprimer les 20% plus anciens
            int toRemove = entries.size() / 5;
            for (int i = 0; i < toRemove; i++) {
                compressionCache.remove(entries.get(i).getKey());
            }
            
            log.info("Cache nettoyé - {} entrées supprimées", toRemove);
        }
    }

    /**
     * Obtenir les statistiques de compression
     */
    public Map<String, CompressionStats> getCompressionStats() {
        return new HashMap<>(compressionStats);
    }

    /**
     * Réinitialiser les statistiques
     */
    public void resetStats() {
        compressionStats.clear();
        log.info("Statistiques de compression réinitialisées");
    }

    // Méthodes privées d'implémentation

    private byte[] compressWithGzip(byte[] data) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
            gzipOut.write(data);
            gzipOut.finish();
            return baos.toByteArray();
        }
    }

    private byte[] decompressWithGzip(byte[] compressedData) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
             GZIPInputStream gzipIn = new GZIPInputStream(bais);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipIn.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        }
    }

    private byte[] compressWithDeflate(byte[] data) throws IOException {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();
        
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                baos.write(buffer, 0, count);
            }
            deflater.end();
            return baos.toByteArray();
        }
    }

    private byte[] decompressWithDeflate(byte[] compressedData) throws IOException {
        Inflater inflater = new Inflater();
        inflater.setInput(compressedData);
        
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                baos.write(buffer, 0, count);
            }
            inflater.end();
            return baos.toByteArray();
        } catch (DataFormatException e) {
            throw new IOException("Erreur de format lors de la décompression DEFLATE", e);
        }
    }

    private byte[] compressWithLZ4(byte[] data) {
        // Implémentation simplifiée - en production, utiliser LZ4 Java
        // Pour cette démo, on utilise DEFLATE comme fallback
        try {
            return compressWithDeflate(data);
        } catch (IOException e) {
            throw new RuntimeException("Erreur de compression LZ4", e);
        }
    }

    private byte[] decompressWithLZ4(byte[] compressedData) {
        // Implémentation simplifiée - en production, utiliser LZ4 Java
        try {
            return decompressWithDeflate(compressedData);
        } catch (IOException e) {
            throw new RuntimeException("Erreur de décompression LZ4", e);
        }
    }

    private CompressionAlgorithm selectOptimalAlgorithm(ReportType reportType, int dataSize) {
        switch (reportType) {
            case FINANCIAL_REPORT:
                return dataSize > 100000 ? CompressionAlgorithm.GZIP : CompressionAlgorithm.DEFLATE;
            case PAYROLL_REPORT:
                return CompressionAlgorithm.GZIP; // Meilleur pour les données répétitives
            case ATTENDANCE_REPORT:
                return CompressionAlgorithm.LZ4; // Plus rapide pour les gros volumes
            default:
                return CompressionAlgorithm.GZIP;
        }
    }

    private String preprocessReportData(String reportData, ReportType reportType) {
        switch (reportType) {
            case FINANCIAL_REPORT:
                // Normaliser les formats de nombres pour améliorer la compression
                return reportData.replaceAll("\\s+", " ").trim();
            case PAYROLL_REPORT:
                // Optimiser les données répétitives
                return reportData.replaceAll("\"null\"", "null");
            default:
                return reportData;
        }
    }

    private void updateCompressionStats(String type, int originalSize, int compressedSize, long compressionTime) {
        compressionStats.compute(type, (key, existing) -> {
            if (existing == null) {
                return new CompressionStats(type, 1, originalSize, compressedSize, compressionTime);
            } else {
                return existing.addOperation(originalSize, compressedSize, compressionTime);
            }
        });
    }

    // Classes internes et énumérations

    public enum CompressionAlgorithm {
        GZIP, DEFLATE, LZ4
    }

    public enum ReportType {
        FINANCIAL_REPORT, PAYROLL_REPORT, ATTENDANCE_REPORT, AUDIT_REPORT
    }

    public static class CompressedData {
        private final String identifier;
        private final byte[] compressedData;
        private final int originalSize;
        private final int compressedSize;
        private final String algorithm;
        private final LocalDateTime createdAt;
        private final long compressionTime;

        public CompressedData(String identifier, byte[] compressedData, int originalSize, 
                            int compressedSize, String algorithm, LocalDateTime createdAt, 
                            long compressionTime) {
            this.identifier = identifier;
            this.compressedData = compressedData.clone();
            this.originalSize = originalSize;
            this.compressedSize = compressedSize;
            this.algorithm = algorithm;
            this.createdAt = createdAt;
            this.compressionTime = compressionTime;
        }

        // Getters
        public String getIdentifier() { return identifier; }
        public byte[] getCompressedData() { return compressedData.clone(); }
        public int getOriginalSize() { return originalSize; }
        public int getCompressedSize() { return compressedSize; }
        public String getAlgorithm() { return algorithm; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public long getCompressionTime() { return compressionTime; }
        
        public double getCompressionRatio() {
            return (double) compressedSize / originalSize;
        }
        
        public double getCompressionPercentage() {
            return (1.0 - getCompressionRatio()) * 100;
        }
    }

    public static class CompressionStats {
        private final String type;
        private int operationCount;
        private long totalOriginalSize;
        private long totalCompressedSize;
        private long totalCompressionTime;

        public CompressionStats(String type, int operationCount, long totalOriginalSize, 
                              long totalCompressedSize, long totalCompressionTime) {
            this.type = type;
            this.operationCount = operationCount;
            this.totalOriginalSize = totalOriginalSize;
            this.totalCompressedSize = totalCompressedSize;
            this.totalCompressionTime = totalCompressionTime;
        }

        public CompressionStats addOperation(int originalSize, int compressedSize, long compressionTime) {
            return new CompressionStats(
                this.type,
                this.operationCount + 1,
                this.totalOriginalSize + originalSize,
                this.totalCompressedSize + compressedSize,
                this.totalCompressionTime + compressionTime
            );
        }

        // Getters
        public String getType() { return type; }
        public int getOperationCount() { return operationCount; }
        public long getTotalOriginalSize() { return totalOriginalSize; }
        public long getTotalCompressedSize() { return totalCompressedSize; }
        public long getTotalCompressionTime() { return totalCompressionTime; }
        
        public double getAverageCompressionRatio() {
            return totalOriginalSize > 0 ? (double) totalCompressedSize / totalOriginalSize : 0;
        }
        
        public double getAverageCompressionTime() {
            return operationCount > 0 ? (double) totalCompressionTime / operationCount : 0;
        }
        
        public long getTotalSpaceSaved() {
            return totalOriginalSize - totalCompressedSize;
        }
    }

    public static class DataCompressionException extends RuntimeException {
        public DataCompressionException(String message) {
            super(message);
        }
        
        public DataCompressionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}