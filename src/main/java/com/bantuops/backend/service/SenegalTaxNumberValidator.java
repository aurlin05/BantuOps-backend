package com.bantuops.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Service de validation des numéros fiscaux sénégalais
 * Conforme aux exigences 3.1, 3.2, 3.3, 3.4, 3.5, 3.6 pour la validation fiscale
 */
@Service
@Slf4j
public class SenegalTaxNumberValidator {

    private static final Pattern TAX_NUMBER_PATTERN = Pattern.compile("^[0-9]{13}$");
    
    // Codes de région sénégalais pour validation
    private static final String[] VALID_REGION_CODES = {
        "01", // Dakar
        "02", // Diourbel
        "03", // Fatick
        "04", // Kaffrine
        "05", // Kaolack
        "06", // Kédougou
        "07", // Kolda
        "08", // Louga
        "09", // Matam
        "10", // Saint-Louis
        "11", // Sédhiou
        "12", // Tambacounda
        "13", // Thiès
        "14"  // Ziguinchor
    };

    /**
     * Valide un numéro fiscal sénégalais (NINEA - Numéro d'Identification Nationale des Entreprises et Associations)
     * Format: RRAAANNNNNNCC où:
     * - RR: Code région (01-14)
     * - AAA: Année d'immatriculation
     * - NNNNNN: Numéro séquentiel
     * - CC: Clé de contrôle
     */
    public boolean isValid(String taxNumber) {
        if (taxNumber == null || taxNumber.trim().isEmpty()) {
            log.debug("Numéro fiscal vide ou null");
            return false;
        }

        String cleanTaxNumber = taxNumber.trim().replaceAll("\\s+", "");
        
        // Vérification du format de base
        if (!TAX_NUMBER_PATTERN.matcher(cleanTaxNumber).matches()) {
            log.debug("Format de numéro fiscal invalide: {}", cleanTaxNumber);
            return false;
        }

        try {
            // Extraction des composants
            String regionCode = cleanTaxNumber.substring(0, 2);
            String yearCode = cleanTaxNumber.substring(2, 5);
            String sequentialNumber = cleanTaxNumber.substring(5, 11);
            String checkDigits = cleanTaxNumber.substring(11, 13);

            // Validation du code région
            if (!isValidRegionCode(regionCode)) {
                log.debug("Code région invalide: {}", regionCode);
                return false;
            }

            // Validation de l'année (doit être raisonnable)
            int year = Integer.parseInt(yearCode);
            int currentYear = java.time.LocalDate.now().getYear() % 1000; // 3 derniers chiffres
            if (year < 0 || year > currentYear + 10) { // Tolérance pour les années futures
                log.debug("Année d'immatriculation invalide: {}", year);
                return false;
            }

            // Validation de la clé de contrôle
            if (!isValidCheckDigits(cleanTaxNumber.substring(0, 11), checkDigits)) {
                log.debug("Clé de contrôle invalide pour le numéro: {}", cleanTaxNumber);
                return false;
            }

            log.debug("Numéro fiscal valide: {}", cleanTaxNumber);
            return true;

        } catch (NumberFormatException e) {
            log.warn("Erreur lors de la validation du numéro fiscal: {}", cleanTaxNumber, e);
            return false;
        }
    }

    /**
     * Valide le code région sénégalais
     */
    private boolean isValidRegionCode(String regionCode) {
        for (String validCode : VALID_REGION_CODES) {
            if (validCode.equals(regionCode)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Valide la clé de contrôle du numéro fiscal
     * Utilise l'algorithme de Luhn modifié pour les numéros fiscaux sénégalais
     */
    private boolean isValidCheckDigits(String baseNumber, String checkDigits) {
        try {
            int calculatedCheck = calculateCheckDigits(baseNumber);
            int providedCheck = Integer.parseInt(checkDigits);
            
            return calculatedCheck == providedCheck;
            
        } catch (NumberFormatException e) {
            log.warn("Erreur lors du calcul de la clé de contrôle: {}", baseNumber, e);
            return false;
        }
    }

    /**
     * Calcule la clé de contrôle pour un numéro fiscal
     */
    public int calculateCheckDigits(String baseNumber) {
        if (baseNumber == null || baseNumber.length() != 11) {
            throw new IllegalArgumentException("Le numéro de base doit contenir exactement 11 chiffres");
        }

        int sum = 0;
        int[] weights = {2, 3, 4, 5, 6, 7, 2, 3, 4, 5, 6}; // Poids pour chaque position

        for (int i = 0; i < baseNumber.length(); i++) {
            int digit = Integer.parseInt(String.valueOf(baseNumber.charAt(i)));
            sum += digit * weights[i];
        }

        int remainder = sum % 97;
        return 97 - remainder;
    }

    /**
     * Génère un numéro fiscal valide pour les tests
     */
    public String generateValidTaxNumber(String regionCode, int year, int sequentialNumber) {
        if (!isValidRegionCode(regionCode)) {
            throw new IllegalArgumentException("Code région invalide: " + regionCode);
        }

        String yearStr = String.format("%03d", year % 1000);
        String seqStr = String.format("%06d", sequentialNumber);
        String baseNumber = regionCode + yearStr + seqStr;
        
        int checkDigits = calculateCheckDigits(baseNumber);
        String checkStr = String.format("%02d", checkDigits);
        
        return baseNumber + checkStr;
    }

    /**
     * Extrait les informations d'un numéro fiscal valide
     */
    public TaxNumberInfo extractInfo(String taxNumber) {
        if (!isValid(taxNumber)) {
            throw new IllegalArgumentException("Numéro fiscal invalide: " + taxNumber);
        }

        String cleanTaxNumber = taxNumber.trim().replaceAll("\\s+", "");
        
        String regionCode = cleanTaxNumber.substring(0, 2);
        int year = Integer.parseInt(cleanTaxNumber.substring(2, 5));
        int sequentialNumber = Integer.parseInt(cleanTaxNumber.substring(5, 11));
        
        return new TaxNumberInfo(regionCode, getRegionName(regionCode), year, sequentialNumber);
    }

    /**
     * Retourne le nom de la région à partir du code
     */
    private String getRegionName(String regionCode) {
        switch (regionCode) {
            case "01": return "Dakar";
            case "02": return "Diourbel";
            case "03": return "Fatick";
            case "04": return "Kaffrine";
            case "05": return "Kaolack";
            case "06": return "Kédougou";
            case "07": return "Kolda";
            case "08": return "Louga";
            case "09": return "Matam";
            case "10": return "Saint-Louis";
            case "11": return "Sédhiou";
            case "12": return "Tambacounda";
            case "13": return "Thiès";
            case "14": return "Ziguinchor";
            default: return "Région inconnue";
        }
    }

    /**
     * Classe pour les informations extraites d'un numéro fiscal
     */
    public static class TaxNumberInfo {
        private final String regionCode;
        private final String regionName;
        private final int registrationYear;
        private final int sequentialNumber;

        public TaxNumberInfo(String regionCode, String regionName, int registrationYear, int sequentialNumber) {
            this.regionCode = regionCode;
            this.regionName = regionName;
            this.registrationYear = registrationYear;
            this.sequentialNumber = sequentialNumber;
        }

        public String getRegionCode() { return regionCode; }
        public String getRegionName() { return regionName; }
        public int getRegistrationYear() { return registrationYear; }
        public int getSequentialNumber() { return sequentialNumber; }

        @Override
        public String toString() {
            return String.format("TaxNumberInfo{region='%s (%s)', year=%d, seq=%d}", 
                               regionName, regionCode, registrationYear, sequentialNumber);
        }
    }
}