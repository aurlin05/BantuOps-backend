package com.bantuops.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Service de validation des numéros de téléphone sénégalais
 * Conforme aux exigences 3.1, 3.2, 3.3, 3.4, 3.5, 3.6 pour la validation téléphonique
 */
@Service
@Slf4j
public class SenegalPhoneNumberValidator {

    private static final String COUNTRY_CODE = "221";
    private static final Pattern PHONE_PATTERN = Pattern.compile("^(\\+221|00221)?[0-9]{8,9}$");
    
    // Préfixes des opérateurs mobiles sénégalais
    private static final Map<String, String> MOBILE_OPERATORS = new HashMap<>();
    
    // Préfixes des numéros fixes par région
    private static final Map<String, String> FIXED_LINE_REGIONS = new HashMap<>();
    
    static {
        // Opérateurs mobiles
        MOBILE_OPERATORS.put("70", "Sonatel (Orange)");
        MOBILE_OPERATORS.put("75", "Sonatel (Orange)");
        MOBILE_OPERATORS.put("76", "Sonatel (Orange)");
        MOBILE_OPERATORS.put("77", "Sonatel (Orange)");
        MOBILE_OPERATORS.put("78", "Sonatel (Orange)");
        MOBILE_OPERATORS.put("79", "Sonatel (Orange)"); // Nouveau préfixe
        
        // Autres opérateurs
        MOBILE_OPERATORS.put("72", "Tigo");
        MOBILE_OPERATORS.put("73", "Tigo");
        MOBILE_OPERATORS.put("74", "Tigo");
        
        // Numéros fixes par région
        FIXED_LINE_REGIONS.put("33", "Dakar et banlieue");
        FIXED_LINE_REGIONS.put("34", "Thiès");
        FIXED_LINE_REGIONS.put("35", "Kaolack");
        FIXED_LINE_REGIONS.put("36", "Ziguinchor");
        FIXED_LINE_REGIONS.put("37", "Louga");
        FIXED_LINE_REGIONS.put("38", "Saint-Louis");
        FIXED_LINE_REGIONS.put("39", "Diourbel");
        FIXED_LINE_REGIONS.put("30", "Tambacounda");
        FIXED_LINE_REGIONS.put("31", "Kolda");
        FIXED_LINE_REGIONS.put("32", "Fatick");
    }

    /**
     * Valide un numéro de téléphone sénégalais
     */
    public boolean isValid(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            log.debug("Numéro de téléphone vide ou null");
            return false;
        }

        String cleanPhone = normalizePhoneNumber(phoneNumber);
        
        // Vérification du format de base
        if (!PHONE_PATTERN.matcher(phoneNumber).matches()) {
            log.debug("Format de numéro de téléphone invalide: {}", phoneNumber);
            return false;
        }

        // Extraction du numéro local
        String localNumber = extractLocalNumber(phoneNumber);
        if (localNumber == null) {
            return false;
        }

        // Validation selon le type (mobile ou fixe)
        boolean isValid = isValidMobileNumber(localNumber) || isValidFixedLineNumber(localNumber);
        
        if (isValid) {
            log.debug("Numéro de téléphone valide: {} -> {}", phoneNumber, localNumber);
        } else {
            log.debug("Numéro de téléphone invalide: {} -> {}", phoneNumber, localNumber);
        }
        
        return isValid;
    }

    /**
     * Normalise un numéro de téléphone en supprimant les espaces et caractères spéciaux
     */
    public String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        
        return phoneNumber.trim()
                         .replaceAll("[\\s\\-\\(\\)\\.]", "")
                         .replaceAll("^00", "+");
    }

    /**
     * Formate un numéro de téléphone au format international
     */
    public String formatInternational(String phoneNumber) {
        if (!isValid(phoneNumber)) {
            throw new IllegalArgumentException("Numéro de téléphone invalide: " + phoneNumber);
        }

        String localNumber = extractLocalNumber(phoneNumber);
        return "+" + COUNTRY_CODE + localNumber;
    }

    /**
     * Formate un numéro de téléphone au format national
     */
    public String formatNational(String phoneNumber) {
        if (!isValid(phoneNumber)) {
            throw new IllegalArgumentException("Numéro de téléphone invalide: " + phoneNumber);
        }

        String localNumber = extractLocalNumber(phoneNumber);
        
        // Formatage avec espaces pour la lisibilité
        if (localNumber.length() == 9) {
            // Mobile: XX XXX XX XX
            return localNumber.substring(0, 2) + " " + 
                   localNumber.substring(2, 5) + " " + 
                   localNumber.substring(5, 7) + " " + 
                   localNumber.substring(7, 9);
        } else if (localNumber.length() == 8) {
            // Fixe: XX XXX XXX
            return localNumber.substring(0, 2) + " " + 
                   localNumber.substring(2, 5) + " " + 
                   localNumber.substring(5, 8);
        }
        
        return localNumber;
    }

    /**
     * Extrait le numéro local (sans indicatif pays)
     */
    private String extractLocalNumber(String phoneNumber) {
        String cleanPhone = normalizePhoneNumber(phoneNumber);
        
        if (cleanPhone.startsWith("+" + COUNTRY_CODE)) {
            return cleanPhone.substring(4);
        } else if (cleanPhone.startsWith("00" + COUNTRY_CODE)) {
            return cleanPhone.substring(5);
        } else if (cleanPhone.startsWith(COUNTRY_CODE)) {
            return cleanPhone.substring(3);
        } else if (cleanPhone.matches("^[0-9]{8,9}$")) {
            return cleanPhone;
        }
        
        return null;
    }

    /**
     * Valide un numéro mobile sénégalais
     */
    private boolean isValidMobileNumber(String localNumber) {
        if (localNumber == null || localNumber.length() != 9) {
            return false;
        }

        String prefix = localNumber.substring(0, 2);
        return MOBILE_OPERATORS.containsKey(prefix);
    }

    /**
     * Valide un numéro fixe sénégalais
     */
    private boolean isValidFixedLineNumber(String localNumber) {
        if (localNumber == null || localNumber.length() != 8) {
            return false;
        }

        String prefix = localNumber.substring(0, 2);
        return FIXED_LINE_REGIONS.containsKey(prefix);
    }

    /**
     * Détermine le type de numéro (mobile ou fixe)
     */
    public PhoneNumberType getPhoneNumberType(String phoneNumber) {
        if (!isValid(phoneNumber)) {
            return PhoneNumberType.INVALID;
        }

        String localNumber = extractLocalNumber(phoneNumber);
        
        if (isValidMobileNumber(localNumber)) {
            return PhoneNumberType.MOBILE;
        } else if (isValidFixedLineNumber(localNumber)) {
            return PhoneNumberType.FIXED_LINE;
        }
        
        return PhoneNumberType.UNKNOWN;
    }

    /**
     * Retourne l'opérateur mobile ou la région pour un numéro
     */
    public String getOperatorOrRegion(String phoneNumber) {
        if (!isValid(phoneNumber)) {
            throw new IllegalArgumentException("Numéro de téléphone invalide: " + phoneNumber);
        }

        String localNumber = extractLocalNumber(phoneNumber);
        String prefix = localNumber.substring(0, 2);
        
        if (MOBILE_OPERATORS.containsKey(prefix)) {
            return MOBILE_OPERATORS.get(prefix);
        } else if (FIXED_LINE_REGIONS.containsKey(prefix)) {
            return FIXED_LINE_REGIONS.get(prefix);
        }
        
        return "Opérateur/Région inconnue";
    }

    /**
     * Vérifie si un numéro est un mobile
     */
    public boolean isMobile(String phoneNumber) {
        return getPhoneNumberType(phoneNumber) == PhoneNumberType.MOBILE;
    }

    /**
     * Vérifie si un numéro est un fixe
     */
    public boolean isFixedLine(String phoneNumber) {
        return getPhoneNumberType(phoneNumber) == PhoneNumberType.FIXED_LINE;
    }

    /**
     * Génère un numéro mobile valide pour les tests
     */
    public String generateValidMobileNumber(String operator) {
        String prefix;
        switch (operator.toLowerCase()) {
            case "orange":
            case "sonatel":
                prefix = "77"; // Préfixe Orange le plus courant
                break;
            case "tigo":
                prefix = "72";
                break;
            default:
                prefix = "77"; // Par défaut Orange
        }
        
        // Génération d'un numéro aléatoire de 7 chiffres
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < 7; i++) {
            sb.append((int) (Math.random() * 10));
        }
        
        return sb.toString();
    }

    /**
     * Génère un numéro fixe valide pour les tests
     */
    public String generateValidFixedLineNumber(String region) {
        String prefix;
        switch (region.toLowerCase()) {
            case "dakar":
                prefix = "33";
                break;
            case "thiès":
                prefix = "34";
                break;
            case "kaolack":
                prefix = "35";
                break;
            case "ziguinchor":
                prefix = "36";
                break;
            default:
                prefix = "33"; // Par défaut Dakar
        }
        
        // Génération d'un numéro aléatoire de 6 chiffres
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < 6; i++) {
            sb.append((int) (Math.random() * 10));
        }
        
        return sb.toString();
    }

    /**
     * Extrait les informations détaillées d'un numéro valide
     */
    public PhoneNumberInfo extractInfo(String phoneNumber) {
        if (!isValid(phoneNumber)) {
            throw new IllegalArgumentException("Numéro de téléphone invalide: " + phoneNumber);
        }

        String localNumber = extractLocalNumber(phoneNumber);
        PhoneNumberType type = getPhoneNumberType(phoneNumber);
        String operatorOrRegion = getOperatorOrRegion(phoneNumber);
        String international = formatInternational(phoneNumber);
        String national = formatNational(phoneNumber);
        
        return new PhoneNumberInfo(localNumber, type, operatorOrRegion, international, national);
    }

    /**
     * Énumération des types de numéros
     */
    public enum PhoneNumberType {
        MOBILE("Mobile"),
        FIXED_LINE("Fixe"),
        INVALID("Invalide"),
        UNKNOWN("Inconnu");

        private final String description;

        PhoneNumberType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Classe pour les informations détaillées d'un numéro
     */
    public static class PhoneNumberInfo {
        private final String localNumber;
        private final PhoneNumberType type;
        private final String operatorOrRegion;
        private final String internationalFormat;
        private final String nationalFormat;

        public PhoneNumberInfo(String localNumber, PhoneNumberType type, String operatorOrRegion,
                              String internationalFormat, String nationalFormat) {
            this.localNumber = localNumber;
            this.type = type;
            this.operatorOrRegion = operatorOrRegion;
            this.internationalFormat = internationalFormat;
            this.nationalFormat = nationalFormat;
        }

        public String getLocalNumber() { return localNumber; }
        public PhoneNumberType getType() { return type; }
        public String getOperatorOrRegion() { return operatorOrRegion; }
        public String getInternationalFormat() { return internationalFormat; }
        public String getNationalFormat() { return nationalFormat; }

        @Override
        public String toString() {
            return String.format("PhoneNumberInfo{local='%s', type=%s, operator='%s', intl='%s', nat='%s'}", 
                               localNumber, type, operatorOrRegion, internationalFormat, nationalFormat);
        }
    }
}