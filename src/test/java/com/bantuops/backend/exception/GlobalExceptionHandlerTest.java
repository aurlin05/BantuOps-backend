package com.bantuops.backend.exception;

import com.bantuops.backend.dto.PayrollRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests pour le gestionnaire global d'exceptions
 * Conforme aux exigences 4.2, 4.3, 4.4 pour la gestion d'erreurs localisées
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(roles = "HR")
    void shouldHandleValidationErrorsWithLocalizedMessages() throws Exception {
        // Given - Requête invalide (salaire négatif)
        PayrollRequest invalidRequest = PayrollRequest.builder()
            .employeeId(-1L) // ID invalide
            .payrollPeriod(YearMonth.of(2025, 1)) // Période future
            .baseSalary(new BigDecimal("-1000")) // Salaire négatif
            .build();

        // When & Then
        mockMvc.perform(post("/api/payroll/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.details").exists())
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.suggestion").exists());
    }

    @Test
    @WithMockUser(roles = "HR")
    void shouldHandleBusinessRuleViolationsWithSuggestions() throws Exception {
        // Given - Requête avec violation de règle métier (salaire sous SMIG)
        PayrollRequest invalidRequest = PayrollRequest.builder()
            .employeeId(1L)
            .payrollPeriod(YearMonth.of(2024, 1))
            .baseSalary(new BigDecimal("30000")) // Sous le SMIG sénégalais
            .regularHours(new BigDecimal("160"))
            .build();

        // When & Then
        mockMvc.perform(post("/api/payroll/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").exists())
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.suggestion").exists());
    }

    @Test
    void shouldHandleAuthenticationErrorsWithLocalizedMessages() throws Exception {
        // Given - Requête sans authentification
        PayrollRequest request = PayrollRequest.builder()
            .employeeId(1L)
            .payrollPeriod(YearMonth.of(2024, 1))
            .baseSalary(new BigDecimal("150000"))
            .build();

        // When & Then
        mockMvc.perform(post("/api/payroll/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.suggestion").exists());
    }

    @Test
    @WithMockUser(roles = "USER") // Rôle insuffisant
    void shouldHandleAccessDeniedWithLocalizedMessages() throws Exception {
        // Given - Utilisateur avec rôle insuffisant
        PayrollRequest request = PayrollRequest.builder()
            .employeeId(1L)
            .payrollPeriod(YearMonth.of(2024, 1))
            .baseSalary(new BigDecimal("150000"))
            .build();

        // When & Then
        mockMvc.perform(post("/api/payroll/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.suggestion").exists());
    }

    @Test
    @WithMockUser(roles = "HR")
    void shouldHandleResourceNotFoundWithLocalizedMessages() throws Exception {
        // Given - Requête pour un employé inexistant
        PayrollRequest request = PayrollRequest.builder()
            .employeeId(99999L) // ID inexistant
            .payrollPeriod(YearMonth.of(2024, 1))
            .baseSalary(new BigDecimal("150000"))
            .regularHours(new BigDecimal("160"))
            .build();

        // When & Then
        mockMvc.perform(post("/api/payroll/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @WithMockUser(roles = "HR")
    void shouldProvideLocalizedMessagesInFrench() throws Exception {
        // Given - Requête avec en-tête Accept-Language français
        PayrollRequest invalidRequest = PayrollRequest.builder()
            .employeeId(-1L)
            .build();

        // When & Then
        mockMvc.perform(post("/api/payroll/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Accept-Language", "fr")
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(roles = "HR")
    void shouldProvideLocalizedMessagesInEnglish() throws Exception {
        // Given - Requête avec en-tête Accept-Language anglais
        PayrollRequest invalidRequest = PayrollRequest.builder()
            .employeeId(-1L)
            .build();

        // When & Then
        mockMvc.perform(post("/api/payroll/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Accept-Language", "en")
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(roles = "HR")
    void shouldProvideLocalizedMessagesInWolof() throws Exception {
        // Given - Requête avec en-tête Accept-Language wolof
        PayrollRequest invalidRequest = PayrollRequest.builder()
            .employeeId(-1L)
            .build();

        // When & Then
        mockMvc.perform(post("/api/payroll/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Accept-Language", "wo")
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(roles = "HR")
    void shouldIncludeTraceabilityInformation() throws Exception {
        // Given - Requête invalide
        PayrollRequest invalidRequest = PayrollRequest.builder()
            .employeeId(-1L)
            .build();

        // When & Then
        mockMvc.perform(post("/api/payroll/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.path").exists())
            .andExpect(jsonPath("$.code").exists());
    }
}