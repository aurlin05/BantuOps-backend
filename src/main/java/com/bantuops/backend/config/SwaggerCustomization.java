package com.bantuops.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Configuration personnalisée pour Swagger UI et OpenAPI
 * Améliore l'expérience utilisateur de la documentation API
 */
@Configuration
public class SwaggerCustomization {

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${spring.profiles.active:development}")
    private String activeProfile;

    /**
     * Personnalise l'organisation et l'affichage de la documentation OpenAPI
     */
    @Bean
    public OpenApiCustomizer openApiCustomizer() {
        return openApi -> {
            // Tri des tags par ordre alphabétique pour une meilleure organisation
            if (openApi.getTags() != null) {
                openApi.getTags().sort(Comparator.comparing(tag -> tag.getName()));
            }

            // Ajout de serveurs dynamiques basés sur l'environnement
            openApi.getServers().clear();
            
            if ("production".equals(activeProfile)) {
                openApi.addServersItem(new Server()
                    .url("https://api.bantuops.com")
                    .description("🚀 Serveur de Production")
                );
                openApi.addServersItem(new Server()
                    .url("https://staging-api.bantuops.com")
                    .description("🧪 Serveur de Test")
                );
            } else if ("staging".equals(activeProfile)) {
                openApi.addServersItem(new Server()
                    .url("https://staging-api.bantuops.com")
                    .description("🧪 Serveur de Test")
                );
                openApi.addServersItem(new Server()
                    .url("http://localhost:" + serverPort)
                    .description("💻 Serveur de Développement Local")
                );
            } else {
                openApi.addServersItem(new Server()
                    .url("http://localhost:" + serverPort)
                    .description("💻 Serveur de Développement Local")
                );
                openApi.addServersItem(new Server()
                    .url("https://staging-api.bantuops.com")
                    .description("🧪 Serveur de Test")
                );
            }

            // Amélioration des informations de l'API
            Info info = openApi.getInfo();
            if (info != null) {
                info.setDescription(enhanceApiDescription(info.getDescription()));
                
                // Ajout d'informations de contact enrichies
                Contact contact = new Contact()
                    .name("Équipe Technique BantuOps")
                    .email("support@bantuops.com")
                    .url("https://bantuops.com/support");
                info.setContact(contact);

                // Licence mise à jour
                License license = new License()
                    .name("Licence Propriétaire BantuOps")
                    .url("https://bantuops.com/license")
                    .identifier("Proprietary");
                info.setLicense(license);

                // Version avec informations d'environnement
                info.setVersion(info.getVersion() + " (" + activeProfile + ")");
            }
        };
    }

    /**
     * Améliore la description de l'API avec du contenu riche
     */
    private String enhanceApiDescription(String originalDescription) {
        return """
            # 🏢 API BantuOps - Gestion des PME Sénégalaises
            
            > **Version actuelle :** %s | **Environnement :** %s
            
            ## 🌟 Vue d'ensemble
            
            Cette API REST sécurisée permet la gestion complète des PME au Sénégal avec une conformité totale à la législation locale.
            
            ## 🚀 Fonctionnalités Principales
            
            ### 💰 **Gestion de Paie**
            - ✅ Calculs conformes à la législation sénégalaise
            - ✅ Gestion automatique des taxes (IRPP, IPRES, CSS)
            - ✅ Calcul des heures supplémentaires selon le Code du Travail
            - ✅ Génération de bulletins de paie sécurisés avec signature numérique
            
            ### 📊 **Gestion Financière**
            - ✅ Facturation avec TVA sénégalaise (18%%)
            - ✅ Gestion des transactions avec chiffrement AES-256
            - ✅ Rapports financiers conformes aux normes SYSCOHADA
            - ✅ Export sécurisé des données avec audit complet
            
            ### 👥 **Gestion RH**
            - ✅ Suivi de l'assiduité avec règles configurables
            - ✅ Gestion des absences et justifications
            - ✅ Calcul automatique des ajustements de paie
            - ✅ Rapports RH détaillés avec métriques
            
            ### 🔐 **Sécurité & Conformité**
            - ✅ Authentification JWT avec refresh tokens
            - ✅ Chiffrement des données sensibles (AES-256)
            - ✅ Audit complet de toutes les actions
            - ✅ Permissions granulaires par rôle (ADMIN, HR, USER)
            - ✅ Validation des données selon les normes sénégalaises
            
            ## 🌍 Conformité Sénégalaise
            
            | Domaine | Norme | Status |
            |---------|-------|--------|
            | **Droit du Travail** | Code du Travail sénégalais | ✅ Conforme |
            | **Fiscalité** | Réglementation DGI | ✅ Conforme |
            | **Comptabilité** | Normes SYSCOHADA | ✅ Conforme |
            | **TVA** | Taux officiel 18%% | ✅ Conforme |
            | **Cotisations** | IPRES (6%%) + CSS (3.5%%) | ✅ Conforme |
            
            ## 🔧 Guide de Démarrage Rapide
            
            ### 1. **Authentification**
            ```bash
            curl -X POST "http://localhost:%s/api/auth/login" \\
              -H "Content-Type: application/json" \\
              -d '{"username": "admin@bantuops.com", "password": "password"}'
            ```
            
            ### 2. **Utilisation du Token**
            ```bash
            curl -X GET "http://localhost:%s/api/employees" \\
              -H "Authorization: Bearer YOUR_JWT_TOKEN"
            ```
            
            ### 3. **Calcul de Paie**
            ```bash
            curl -X POST "http://localhost:%s/api/payroll/calculate" \\
              -H "Authorization: Bearer YOUR_JWT_TOKEN" \\
              -H "Content-Type: application/json" \\
              -d '{"employeeId": 1, "payrollPeriod": "2024-01", "baseSalary": 500000}'
            ```
            
            ## 📋 Codes de Réponse HTTP
            
            | Code | Signification | Description |
            |------|---------------|-------------|
            | `200` | ✅ **Succès** | Requête traitée avec succès |
            | `201` | ✅ **Créé** | Ressource créée avec succès |
            | `400` | ❌ **Requête invalide** | Données d'entrée incorrectes |
            | `401` | 🔒 **Non authentifié** | Token manquant ou invalide |
            | `403` | 🚫 **Accès refusé** | Permissions insuffisantes |
            | `404` | 🔍 **Non trouvé** | Ressource inexistante |
            | `409` | ⚠️ **Conflit** | Ressource déjà existante |
            | `422` | 📝 **Règle métier** | Violation de règle métier |
            | `429` | 🚦 **Trop de requêtes** | Limite de taux dépassée |
            | `500` | 💥 **Erreur serveur** | Erreur interne du serveur |
            
            ## 🛠️ Outils et SDKs
            
            - **Swagger UI** : Interface interactive pour tester l'API
            - **Postman Collection** : Collection prête à l'emploi
            - **SDK JavaScript** : `npm install @bantuops/api-client`
            - **SDK Python** : `pip install bantuops-api`
            - **SDK PHP** : `composer require bantuops/api-client`
            
            ## 📞 Support
            
            - **Email** : support@bantuops.com
            - **Documentation** : https://docs.bantuops.com
            - **Status** : https://status.bantuops.com
            - **GitHub** : https://github.com/bantuops/api
            
            ---
            
            > 💡 **Astuce** : Utilisez l'interface Swagger UI ci-dessous pour explorer et tester tous les endpoints de manière interactive !
            """.formatted("1.0.0", activeProfile, serverPort, serverPort, serverPort);
    }
}