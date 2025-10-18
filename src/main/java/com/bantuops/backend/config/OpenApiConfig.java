package com.bantuops.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;

/**
 * Configuration OpenAPI pour la documentation des APIs BantuOps
 * Conforme aux exigences 4.1, 4.2, 4.3, 4.4, 4.5, 4.6 pour la documentation
 * complète
 */
@Configuration
@OpenAPIDefinition(info = @Info(title = "BantuOps Backend API", version = "1.0.0", description = """
        API REST sécurisée pour la gestion des PME sénégalaises.

        Cette API fournit des endpoints pour :
        - Gestion de la paie selon la législation sénégalaise
        - Gestion financière et facturation avec TVA
        - Gestion des ressources humaines et assiduité
        - Gestion des employés avec validation locale

        Toutes les APIs nécessitent une authentification JWT et respectent les permissions par rôle.
        """, contact = @Contact(name = "Équipe BantuOps", email = "support@bantuops.com", url = "https://bantuops.com"), license = @License(name = "Propriétaire", url = "https://bantuops.com/license")), servers = {
        @Server(url = "https://api.bantuops.com", description = "Serveur de production"),
        @Server(url = "https://staging-api.bantuops.com", description = "Serveur de test"),
        @Server(url = "http://localhost:8080", description = "Serveur de développement local")
})
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, bearerFormat = "JWT", scheme = "bearer", description = """
        Authentification JWT Bearer Token.

        Pour obtenir un token :
        1. Connectez-vous via POST /api/auth/login
        2. Utilisez le token retourné dans l'en-tête Authorization: Bearer <token>

        Le token expire après 24 heures et doit être renouvelé.
        """)
@SuppressWarnings("unchecked")
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new io.swagger.v3.oas.models.info.Info()
                        .title("BantuOps Backend API")
                        .version("1.0.0")
                        .description("""
                                # API BantuOps - Gestion des PME Sénégalaises

                                Cette API REST sécurisée permet la gestion complète des PME au Sénégal avec :

                                ## 🏢 Fonctionnalités Principales

                                ### 💰 Gestion de Paie
                                - Calculs conformes à la législation sénégalaise
                                - Gestion des taxes (IRPP, IPRES, CSS)
                                - Calcul des heures supplémentaires
                                - Génération de bulletins de paie sécurisés

                                ### 📊 Gestion Financière
                                - Facturation avec TVA sénégalaise (18%)
                                - Gestion des transactions chiffrées
                                - Rapports financiers conformes
                                - Export sécurisé des données

                                ### 👥 Gestion RH
                                - Suivi de l'assiduité
                                - Gestion des absences et retards
                                - Calcul des ajustements de paie
                                - Rapports RH détaillés

                                ### 🔐 Sécurité
                                - Authentification JWT
                                - Chiffrement des données sensibles
                                - Audit complet des actions
                                - Permissions granulaires par rôle

                                ## 🌍 Conformité Sénégalaise

                                L'API respecte la législation sénégalaise :
                                - Code du Travail sénégalais
                                - Réglementation fiscale DGI
                                - Normes comptables SYSCOHADA
                                - Validation des numéros fiscaux et bancaires

                                ## 🚀 Utilisation

                                1. **Authentification** : Obtenez un token JWT via `/api/auth/login`
                                2. **Autorisation** : Incluez le token dans l'en-tête `Authorization: Bearer <token>`
                                3. **Appels API** : Utilisez les endpoints selon vos permissions

                                ## 📋 Codes d'Erreur

                                | Code | Description |
                                |------|-------------|
                                | 200 | Succès |
                                | 201 | Ressource créée |
                                | 400 | Données invalides |
                                | 401 | Non authentifié |
                                | 403 | Accès refusé |
                                | 404 | Ressource non trouvée |
                                | 409 | Conflit de ressource |
                                | 422 | Règle métier violée |
                                | 500 | Erreur serveur |

                                ## 🔄 Versions

                                - **v1.0.0** : Version initiale avec toutes les fonctionnalités de base
                                """)
                        .contact(new io.swagger.v3.oas.models.info.Contact()
                                .name("Équipe BantuOps")
                                .email("support@bantuops.com")
                                .url("https://bantuops.com"))
                        .license(new io.swagger.v3.oas.models.info.License()
                                .name("Propriétaire")
                                .url("https://bantuops.com/license")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .addTagsItem(new Tag()
                        .name("Authentification")
                        .description("Endpoints pour l'authentification et la gestion des sessions"))
                .addTagsItem(new Tag()
                        .name("Gestion de Paie")
                        .description("Calculs de paie conformes à la législation sénégalaise"))
                .addTagsItem(new Tag()
                        .name("Gestion Financière")
                        .description("Facturation, TVA et gestion des transactions"))
                .addTagsItem(new Tag()
                        .name("Gestion RH")
                        .description("Assiduité, absences et ajustements de paie"))
                .addTagsItem(new Tag()
                        .name("Gestion des Employés")
                        .description("CRUD et validation des données employés"))
                .addTagsItem(new Tag()
                        .name("Rapports et Analytics")
                        .description("Génération de rapports et métriques"))
                .components(createApiComponents());
    }

    /**
     * Crée les composants complets de l'API avec schémas, réponses et exemples
     */
    private Components createApiComponents() {
        return new Components()
                // Schémas de sécurité
                .addSecuritySchemes("bearerAuth", new io.swagger.v3.oas.models.security.SecurityScheme()
                        .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Token JWT pour l'authentification"))

                // Réponses d'erreur standardisées
                .addResponses("BadRequest", createErrorResponse(
                        "Requête invalide - Données de validation échouées",
                        "VALIDATION_ERROR",
                        "Les données fournies ne respectent pas le format attendu ou les règles de validation"))
                .addResponses("Unauthorized", createErrorResponse(
                        "Non authentifié - Token manquant ou invalide",
                        "AUTHENTICATION_REQUIRED",
                        "Un token JWT valide est requis pour accéder à cette ressource"))
                .addResponses("Forbidden", createErrorResponse(
                        "Accès refusé - Permissions insuffisantes",
                        "ACCESS_DENIED",
                        "Vous n'avez pas les permissions nécessaires pour effectuer cette action"))
                .addResponses("NotFound", createErrorResponse(
                        "Ressource non trouvée",
                        "RESOURCE_NOT_FOUND",
                        "La ressource demandée n'existe pas ou a été supprimée"))
                .addResponses("Conflict", createErrorResponse(
                        "Conflit de ressource - Données en conflit",
                        "RESOURCE_CONFLICT",
                        "La ressource existe déjà ou est en conflit avec une autre ressource"))
                .addResponses("UnprocessableEntity", createErrorResponse(
                        "Règle métier violée",
                        "BUSINESS_RULE_VIOLATION",
                        "L'opération viole une règle métier de l'application"))
                .addResponses("InternalServerError", createErrorResponse(
                        "Erreur interne du serveur",
                        "INTERNAL_SERVER_ERROR",
                        "Une erreur inattendue s'est produite côté serveur"))

                // Schémas de données
                .addSchemas("PayrollRequest", createPayrollRequestSchema())
                .addSchemas("PayrollResult", createPayrollResultSchema())
                .addSchemas("InvoiceRequest", createInvoiceRequestSchema())
                .addSchemas("InvoiceResponse", createInvoiceResponseSchema())
                .addSchemas("EmployeeRequest", createEmployeeRequestSchema())
                .addSchemas("EmployeeResponse", createEmployeeResponseSchema())
                .addSchemas("AttendanceRequest", createAttendanceRequestSchema())
                .addSchemas("AttendanceResponse", createAttendanceResponseSchema())
                .addSchemas("FinancialReport", createFinancialReportSchema())
                .addSchemas("VATCalculation", createVATCalculationSchema())
                .addSchemas("TaxRates", createTaxRatesSchema())
                .addSchemas("ErrorResponse", createErrorResponseSchema())
                .addSchemas("ValidationResult", createValidationResultSchema())
                .addSchemas("AuthenticationRequest", createAuthenticationRequestSchema())
                .addSchemas("AuthenticationResponse", createAuthenticationResponseSchema())
                .addSchemas("TokenResponse", createTokenResponseSchema())
                .addSchemas("PayslipDocument", createPayslipDocumentSchema())
                .addSchemas("BulkOperationResult", createBulkOperationResultSchema())
                .addSchemas("AuditLog", createAuditLogSchema())

                // Exemples de requêtes et réponses
                .addExamples("PayrollRequestExample", createPayrollRequestExample())
                .addExamples("PayrollResponseExample", createPayrollResponseExample())
                .addExamples("InvoiceRequestExample", createInvoiceRequestExample())
                .addExamples("InvoiceResponseExample", createInvoiceResponseExample())
                .addExamples("EmployeeRequestExample", createEmployeeRequestExample())
                .addExamples("EmployeeResponseExample", createEmployeeResponseExample())
                .addExamples("AttendanceRequestExample", createAttendanceRequestExample())
                .addExamples("AttendanceResponseExample", createAttendanceResponseExample())
                .addExamples("FinancialReportExample", createFinancialReportExample())
                .addExamples("VATCalculationExample", createVATCalculationExample())
                .addExamples("TaxRatesExample", createTaxRatesExample())
                .addExamples("ErrorResponseExample", createErrorResponseExample())
                .addExamples("ValidationErrorExample", createValidationErrorExample())
                .addExamples("BusinessRuleErrorExample", createBusinessRuleErrorExample())
                .addExamples("SecurityErrorExample", createSecurityErrorExample())
                .addExamples("AuthenticationRequestExample", createAuthenticationRequestExample())
                .addExamples("AuthenticationResponseExample", createAuthenticationResponseExample())
                .addExamples("PayslipDocumentExample", createPayslipDocumentExample())
                .addExamples("BulkOperationResultExample", createBulkOperationResultExample())
                .addExamples("AuditLogExample", createAuditLogExample());
    }

    /**
     * Crée une réponse d'erreur standardisée pour la documentation
     */
    private ApiResponse createErrorResponse(String description, String errorCode, String message) {
        return new ApiResponse()
                .description(description)
                .content(new Content()
                        .addMediaType("application/json", new MediaType()
                                .schema(new Schema<>()
                                        .type("object")
                                        .addProperty("code", new Schema<>().type("string").example(errorCode))
                                        .addProperty("message", new Schema<>().type("string").example(message))
                                        .addProperty("timestamp",
                                                new Schema<>().type("string").format("date-time")
                                                        .example("2024-01-15T10:30:00Z"))
                                        .addProperty("path",
                                                new Schema<>().type("string").example("/api/payroll/calculate"))
                                        .addProperty("details", new Schema<>().type("object"))
                                        .addProperty("suggestion", new Schema<>().type("string")
                                                .example("Veuillez vérifier les données et réessayer")))));
    }

    // ==================== SCHÉMAS DE DONNÉES ====================

    /**
     * Schéma pour les requêtes de calcul de paie
     */
    private Schema<?> createPayrollRequestSchema() {
        return new Schema<>()
                .type("object")
                .description("Requête de calcul de paie avec validation complète")
                .addProperty("employeeId",
                        new Schema<>().type("integer").format("int64").description("ID unique de l'employé"))
                .addProperty("payrollPeriod",
                        new Schema<>().type("string").pattern("\\d{4}-\\d{2}")
                                .description("Période de paie au format YYYY-MM"))
                .addProperty("baseSalary",
                        new Schema<>().type("number").format("decimal").minimum(BigDecimal.valueOf(0))
                                .description("Salaire de base en FCFA"))
                .addProperty("regularHours",
                        new Schema<>().type("number").format("decimal").description("Heures normales travaillées"))
                .addProperty("overtimeHours",
                        new Schema<>().type("number").format("decimal").description("Heures supplémentaires"))
                .addProperty("performanceBonus",
                        new Schema<>().type("number").format("decimal").description("Prime de performance"))
                .addProperty("transportAllowance",
                        new Schema<>().type("number").format("decimal").description("Indemnité de transport"))
                .addProperty("mealAllowance",
                        new Schema<>().type("number").format("decimal").description("Indemnité de repas"))
                .addProperty("notes", new Schema<>().type("string").maxLength(500).description("Notes additionnelles"))
                .required(Arrays.asList("employeeId", "payrollPeriod", "baseSalary"));
    }

    /**
     * Schéma pour les résultats de calcul de paie
     */
    private Schema<?> createPayrollResultSchema() {
        return new Schema<>()
                .type("object")
                .description("Résultat détaillé du calcul de paie")
                .addProperty("employeeId", new Schema<>().type("integer").format("int64"))
                .addProperty("period", new Schema<>().type("string").pattern("\\d{4}-\\d{2}"))
                .addProperty("baseSalary", new Schema<>().type("number").format("decimal"))
                .addProperty("grossSalary",
                        new Schema<>().type("number").format("decimal").description("Salaire brut total"))
                .addProperty("netSalary",
                        new Schema<>().type("number").format("decimal").description("Salaire net après déductions"))
                .addProperty("incomeTax",
                        new Schema<>().type("number").format("decimal").description("Impôt sur le revenu (IRPP)"))
                .addProperty("ipresContribution",
                        new Schema<>().type("number").format("decimal").description("Cotisation IPRES"))
                .addProperty("cssContribution",
                        new Schema<>().type("number").format("decimal").description("Cotisation CSS"))
                .addProperty("totalAllowances",
                        new Schema<>().type("number").format("decimal").description("Total des indemnités"))
                .addProperty("totalDeductions",
                        new Schema<>().type("number").format("decimal").description("Total des déductions"))
                .addProperty("calculatedBy",
                        new Schema<>().type("string").description("Utilisateur ayant effectué le calcul"))
                .addProperty("calculatedAt", new Schema<>().type("string").format("date-time"))
                .addProperty("notes", new Schema<>().type("string"));
    }

    /**
     * Schéma pour les requêtes de facture
     */
    private Schema<?> createInvoiceRequestSchema() {
        return new Schema<>()
                .type("object")
                .description("Requête de création de facture avec validation fiscale")
                .addProperty("invoiceNumber",
                        new Schema<>().type("string").pattern("^[A-Z]{3,4}-\\d{4}-\\d{3}$")
                                .description("Numéro de facture unique"))
                .addProperty("invoiceDate", new Schema<>().type("string").format("date").description("Date d'émission"))
                .addProperty("dueDate", new Schema<>().type("string").format("date").description("Date d'échéance"))
                .addProperty("clientName", new Schema<>().type("string").maxLength(200).description("Nom du client"))
                .addProperty("clientAddress",
                        new Schema<>().type("string").maxLength(500).description("Adresse du client"))
                .addProperty("clientTaxNumber",
                        new Schema<>().type("string").pattern("\\d{13}").description("Numéro fiscal sénégalais"))
                .addProperty("subtotalAmount",
                        new Schema<>().type("number").format("decimal").minimum(BigDecimal.valueOf(0))
                                .description("Montant HT"))
                .addProperty("vatRate",
                        new Schema<>().type("number").format("decimal").minimum(BigDecimal.valueOf(0))
                                .maximum(BigDecimal.valueOf(1)).description("Taux de TVA (0.18 pour 18%)"))
                .addProperty("currency", new Schema<>().type("string").pattern("XOF|EUR|USD").description("Devise"))
                .addProperty("description",
                        new Schema<>().type("string").maxLength(1000).description("Description des services/produits"))
                .required(Arrays.asList("invoiceNumber", "invoiceDate", "clientName", "subtotalAmount", "vatRate"));
    }

    /**
     * Schéma pour les réponses de facture
     */
    private Schema<?> createInvoiceResponseSchema() {
        return new Schema<>()
                .type("object")
                .description("Facture créée avec calculs automatiques")
                .addProperty("id", new Schema<>().type("integer").format("int64"))
                .addProperty("invoiceNumber", new Schema<>().type("string"))
                .addProperty("subtotalAmount", new Schema<>().type("number").format("decimal"))
                .addProperty("vatAmount",
                        new Schema<>().type("number").format("decimal").description("Montant de la TVA"))
                .addProperty("totalAmount", new Schema<>().type("number").format("decimal").description("Montant TTC"))
                .addProperty("status",
                        new Schema<>().type("string")
                                ._enum(Arrays.asList("DRAFT", "PENDING", "PAID", "OVERDUE", "CANCELLED")))
                .addProperty("createdAt", new Schema<>().type("string").format("date-time"))
                .addProperty("updatedAt", new Schema<>().type("string").format("date-time"));
    }

    /**
     * Schéma pour les requêtes d'employé
     */
    private Schema<?> createEmployeeRequestSchema() {
        return new Schema<>()
                .type("object")
                .description("Requête de création/modification d'employé avec validation sénégalaise")
                .addProperty("employeeNumber",
                        new Schema<>().type("string").pattern("^EMP-\\d{3,6}$").description("Numéro unique d'employé"))
                .addProperty("firstName",
                        new Schema<>().type("string").minLength(2).maxLength(50).description("Prénom"))
                .addProperty("lastName",
                        new Schema<>().type("string").minLength(2).maxLength(50).description("Nom de famille"))
                .addProperty("email",
                        new Schema<>().type("string").format("email").description("Adresse email professionnelle"))
                .addProperty("phoneNumber",
                        new Schema<>().type("string").pattern("^\\+221[0-9]{9}$")
                                .description("Numéro de téléphone sénégalais"))
                .addProperty("nationalId",
                        new Schema<>().type("string").pattern("\\d{13}").description("Numéro de carte d'identité"))
                .addProperty("dateOfBirth",
                        new Schema<>().type("string").format("date").description("Date de naissance"))
                .addProperty("position", new Schema<>().type("string").maxLength(100).description("Poste occupé"))
                .addProperty("department", new Schema<>().type("string").maxLength(100).description("Département"))
                .addProperty("hireDate", new Schema<>().type("string").format("date").description("Date d'embauche"))
                .addProperty("contractType",
                        new Schema<>().type("string")._enum(Arrays.asList("CDI", "CDD", "STAGE", "CONSULTANT"))
                                .description("Type de contrat"))
                .addProperty("baseSalary",
                        new Schema<>().type("number").format("decimal").minimum(BigDecimal.valueOf(0))
                                .description("Salaire de base"))
                .addProperty("workStartTime",
                        new Schema<>().type("string").format("time").description("Heure de début de travail"))
                .addProperty("workEndTime",
                        new Schema<>().type("string").format("time").description("Heure de fin de travail"))
                .addProperty("workDays",
                        new Schema<>().type("string").description("Jours de travail séparés par des virgules"))
                .required(Arrays.asList("employeeNumber", "firstName", "lastName", "email", "position", "department",
                        "hireDate", "contractType", "baseSalary"));
    }

    /**
     * Schéma pour les réponses d'employé
     */
    private Schema<?> createEmployeeResponseSchema() {
        return new Schema<>()
                .type("object")
                .description("Employé avec informations chiffrées pour la sécurité")
                .addProperty("id", new Schema<>().type("integer").format("int64"))
                .addProperty("employeeNumber", new Schema<>().type("string"))
                .addProperty("personalInfo", new Schema<>().type("object")
                        .addProperty("firstName", new Schema<>().type("string").example("[ENCRYPTED]"))
                        .addProperty("lastName", new Schema<>().type("string").example("[ENCRYPTED]"))
                        .addProperty("email", new Schema<>().type("string").example("[ENCRYPTED]"))
                        .addProperty("phoneNumber", new Schema<>().type("string").example("[ENCRYPTED]"))
                        .description("Informations personnelles chiffrées"))
                .addProperty("employmentInfo", new Schema<>().type("object")
                        .addProperty("position", new Schema<>().type("string"))
                        .addProperty("department", new Schema<>().type("string"))
                        .addProperty("hireDate", new Schema<>().type("string").format("date"))
                        .addProperty("contractType", new Schema<>().type("string"))
                        .addProperty("isActive", new Schema<>().type("boolean"))
                        .description("Informations d'emploi"))
                .addProperty("createdAt", new Schema<>().type("string").format("date-time"))
                .addProperty("updatedAt", new Schema<>().type("string").format("date-time"));
    }

    /**
     * Schéma pour les requêtes d'assiduité
     */
    private Schema<?> createAttendanceRequestSchema() {
        return new Schema<>()
                .type("object")
                .description("Requête d'enregistrement d'assiduité")
                .addProperty("employeeId", new Schema<>().type("integer").format("int64"))
                .addProperty("workDate", new Schema<>().type("string").format("date"))
                .addProperty("scheduledStartTime", new Schema<>().type("string").format("time"))
                .addProperty("actualStartTime", new Schema<>().type("string").format("time"))
                .addProperty("scheduledEndTime", new Schema<>().type("string").format("time"))
                .addProperty("actualEndTime", new Schema<>().type("string").format("time"))
                .addProperty("type",
                        new Schema<>().type("string")._enum(Arrays.asList("PRESENT", "LATE", "ABSENT", "HALF_DAY")))
                .addProperty("justification", new Schema<>().type("string").maxLength(500))
                .required(Arrays.asList("employeeId", "workDate", "scheduledStartTime", "scheduledEndTime", "type"));
    }

    /**
     * Schéma pour les réponses d'assiduité
     */
    private Schema<?> createAttendanceResponseSchema() {
        return new Schema<>()
                .type("object")
                .description("Enregistrement d'assiduité avec calculs automatiques")
                .addProperty("id", new Schema<>().type("integer").format("int64"))
                .addProperty("employeeId", new Schema<>().type("integer").format("int64"))
                .addProperty("workDate", new Schema<>().type("string").format("date"))
                .addProperty("type", new Schema<>().type("string"))
                .addProperty("delayMinutes", new Schema<>().type("integer").description("Retard en minutes"))
                .addProperty("status",
                        new Schema<>().type("string")._enum(Arrays.asList("PENDING", "APPROVED", "REJECTED")))
                .addProperty("createdAt", new Schema<>().type("string").format("date-time"));
    }

    /**
     * Schéma pour les rapports financiers
     */
    private Schema<?> createFinancialReportSchema() {
        return new Schema<>()
                .type("object")
                .description("Rapport financier détaillé")
                .addProperty("reportId", new Schema<>().type("string"))
                .addProperty("period", new Schema<>().type("object")
                        .addProperty("startDate", new Schema<>().type("string").format("date"))
                        .addProperty("endDate", new Schema<>().type("string").format("date")))
                .addProperty("totalRevenue", new Schema<>().type("number").format("decimal"))
                .addProperty("totalExpenses", new Schema<>().type("number").format("decimal"))
                .addProperty("netProfit", new Schema<>().type("number").format("decimal"))
                .addProperty("vatCollected", new Schema<>().type("number").format("decimal"))
                .addProperty("taxesPaid", new Schema<>().type("number").format("decimal"))
                .addProperty("generatedAt", new Schema<>().type("string").format("date-time"));
    }

    /**
     * Schéma pour les calculs de TVA
     */
    private Schema<?> createVATCalculationSchema() {
        return new Schema<>()
                .type("object")
                .description("Résultat de calcul de TVA")
                .addProperty("baseAmount",
                        new Schema<>().type("number").format("decimal").description("Montant de base HT"))
                .addProperty("vatRate",
                        new Schema<>().type("number").format("decimal").description("Taux de TVA appliqué"))
                .addProperty("vatAmount",
                        new Schema<>().type("number").format("decimal").description("Montant de la TVA"))
                .addProperty("totalAmount", new Schema<>().type("number").format("decimal").description("Montant TTC"))
                .addProperty("exemptionApplied", new Schema<>().type("boolean").description("Exonération appliquée"))
                .addProperty("calculatedAt", new Schema<>().type("string").format("date-time"));
    }

    /**
     * Schéma pour les taux de taxes
     */
    private Schema<?> createTaxRatesSchema() {
        return new Schema<>()
                .type("object")
                .description("Taux de taxes et cotisations sénégalais")
                .addProperty("incomeTaxBrackets", new Schema<>().type("array").items(new Schema<>().type("object")
                        .addProperty("minIncome", new Schema<>().type("number").format("decimal"))
                        .addProperty("maxIncome", new Schema<>().type("number").format("decimal"))
                        .addProperty("rate", new Schema<>().type("number").format("decimal"))
                        .addProperty("fixedAmount", new Schema<>().type("number").format("decimal"))))
                .addProperty("ipresRate",
                        new Schema<>().type("number").format("decimal").example(0.06).description("Taux IPRES (6%)"))
                .addProperty("cssRate",
                        new Schema<>().type("number").format("decimal").example(0.035).description("Taux CSS (3.5%)"))
                .addProperty("vatRate",
                        new Schema<>().type("number").format("decimal").example(0.18).description("Taux TVA (18%)"))
                .addProperty("effectiveDate", new Schema<>().type("string").format("date"));
    }

    /**
     * Schéma pour les réponses d'erreur
     */
    private Schema<?> createErrorResponseSchema() {
        return new Schema<>()
                .type("object")
                .description("Réponse d'erreur standardisée")
                .addProperty("code", new Schema<>().type("string").description("Code d'erreur unique"))
                .addProperty("message", new Schema<>().type("string").description("Message d'erreur lisible"))
                .addProperty("details", new Schema<>().type("object").description("Détails additionnels de l'erreur"))
                .addProperty("timestamp", new Schema<>().type("string").format("date-time"))
                .addProperty("path", new Schema<>().type("string").description("Chemin de l'API qui a généré l'erreur"))
                .addProperty("suggestion",
                        new Schema<>().type("string").description("Suggestion pour résoudre l'erreur"))
                .addProperty("traceId", new Schema<>().type("string").description("ID de trace pour le débogage"))
                .addProperty("correlationId",
                        new Schema<>().type("string").description("ID de corrélation pour le suivi"))
                .required(Arrays.asList("code", "message", "timestamp"));
    }

    /**
     * Schéma pour les résultats de validation
     */
    private Schema<?> createValidationResultSchema() {
        return new Schema<>()
                .type("object")
                .description("Résultat de validation des données")
                .addProperty("valid", new Schema<>().type("boolean").description("Indique si la validation a réussi"))
                .addProperty("errors", new Schema<>().type("object").description("Erreurs de validation par champ"))
                .addProperty("warnings",
                        new Schema<>().type("array").items(new Schema<>().type("string"))
                                .description("Avertissements de validation"))
                .addProperty("validatedAt", new Schema<>().type("string").format("date-time"));
    }

    /**
     * Schéma pour les requêtes d'authentification
     */
    private Schema<?> createAuthenticationRequestSchema() {
        return new Schema<>()
                .type("object")
                .description("Requête d'authentification utilisateur")
                .addProperty("username",
                        new Schema<>().type("string").format("email").description("Nom d'utilisateur (email)"))
                .addProperty("password", new Schema<>().type("string").minLength(8).description("Mot de passe"))
                .addProperty("rememberMe", new Schema<>().type("boolean").description("Se souvenir de moi"))
                .required(Arrays.asList("username", "password"));
    }

    /**
     * Schéma pour les réponses d'authentification
     */
    private Schema<?> createAuthenticationResponseSchema() {
        return new Schema<>()
                .type("object")
                .description("Réponse d'authentification avec token JWT")
                .addProperty("token", new Schema<>().type("string").description("Token JWT d'accès"))
                .addProperty("refreshToken", new Schema<>().type("string").description("Token de renouvellement"))
                .addProperty("expiresIn", new Schema<>().type("integer").description("Durée de validité en secondes"))
                .addProperty("tokenType", new Schema<>().type("string").example("Bearer").description("Type de token"))
                .addProperty("user", new Schema<>().type("object")
                        .addProperty("id", new Schema<>().type("integer").format("int64"))
                        .addProperty("username", new Schema<>().type("string"))
                        .addProperty("roles", new Schema<>().type("array").items(new Schema<>().type("string")))
                        .addProperty("permissions", new Schema<>().type("array").items(new Schema<>().type("string")))
                        .description("Informations utilisateur"));
    }

    /**
     * Schéma pour les réponses de token
     */
    private Schema<?> createTokenResponseSchema() {
        return new Schema<>()
                .type("object")
                .description("Réponse de renouvellement de token")
                .addProperty("token", new Schema<>().type("string").description("Nouveau token JWT"))
                .addProperty("refreshToken", new Schema<>().type("string").description("Nouveau refresh token"))
                .addProperty("expiresIn", new Schema<>().type("integer").description("Durée de validité en secondes"))
                .addProperty("tokenType", new Schema<>().type("string").example("Bearer"));
    }

    /**
     * Schéma pour les documents de bulletin de paie
     */
    private Schema<?> createPayslipDocumentSchema() {
        return new Schema<>()
                .type("object")
                .description("Document de bulletin de paie généré")
                .addProperty("documentId", new Schema<>().type("string").description("ID unique du document"))
                .addProperty("employeeId", new Schema<>().type("integer").format("int64"))
                .addProperty("period", new Schema<>().type("string").pattern("\\d{4}-\\d{2}"))
                .addProperty("generatedAt", new Schema<>().type("string").format("date-time"))
                .addProperty("templateUsed", new Schema<>().type("string").description("Template utilisé"))
                .addProperty("digitalSignature", new Schema<>().type("string").description("Signature numérique"))
                .addProperty("pdfUrl",
                        new Schema<>().type("string").format("uri").description("URL de téléchargement PDF"))
                .addProperty("status",
                        new Schema<>().type("string")._enum(Arrays.asList("GENERATED", "SIGNED", "SENT", "ARCHIVED")))
                .addProperty("metadata", new Schema<>().type("object").description("Métadonnées additionnelles"));
    }

    /**
     * Schéma pour les résultats d'opérations en lot
     */
    private Schema<?> createBulkOperationResultSchema() {
        return new Schema<>()
                .type("object")
                .description("Résultat d'une opération en lot")
                .addProperty("totalProcessed",
                        new Schema<>().type("integer").description("Nombre total d'éléments traités"))
                .addProperty("successful",
                        new Schema<>().type("integer").description("Nombre d'éléments traités avec succès"))
                .addProperty("failed", new Schema<>().type("integer").description("Nombre d'éléments échoués"))
                .addProperty("errors", new Schema<>().type("array").items(new Schema<>().type("object")
                        .addProperty("itemId", new Schema<>().type("string"))
                        .addProperty("error", new Schema<>().type("string"))
                        .addProperty("details", new Schema<>().type("object")))
                        .description("Détails des erreurs"))
                .addProperty("processedAt", new Schema<>().type("string").format("date-time"))
                .addProperty("duration",
                        new Schema<>().type("integer").description("Durée de traitement en millisecondes"));
    }

    /**
     * Schéma pour les logs d'audit
     */
    private Schema<?> createAuditLogSchema() {
        return new Schema<>()
                .type("object")
                .description("Entrée de log d'audit")
                .addProperty("id", new Schema<>().type("integer").format("int64"))
                .addProperty("entityType", new Schema<>().type("string").description("Type d'entité modifiée"))
                .addProperty("entityId", new Schema<>().type("string").description("ID de l'entité"))
                .addProperty("action",
                        new Schema<>().type("string")._enum(Arrays.asList("CREATE", "UPDATE", "DELETE", "VIEW")))
                .addProperty("userId", new Schema<>().type("string").description("ID de l'utilisateur"))
                .addProperty("userRole", new Schema<>().type("string").description("Rôle de l'utilisateur"))
                .addProperty("timestamp", new Schema<>().type("string").format("date-time"))
                .addProperty("ipAddress", new Schema<>().type("string").description("Adresse IP"))
                .addProperty("userAgent", new Schema<>().type("string").description("User Agent"))
                .addProperty("changes", new Schema<>().type("object").description("Détails des modifications"))
                .addProperty("sessionId", new Schema<>().type("string").description("ID de session"));
    }

    // ==================== EXEMPLES DE DONNÉES ====================

    /**
     * Exemple de requête de calcul de paie
     */
    private Example createPayrollRequestExample() {
        return new Example()
                .summary("Calcul de paie mensuel")
                .description("Exemple de calcul de paie pour un employé sénégalais avec heures supplémentaires")
                .value(Map.of(
                        "employeeId", 1,
                        "payrollPeriod", "2024-01",
                        "baseSalary", 500000,
                        "regularHours", 173.33,
                        "overtimeHours", 10.5,
                        "performanceBonus", 50000,
                        "transportAllowance", 25000,
                        "mealAllowance", 15000,
                        "notes", "Calcul mensuel standard avec heures supplémentaires et primes"));
    }

    /**
     * Exemple de réponse de calcul de paie
     */
    private Example createPayrollResponseExample() {
        var value = new java.util.HashMap<String, Object>();
        value.put("employeeId", 1);
        value.put("period", "2024-01");
        value.put("baseSalary", 500000);
        value.put("grossSalary", 615000);
        value.put("netSalary", 487500);
        value.put("incomeTax", 75000);
        value.put("ipresContribution", 30750);
        value.put("cssContribution", 21525);
        value.put("totalAllowances", 90000);
        value.put("totalDeductions", 127275);
        value.put("calculatedBy", "admin@bantuops.com");
        value.put("calculatedAt", "2024-01-15T10:30:00Z");
        value.put("notes", "Calcul conforme à la législation sénégalaise - Code du Travail");

        return new Example()
                .summary("Résultat de calcul de paie")
                .description("Résultat détaillé du calcul de paie avec taxes sénégalaises appliquées")
                .value(value);
    }

    /**
     * Exemple de requête de facture
     */
    private Example createInvoiceRequestExample() {
        return new Example()
                .summary("Création de facture")
                .description("Exemple de création de facture avec TVA sénégalaise et validation fiscale")
                .value(Map.of(
                        "invoiceNumber", "FACT-2024-001",
                        "invoiceDate", "2024-01-15",
                        "dueDate", "2024-02-15",
                        "clientName", "Entreprise ABC SARL",
                        "clientAddress", "Avenue Cheikh Anta Diop, Dakar, Sénégal",
                        "clientTaxNumber", "1234567890123",
                        "subtotalAmount", 1000000,
                        "vatRate", 0.18,
                        "currency", "XOF",
                        "description", "Prestation de services informatiques - Développement application mobile"));
    }

    /**
     * Exemple de réponse de facture
     */
    private Example createInvoiceResponseExample() {
        return new Example()
                .summary("Facture créée")
                .description("Facture créée avec calculs automatiques de TVA et montants")
                .value(Map.of(
                        "id", 1,
                        "invoiceNumber", "FACT-2024-001",
                        "subtotalAmount", 1000000,
                        "vatAmount", 180000,
                        "totalAmount", 1180000,
                        "status", "PENDING",
                        "createdAt", "2024-01-15T10:30:00Z",
                        "updatedAt", "2024-01-15T10:30:00Z",
                        "dueDate", "2024-02-15",
                        "currency", "XOF"));
    }

    /**
     * Exemple de requête d'employé
     */
    private Example createEmployeeRequestExample() {
        return new Example()
                .summary("Création d'employé")
                .description("Exemple de création d'employé avec validation complète selon les normes sénégalaises")
                .value(Map.of());
    }

    /**
     * Exemple de réponse d'employé
     */
    private Example createEmployeeResponseExample() {
        return new Example()
                .summary("Employé créé")
                .description("Employé créé avec informations personnelles chiffrées pour la sécurité")
                .value(Map.of(
                        "id", 1,
                        "employeeNumber", "EMP-001",
                        "personalInfo", Map.of(
                                "firstName", "[ENCRYPTED]",
                                "lastName", "[ENCRYPTED]",
                                "email", "[ENCRYPTED]",
                                "phoneNumber", "[ENCRYPTED]",
                                "nationalId", "[ENCRYPTED]"),
                        "employmentInfo", Map.of(
                                "position", "Développeur Senior",
                                "department", "Informatique",
                                "hireDate", "2024-01-01",
                                "contractType", "CDI",
                                "isActive", true),
                        "createdAt", "2024-01-01T10:00:00Z",
                        "updatedAt", "2024-01-01T10:00:00Z"));
    }

    /**
     * Exemple de requête d'assiduité
     */
    private Example createAttendanceRequestExample() {
        return new Example()
                .summary("Enregistrement de présence")
                .description("Exemple d'enregistrement de présence avec calcul automatique de retard")
                .value(Map.of(
                        "employeeId", 1,
                        "workDate", "2024-01-15",
                        "scheduledStartTime", "08:00",
                        "actualStartTime", "08:15",
                        "scheduledEndTime", "17:00",
                        "actualEndTime", "17:00",
                        "type", "LATE",
                        "justification", "Embouteillage sur la VDN - Dakar"));
    }

    /**
     * Exemple de réponse d'assiduité
     */
    private Example createAttendanceResponseExample() {
        return new Example()
                .summary("Présence enregistrée")
                .description("Présence enregistrée avec calcul automatique du retard et statut")
                .value(Map.of(
                        "id", 1,
                        "employeeId", 1,
                        "workDate", "2024-01-15",
                        "type", "LATE",
                        "delayMinutes", 15,
                        "status", "PENDING",
                        "justification", "Embouteillage sur la VDN - Dakar",
                        "createdAt", "2024-01-15T08:15:00Z"));
    }

    /**
     * Exemple de rapport financier
     */
    private Example createFinancialReportExample() {
        return new Example()
                .summary("Rapport financier mensuel")
                .description("Rapport financier détaillé avec métriques clés")
                .value(Map.of(
                        "reportId", "FIN-RPT-2024-01",
                        "period", Map.of(
                                "startDate", "2024-01-01",
                                "endDate", "2024-01-31"),
                        "totalRevenue", 15000000,
                        "totalExpenses", 8500000,
                        "netProfit", 6500000,
                        "vatCollected", 2700000,
                        "taxesPaid", 1200000,
                        "generatedAt", "2024-02-01T09:00:00Z"));
    }

    /**
     * Exemple de calcul de TVA
     */
    private Example createVATCalculationExample() {
        return new Example()
                .summary("Calcul de TVA sénégalaise")
                .description("Calcul de TVA avec taux sénégalais de 18%")
                .value(Map.of(
                        "baseAmount", 1000000,
                        "vatRate", 0.18,
                        "vatAmount", 180000,
                        "totalAmount", 1180000,
                        "exemptionApplied", false,
                        "calculatedAt", "2024-01-15T10:30:00Z"));
    }

    /**
     * Exemple de taux de taxes
     */
    private Example createTaxRatesExample() {
        return new Example()
                .summary("Taux de taxes sénégalais")
                .description("Taux officiels des taxes et cotisations au Sénégal")
                .value(Map.of(
                        "incomeTaxBrackets", Arrays.asList(
                                Map.of("minIncome", 0, "maxIncome", 630000, "rate", 0.0, "fixedAmount", 0),
                                Map.of("minIncome", 630001, "maxIncome", 1500000, "rate", 0.20, "fixedAmount", 0),
                                Map.of("minIncome", 1500001, "maxIncome", 4000000, "rate", 0.30, "fixedAmount", 174000),
                                Map.of("minIncome", 4000001, "maxIncome", 8300000, "rate", 0.35, "fixedAmount", 924000),
                                Map.of("minIncome", 8300001, "maxIncome", null, "rate", 0.40, "fixedAmount", 2429000)),
                        "ipresRate", 0.06,
                        "cssRate", 0.035,
                        "vatRate", 0.18,
                        "effectiveDate", "2024-01-01"));
    }

    /**
     * Exemple de réponse d'erreur générale
     */
    private Example createErrorResponseExample() {
        return new Example()
                .summary("Erreur de validation")
                .description("Exemple de réponse d'erreur avec détails de validation")
                .value(Map.of(
                        "code", "VALIDATION_ERROR",
                        "message", "Données invalides",
                        "details", Map.of(
                                "baseSalary", "Le salaire de base doit être positif",
                                "email", "L'email doit être valide",
                                "phoneNumber", "Le numéro de téléphone doit être au format sénégalais (+221XXXXXXXXX)"),
                        "timestamp", "2024-01-15T10:30:00Z",
                        "path", "/api/employees",
                        "suggestion", "Veuillez corriger les champs invalides et réessayer"));
    }

    /**
     * Exemple d'erreur de validation spécifique
     */
    private Example createValidationErrorExample() {
        return new Example()
                .summary("Erreur de validation de champs")
                .description("Erreur détaillée de validation avec champs spécifiques")
                .value(Map.of(
                        "code", "FIELD_VALIDATION_ERROR",
                        "message", "Validation échouée pour plusieurs champs",
                        "details", Map.of(
                                "employeeNumber", "Le numéro d'employé doit suivre le format EMP-XXX",
                                "nationalId", "Le numéro de carte d'identité doit contenir 13 chiffres",
                                "baseSalary", "Le salaire ne peut pas être inférieur au SMIG sénégalais"),
                        "timestamp", "2024-01-15T10:30:00Z",
                        "path", "/api/employees",
                        "suggestion", "Consultez la documentation pour les formats requis"));
    }

    /**
     * Exemple d'erreur de règle métier
     */
    private Example createBusinessRuleErrorExample() {
        return new Example()
                .summary("Violation de règle métier")
                .description("Erreur liée à une violation de règle métier sénégalaise")
                .value(Map.of(
                        "code", "BUSINESS_RULE_VIOLATION",
                        "message", "Violation des règles métier sénégalaises",
                        "details", Map.of(
                                "rule", "SENEGAL_TAX_COMPLIANCE",
                                "violation", "Le numéro fiscal fourni n'est pas valide selon les normes DGI",
                                "field", "clientTaxNumber"),
                        "timestamp", "2024-01-15T10:30:00Z",
                        "path", "/api/financial/invoices",
                        "suggestion", "Vérifiez le numéro fiscal auprès de la Direction Générale des Impôts"));
    }

    /**
     * Exemple d'erreur de sécurité
     */
    private Example createSecurityErrorExample() {
        return new Example()
                .summary("Erreur de sécurité")
                .description("Erreur d'accès ou de permissions insuffisantes")
                .value(Map.of(
                        "code", "ACCESS_DENIED",
                        "message", "Accès refusé - Permissions insuffisantes",
                        "details", Map.of(
                                "requiredRole", "ADMIN",
                                "userRole", "HR",
                                "resource", "financial-export",
                                "action", "READ"),
                        "timestamp", "2024-01-15T10:30:00Z",
                        "path", "/api/financial/export",
                        "suggestion", "Contactez votre administrateur pour obtenir les permissions nécessaires"));
    }

    /**
     * Exemple de requête d'authentification
     */
    private Example createAuthenticationRequestExample() {
        return new Example()
                .summary("Connexion utilisateur")
                .description("Exemple de requête d'authentification avec identifiants")
                .value(Map.of(
                        "username", "admin@bantuops.com",
                        "password", "SecurePassword123!",
                        "rememberMe", false));
    }

    /**
     * Exemple de réponse d'authentification
     */
    private Example createAuthenticationResponseExample() {
        return new Example()
                .summary("Réponse d'authentification")
                .description("Token JWT généré avec informations utilisateur")
                .value(Map.of(
                        "token",
                        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZG1pbkBiYW50dW9wcy5jb20iLCJyb2xlcyI6WyJBRE1JTiJdLCJleHAiOjE3MDUzMjk2MDB9.signature",
                        "refreshToken",
                        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZG1pbkBiYW50dW9wcy5jb20iLCJ0eXBlIjoicmVmcmVzaCIsImV4cCI6MTcwNTQxNjAwMH0.signature",
                        "expiresIn", 86400,
                        "tokenType", "Bearer",
                        "user", Map.of(
                                "id", 1,
                                "username", "admin@bantuops.com",
                                "roles", Arrays.asList("ADMIN"),
                                "permissions", Arrays.asList("READ", "WRITE", "DELETE", "ADMIN_ACCESS"))));
    }

    /**
     * Exemple de document de bulletin de paie
     */
    private Example createPayslipDocumentExample() {
        return new Example()
                .summary("Bulletin de paie généré")
                .description("Document de bulletin de paie avec signature numérique")
                .value(Map.of(
                        "documentId", "PAYSLIP-2024-001-001",
                        "employeeId", 1,
                        "period", "2024-01",
                        "generatedAt", "2024-01-15T10:30:00Z",
                        "templateUsed", "standard_senegal_v1",
                        "digitalSignature", "SHA256:a1b2c3d4e5f6...",
                        "pdfUrl", "/api/payslips/PAYSLIP-2024-001-001/pdf",
                        "status", "GENERATED",
                        "metadata", Map.of(
                                "generatedBy", "admin@bantuops.com",
                                "companyName", "BantuOps SARL",
                                "companyTaxNumber", "1234567890123",
                                "fileSize", "245KB",
                                "pageCount", 2)));
    }

    /**
     * Exemple de résultat d'opération en lot
     */
    private Example createBulkOperationResultExample() {
        return new Example()
                .summary("Résultat d'opération en lot")
                .description("Résultat de traitement en lot avec statistiques et erreurs")
                .value(Map.of(
                        "totalProcessed", 100,
                        "successful", 95,
                        "failed", 5,
                        "errors", Arrays.asList(
                                Map.of(
                                        "itemId", "EMP-006",
                                        "error", "VALIDATION_ERROR",
                                        "details", Map.of(
                                                "field", "baseSalary",
                                                "message", "Le salaire ne peut pas être négatif")),
                                Map.of(
                                        "itemId", "EMP-012",
                                        "error", "BUSINESS_RULE_VIOLATION",
                                        "details", Map.of(
                                                "rule", "SENEGAL_TAX_COMPLIANCE",
                                                "message", "Numéro fiscal invalide"))),
                        "processedAt", "2024-01-15T10:30:00Z",
                        "duration", 15000));
    }

    /**
     * Exemple de log d'audit
     */
    private Example createAuditLogExample() {
        return new Example()
                .summary("Entrée de log d'audit")
                .description("Exemple d'entrée d'audit pour une modification d'employé")
                .value(Map.of());
    }
}