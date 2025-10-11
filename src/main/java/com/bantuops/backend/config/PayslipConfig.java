package com.bantuops.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration pour les templates de bulletins de paie
 * Conforme aux exigences 1.3, 2.3, 2.4
 */
@Configuration
@ConfigurationProperties(prefix = "bantuops.payslip")
@Data
public class PayslipConfig {

    /**
     * Configuration de l'entreprise
     */
    private Company company = new Company();

    /**
     * Templates disponibles
     */
    private Map<String, Template> templates = new HashMap<>();

    /**
     * Configuration PDF
     */
    private Pdf pdf = new Pdf();

    /**
     * Configuration de sécurité
     */
    private Security security = new Security();

    @Data
    public static class Company {
        private String name = "BantuOps";
        private String address = "Dakar, Sénégal";
        private String ninea = "";
        private String rccm = "";
        private String phone = "";
        private String email = "";
        private String website = "";
    }

    @Data
    public static class Template {
        private String name;
        private String description;
        private String cssFile;
        private boolean isDefault = false;
        private Map<String, String> customFields = new HashMap<>();
    }

    @Data
    public static class Pdf {
        private String pageSize = "A4";
        private String orientation = "PORTRAIT";
        private int marginTop = 15;
        private int marginBottom = 15;
        private int marginLeft = 15;
        private int marginRight = 15;
        private boolean enableCompression = true;
        private String defaultFont = "Times-Roman";
    }

    @Data
    public static class Security {
        private boolean enableEncryption = true;
        private boolean enableDigitalSignature = true;
        private String encryptionAlgorithm = "AES-256";
        private int passwordLength = 12;
        private boolean includeEmployeeIdInPassword = true;
        private boolean includePeriodInPassword = true;
    }

    /**
     * Initialise les templates par défaut
     */
    public void initializeDefaultTemplates() {
        if (templates.isEmpty()) {
            // Template officiel sénégalais
            Template officialTemplate = new Template();
            officialTemplate.setName("senegal_official");
            officialTemplate.setDescription("Template officiel conforme à la législation sénégalaise");
            officialTemplate.setDefault(true);
            templates.put("senegal_official", officialTemplate);

            // Template simplifié
            Template simplifiedTemplate = new Template();
            simplifiedTemplate.setName("senegal_simplified");
            simplifiedTemplate.setDescription("Template simplifié pour bulletins de base");
            templates.put("senegal_simplified", simplifiedTemplate);

            // Template détaillé
            Template detailedTemplate = new Template();
            detailedTemplate.setName("senegal_detailed");
            detailedTemplate.setDescription("Template détaillé avec calculs complets");
            templates.put("senegal_detailed", detailedTemplate);
        }
    }

    /**
     * Récupère le template par défaut
     */
    public Template getDefaultTemplate() {
        return templates.values().stream()
            .filter(Template::isDefault)
            .findFirst()
            .orElse(templates.get("senegal_official"));
    }

    /**
     * Vérifie si un template existe
     */
    public boolean templateExists(String templateName) {
        return templates.containsKey(templateName);
    }
}