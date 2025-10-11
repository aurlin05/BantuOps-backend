package com.bantuops.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests pour la configuration OpenAPI
 * Vérifie que la documentation API est correctement configurée
 */
@SpringBootTest
@ActiveProfiles("test")
class OpenApiConfigTest {

    @Autowired
    private OpenApiConfig openApiConfig;

    @Test
    void shouldCreateCustomOpenAPIConfiguration() {
        // Given & When
        OpenAPI openAPI = openApiConfig.customOpenAPI();

        // Then
        assertThat(openAPI).isNotNull();
        assertThat(openAPI.getInfo()).isNotNull();
        assertThat(openAPI.getInfo().getTitle()).isEqualTo("BantuOps Backend API");
        assertThat(openAPI.getInfo().getVersion()).isEqualTo("1.0.0");
        assertThat(openAPI.getInfo().getDescription()).contains("API REST sécurisée");
        assertThat(openAPI.getInfo().getDescription()).contains("PME sénégalaises");
    }

    @Test
    void shouldHaveSecurityConfiguration() {
        // Given & When
        OpenAPI openAPI = openApiConfig.customOpenAPI();

        // Then
        assertThat(openAPI.getSecurity()).isNotEmpty();
        assertThat(openAPI.getSecurity().get(0).get("bearerAuth")).isNotNull();
    }

    @Test
    void shouldHaveTagsConfiguration() {
        // Given & When
        OpenAPI openAPI = openApiConfig.customOpenAPI();

        // Then
        assertThat(openAPI.getTags()).isNotEmpty();
        assertThat(openAPI.getTags()).hasSize(6);
        
        // Vérifier que les tags principaux sont présents
        assertThat(openAPI.getTags().stream()
            .anyMatch(tag -> "Authentification".equals(tag.getName()))).isTrue();
        assertThat(openAPI.getTags().stream()
            .anyMatch(tag -> "Gestion de Paie".equals(tag.getName()))).isTrue();
        assertThat(openAPI.getTags().stream()
            .anyMatch(tag -> "Gestion Financière".equals(tag.getName()))).isTrue();
        assertThat(openAPI.getTags().stream()
            .anyMatch(tag -> "Gestion RH".equals(tag.getName()))).isTrue();
        assertThat(openAPI.getTags().stream()
            .anyMatch(tag -> "Gestion des Employés".equals(tag.getName()))).isTrue();
    }

    @Test
    void shouldHaveComponentsConfiguration() {
        // Given & When
        OpenAPI openAPI = openApiConfig.customOpenAPI();

        // Then
        assertThat(openAPI.getComponents()).isNotNull();
        assertThat(openAPI.getComponents().getSecuritySchemes()).containsKey("bearerAuth");
        assertThat(openAPI.getComponents().getResponses()).isNotEmpty();
        assertThat(openAPI.getComponents().getSchemas()).isNotEmpty();
        assertThat(openAPI.getComponents().getExamples()).isNotEmpty();
    }

    @Test
    void shouldHaveStandardErrorResponses() {
        // Given & When
        OpenAPI openAPI = openApiConfig.customOpenAPI();

        // Then
        assertThat(openAPI.getComponents().getResponses()).containsKey("BadRequest");
        assertThat(openAPI.getComponents().getResponses()).containsKey("Unauthorized");
        assertThat(openAPI.getComponents().getResponses()).containsKey("Forbidden");
        assertThat(openAPI.getComponents().getResponses()).containsKey("NotFound");
        assertThat(openAPI.getComponents().getResponses()).containsKey("Conflict");
        assertThat(openAPI.getComponents().getResponses()).containsKey("UnprocessableEntity");
        assertThat(openAPI.getComponents().getResponses()).containsKey("InternalServerError");
    }

    @Test
    void shouldHaveDataSchemas() {
        // Given & When
        OpenAPI openAPI = openApiConfig.customOpenAPI();

        // Then
        assertThat(openAPI.getComponents().getSchemas()).containsKey("PayrollRequest");
        assertThat(openAPI.getComponents().getSchemas()).containsKey("PayrollResult");
        assertThat(openAPI.getComponents().getSchemas()).containsKey("InvoiceRequest");
        assertThat(openAPI.getComponents().getSchemas()).containsKey("InvoiceResponse");
        assertThat(openAPI.getComponents().getSchemas()).containsKey("EmployeeRequest");
        assertThat(openAPI.getComponents().getSchemas()).containsKey("EmployeeResponse");
        assertThat(openAPI.getComponents().getSchemas()).containsKey("AttendanceRequest");
        assertThat(openAPI.getComponents().getSchemas()).containsKey("ErrorResponse");
    }

    @Test
    void shouldHaveExamples() {
        // Given & When
        OpenAPI openAPI = openApiConfig.customOpenAPI();

        // Then
        assertThat(openAPI.getComponents().getExamples()).containsKey("PayrollRequestExample");
        assertThat(openAPI.getComponents().getExamples()).containsKey("PayrollResponseExample");
        assertThat(openAPI.getComponents().getExamples()).containsKey("InvoiceRequestExample");
        assertThat(openAPI.getComponents().getExamples()).containsKey("InvoiceResponseExample");
        assertThat(openAPI.getComponents().getExamples()).containsKey("EmployeeRequestExample");
        assertThat(openAPI.getComponents().getExamples()).containsKey("EmployeeResponseExample");
        assertThat(openAPI.getComponents().getExamples()).containsKey("AttendanceRequestExample");
        assertThat(openAPI.getComponents().getExamples()).containsKey("ErrorResponseExample");
    }

    @Test
    void shouldHaveContactInformation() {
        // Given & When
        OpenAPI openAPI = openApiConfig.customOpenAPI();

        // Then
        assertThat(openAPI.getInfo().getContact()).isNotNull();
        assertThat(openAPI.getInfo().getContact().getName()).isEqualTo("Équipe BantuOps");
        assertThat(openAPI.getInfo().getContact().getEmail()).isEqualTo("support@bantuops.com");
        assertThat(openAPI.getInfo().getContact().getUrl()).isEqualTo("https://bantuops.com");
    }

    @Test
    void shouldHaveLicenseInformation() {
        // Given & When
        OpenAPI openAPI = openApiConfig.customOpenAPI();

        // Then
        assertThat(openAPI.getInfo().getLicense()).isNotNull();
        assertThat(openAPI.getInfo().getLicense().getName()).isEqualTo("Propriétaire");
        assertThat(openAPI.getInfo().getLicense().getUrl()).isEqualTo("https://bantuops.com/license");
    }
}