package com.bantuops.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Service de validation des comptes bancaires sénégalais
 * Conforme aux exigences 3.1, 3.2, 3.3, 3.4, 3.5, 3.6 pour la validation bancaire
 */
@Service
@Slf4j
public class SenegalBankAccountValidator {

    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile("^[0-9]{10,16}$");
    private static final Pattern RIB_PATTERN = Pattern.compile("^[0-9]{5}[0-9]{5}[0-9]{11}[0-9]{2}$");
    
    // Codes des principales banques sénégalaises
    private static final Map<String, String> BANK_CODES = new HashMap<>();
    
    static {
        // Banques commerciales principales
        BANK_CODES.put("10001", "Banque Centrale des États de l'Afrique de l'Ouest (BCEAO)");
        BANK_CODES.put("10002", "Société Générale de Banques au Sénégal (SGBS)");
        BANK_CODES.put("10003", "Banque Internationale pour le Commerce et l'Industrie du Sénégal (BICIS)");
        BANK_CODES.put("10004", "Crédit Lyonnais Sénégal (CLS)");
        BANK_CODES.put("10005", "Banque de l'Habitat du Sénégal (BHS)");
        BANK_CODES.put("10006", "Compagnie Bancaire de l'Afrique Occidentale (CBAO)");
        BANK_CODES.put("10007", "Banque Islamique du Sénégal (BIS)");
        BANK_CODES.put("10008", "Ecobank Sénégal");
        BANK_CODES.put("10009", "Banque Atlantique Sénégal");
        BANK_CODES.put("10010", "Citibank Sénégal");
        BANK_CODES.put("10011", "United Bank for Africa (UBA) Sénégal");
        BANK_CODES.put("10012", "Banque Agricole");
        BANK_CODES.put("10013", "Banque Sahélo-Saharienne pour l'Investissement et le Commerce (BSIC)");
        BANK_CODES.put("10014", "Caisse Nationale de Crédit Agricole du Sénégal (CNCAS)");
        BANK_CODES.put("10015", "Banque Régionale de Solidarité (BRS)");
        
        // Institutions de microfinance
        BANK_CODES.put("20001", "Crédit Mutuel du Sénégal (CMS)");
        BANK_CODES.put("20002", "Pamécas");
        BANK_CODES.put("20003", "ACEP");
        BANK_CODES.put("20004", "Microcred");
        
        // Services financiers mobiles
        BANK_CODES.put("30001", "Orange Money");
        BANK_CODES.put("30002", "Free Money");
        BANK_CODES.put("30003", "Tigo Cash");
        BANK_CODES.put("30004", "Wari");
    }

    /**
     * Valide un numéro de compte bancaire sénégalais
     */
    public boolean isValidAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            log.debug("Numéro de compte vide ou null");
            return false;
        }

        String cleanAccount = accountNumber.trim().replaceAll("[\\s-]", "");
        
        // Vérification du format de base
        if (!ACCOUNT_NUMBER_PATTERN.matcher(cleanAccount).matches()) {
            log.debug("Format de numéro de compte invalide: {}", cleanAccount);
            return false;
        }

        // Validation spécifique selon la longueur
        if (cleanAccount.length() >= 12) {
            return validateFullAccountNumber(cleanAccount);
        } else {
            return validateSimpleAccountNumber(cleanAccount);
        }
    }

    /**
     * Valide un RIB (Relevé d'Identité Bancaire) sénégalais complet
     * Format: BBBBBGGGGGCCCCCCCCCCCCC où:
     * - BBBBB: Code banque (5 chiffres)
     * - GGGGG: Code guichet (5 chiffres)
     * - CCCCCCCCCCC: Numéro de compte (11 chiffres)
     * - CC: Clé RIB (2 chiffres)
     */
    public boolean isValidRIB(String rib) {
        if (rib == null || rib.trim().isEmpty()) {
            log.debug("RIB vide ou null");
            return false;
        }

        String cleanRib = rib.trim().replaceAll("[\\s-]", "");
        
        // Vérification du format
        if (!RIB_PATTERN.matcher(cleanRib).matches()) {
            log.debug("Format RIB invalide: {}", cleanRib);
            return false;
        }

        try {
            String bankCode = cleanRib.substring(0, 5);
            String branchCode = cleanRib.substring(5, 10);
            String accountNumber = cleanRib.substring(10, 21);
            String ribKey = cleanRib.substring(21, 23);

            // Validation du code banque
            if (!isValidBankCode(bankCode)) {
                log.debug("Code banque invalide: {}", bankCode);
                return false;
            }

            // Validation de la clé RIB
            if (!isValidRibKey(bankCode + branchCode + accountNumber, ribKey)) {
                log.debug("Clé RIB invalide pour: {}", cleanRib);
                return false;
            }

            log.debug("RIB valide: {}", cleanRib);
            return true;

        } catch (Exception e) {
            log.warn("Erreur lors de la validation du RIB: {}", cleanRib, e);
            return false;
        }
    }

    /**
     * Valide un numéro de compte mobile money sénégalais
     */
    public boolean isValidMobileMoneyAccount(String mobileAccount) {
        if (mobileAccount == null || mobileAccount.trim().isEmpty()) {
            return false;
        }

        String cleanAccount = mobileAccount.trim().replaceAll("[\\s-]", "");
        
        // Format général pour mobile money: préfixe + numéro de téléphone
        if (cleanAccount.length() < 8 || cleanAccount.length() > 15) {
            return false;
        }

        // Validation des préfixes connus
        return cleanAccount.startsWith("70") || cleanAccount.startsWith("75") || 
               cleanAccount.startsWith("76") || cleanAccount.startsWith("77") || 
               cleanAccount.startsWith("78");
    }

    /**
     * Valide un numéro de compte simple (sans RIB complet)
     */
    private boolean validateSimpleAccountNumber(String accountNumber) {
        // Validation basique pour les comptes courts
        try {
            Long.parseLong(accountNumber);
            return accountNumber.length() >= 10;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Valide un numéro de compte complet avec code banque
     */
    private boolean validateFullAccountNumber(String accountNumber) {
        if (accountNumber.length() < 12) {
            return false;
        }

        String bankCode = accountNumber.substring(0, 5);
        return isValidBankCode(bankCode);
    }

    /**
     * Vérifie si un code banque est valide
     */
    private boolean isValidBankCode(String bankCode) {
        if (bankCode == null || bankCode.length() != 5) {
            return false;
        }

        // Vérification dans la liste des codes connus
        if (BANK_CODES.containsKey(bankCode)) {
            return true;
        }

        // Validation des plages de codes valides
        try {
            int code = Integer.parseInt(bankCode);
            
            // Banques commerciales: 10001-19999
            if (code >= 10001 && code <= 19999) {
                return true;
            }
            
            // Institutions de microfinance: 20001-29999
            if (code >= 20001 && code <= 29999) {
                return true;
            }
            
            // Services financiers mobiles: 30001-39999
            if (code >= 30001 && code <= 39999) {
                return true;
            }
            
            return false;
            
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Valide la clé RIB selon l'algorithme sénégalais
     */
    private boolean isValidRibKey(String baseRib, String ribKey) {
        try {
            int calculatedKey = calculateRibKey(baseRib);
            int providedKey = Integer.parseInt(ribKey);
            
            return calculatedKey == providedKey;
            
        } catch (NumberFormatException e) {
            log.warn("Erreur lors de la validation de la clé RIB: {}", baseRib, e);
            return false;
        }
    }

    /**
     * Calcule la clé RIB selon l'algorithme standard
     */
    public int calculateRibKey(String baseRib) {
        if (baseRib == null || baseRib.length() != 21) {
            throw new IllegalArgumentException("Le RIB de base doit contenir exactement 21 chiffres");
        }

        // Algorithme de calcul de la clé RIB (modulo 97)
        StringBuilder sb = new StringBuilder();
        
        // Conversion des lettres en chiffres si nécessaire (pour les comptes avec lettres)
        for (char c : baseRib.toCharArray()) {
            if (Character.isDigit(c)) {
                sb.append(c);
            } else if (Character.isLetter(c)) {
                // A=10, B=11, ..., Z=35
                sb.append(Character.toUpperCase(c) - 'A' + 10);
            }
        }

        // Calcul du modulo 97
        String ribNumber = sb.toString();
        long remainder = 0;
        
        for (char digit : ribNumber.toCharArray()) {
            remainder = (remainder * 10 + (digit - '0')) % 97;
        }

        return (int) (97 - remainder);
    }

    /**
     * Extrait les informations d'un RIB valide
     */
    public BankAccountInfo extractRibInfo(String rib) {
        if (!isValidRIB(rib)) {
            throw new IllegalArgumentException("RIB invalide: " + rib);
        }

        String cleanRib = rib.trim().replaceAll("[\\s-]", "");
        
        String bankCode = cleanRib.substring(0, 5);
        String branchCode = cleanRib.substring(5, 10);
        String accountNumber = cleanRib.substring(10, 21);
        String ribKey = cleanRib.substring(21, 23);
        
        String bankName = BANK_CODES.getOrDefault(bankCode, "Banque inconnue");
        
        return new BankAccountInfo(bankCode, bankName, branchCode, accountNumber, ribKey);
    }

    /**
     * Génère un RIB valide pour les tests
     */
    public String generateValidRIB(String bankCode, String branchCode, String accountNumber) {
        if (!isValidBankCode(bankCode)) {
            throw new IllegalArgumentException("Code banque invalide: " + bankCode);
        }
        
        if (branchCode.length() != 5 || accountNumber.length() != 11) {
            throw new IllegalArgumentException("Format de guichet ou compte invalide");
        }

        String baseRib = bankCode + branchCode + accountNumber;
        int ribKey = calculateRibKey(baseRib);
        
        return baseRib + String.format("%02d", ribKey);
    }

    /**
     * Retourne le nom de la banque à partir du code
     */
    public String getBankName(String bankCode) {
        return BANK_CODES.getOrDefault(bankCode, "Banque inconnue");
    }

    /**
     * Classe pour les informations extraites d'un RIB
     */
    public static class BankAccountInfo {
        private final String bankCode;
        private final String bankName;
        private final String branchCode;
        private final String accountNumber;
        private final String ribKey;

        public BankAccountInfo(String bankCode, String bankName, String branchCode, 
                              String accountNumber, String ribKey) {
            this.bankCode = bankCode;
            this.bankName = bankName;
            this.branchCode = branchCode;
            this.accountNumber = accountNumber;
            this.ribKey = ribKey;
        }

        public String getBankCode() { return bankCode; }
        public String getBankName() { return bankName; }
        public String getBranchCode() { return branchCode; }
        public String getAccountNumber() { return accountNumber; }
        public String getRibKey() { return ribKey; }
        
        public String getFullRib() {
            return bankCode + branchCode + accountNumber + ribKey;
        }

        @Override
        public String toString() {
            return String.format("BankAccountInfo{bank='%s (%s)', branch='%s', account='%s'}", 
                               bankName, bankCode, branchCode, accountNumber);
        }
    }
}