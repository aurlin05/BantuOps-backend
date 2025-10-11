package com.bantuops.backend.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Filtre pour logger les requêtes et réponses HTTP
 * Conforme aux exigences 7.4, 7.5, 7.6, 6.1, 6.2 pour le logging des APIs
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RequestResponseLoggingFilter implements Filter {

    private static final List<String> EXCLUDED_PATHS = Arrays.asList(
        "/actuator",
        "/swagger-ui",
        "/v3/api-docs",
        "/favicon.ico",
        "/static",
        "/css",
        "/js",
        "/images"
    );

    private static final List<String> SENSITIVE_HEADERS = Arrays.asList(
        "authorization",
        "cookie",
        "x-api-key",
        "x-auth-token"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Vérifier si le chemin doit être exclu du logging
        if (shouldExcludePath(httpRequest.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        // Wrapper les requêtes et réponses pour pouvoir lire le contenu
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(httpRequest);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(httpResponse);

        long startTime = System.currentTimeMillis();

        try {
            // Logger la requête entrante
            logRequest(wrappedRequest);

            // Continuer la chaîne de filtres
            chain.doFilter(wrappedRequest, wrappedResponse);

        } finally {
            long duration = System.currentTimeMillis() - startTime;

            // Logger la réponse sortante
            logResponse(wrappedResponse, duration);

            // Important: copier le contenu de la réponse vers la réponse originale
            wrappedResponse.copyBodyToResponse();
        }
    }

    /**
     * Logger les détails de la requête
     */
    private void logRequest(ContentCachingRequestWrapper request) {
        try {
            StringBuilder logMessage = new StringBuilder();
            logMessage.append("\n=== REQUÊTE HTTP ENTRANTE ===\n");
            logMessage.append("Méthode: ").append(request.getMethod()).append("\n");
            logMessage.append("URI: ").append(request.getRequestURI()).append("\n");
            
            if (request.getQueryString() != null) {
                logMessage.append("Query String: ").append(request.getQueryString()).append("\n");
            }
            
            logMessage.append("IP Client: ").append(getClientIpAddress(request)).append("\n");
            logMessage.append("User-Agent: ").append(request.getHeader("User-Agent")).append("\n");
            
            // Logger les headers (en masquant les sensibles)
            logMessage.append("Headers:\n");
            request.getHeaderNames().asIterator().forEachRemaining(headerName -> {
                String headerValue = request.getHeader(headerName);
                if (isSensitiveHeader(headerName)) {
                    headerValue = "[MASKED]";
                }
                logMessage.append("  ").append(headerName).append(": ").append(headerValue).append("\n");
            });

            // Logger le corps de la requête si présent
            String requestBody = getRequestBody(request);
            if (requestBody != null && !requestBody.isEmpty()) {
                logMessage.append("Corps de la requête:\n");
                logMessage.append(maskSensitiveData(requestBody)).append("\n");
            }

            logMessage.append("================================");
            
            log.info(logMessage.toString());

        } catch (Exception e) {
            log.error("Erreur lors du logging de la requête: {}", e.getMessage());
        }
    }

    /**
     * Logger les détails de la réponse
     */
    private void logResponse(ContentCachingResponseWrapper response, long duration) {
        try {
            StringBuilder logMessage = new StringBuilder();
            logMessage.append("\n=== RÉPONSE HTTP SORTANTE ===\n");
            logMessage.append("Statut: ").append(response.getStatus()).append("\n");
            logMessage.append("Durée: ").append(duration).append(" ms\n");
            
            // Logger les headers de réponse
            logMessage.append("Headers:\n");
            response.getHeaderNames().forEach(headerName -> {
                String headerValue = response.getHeader(headerName);
                logMessage.append("  ").append(headerName).append(": ").append(headerValue).append("\n");
            });

            // Logger le corps de la réponse si présent et si ce n'est pas trop volumineux
            String responseBody = getResponseBody(response);
            if (responseBody != null && !responseBody.isEmpty()) {
                if (responseBody.length() <= 1000) {
                    logMessage.append("Corps de la réponse:\n");
                    logMessage.append(maskSensitiveData(responseBody)).append("\n");
                } else {
                    logMessage.append("Corps de la réponse: [TROP VOLUMINEUX - ").append(responseBody.length()).append(" caractères]\n");
                }
            }

            logMessage.append("===============================");
            
            // Utiliser différents niveaux de log selon le statut
            if (response.getStatus() >= 500) {
                log.error(logMessage.toString());
            } else if (response.getStatus() >= 400) {
                log.warn(logMessage.toString());
            } else {
                log.info(logMessage.toString());
            }

        } catch (Exception e) {
            log.error("Erreur lors du logging de la réponse: {}", e.getMessage());
        }
    }

    /**
     * Extrait le corps de la requête
     */
    private String getRequestBody(ContentCachingRequestWrapper request) {
        try {
            byte[] content = request.getContentAsByteArray();
            if (content.length > 0) {
                return new String(content, request.getCharacterEncoding());
            }
        } catch (Exception e) {
            log.debug("Impossible de lire le corps de la requête: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Extrait le corps de la réponse
     */
    private String getResponseBody(ContentCachingResponseWrapper response) {
        try {
            byte[] content = response.getContentAsByteArray();
            if (content.length > 0) {
                return new String(content, response.getCharacterEncoding());
            }
        } catch (Exception e) {
            log.debug("Impossible de lire le corps de la réponse: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Masque les données sensibles dans le contenu
     */
    private String maskSensitiveData(String content) {
        if (content == null || content.trim().isEmpty()) {
            return content;
        }

        try {
            // Masquer les champs sensibles courants dans le JSON
            return content
                .replaceAll("(\"password\"\\s*:\\s*\")[^\"]*\"", "$1[MASKED]\"")
                .replaceAll("(\"token\"\\s*:\\s*\")[^\"]*\"", "$1[MASKED]\"")
                .replaceAll("(\"nationalId\"\\s*:\\s*\")[^\"]*\"", "$1[MASKED]\"")
                .replaceAll("(\"email\"\\s*:\\s*\")[^\"]*\"", "$1[MASKED]\"")
                .replaceAll("(\"phoneNumber\"\\s*:\\s*\")[^\"]*\"", "$1[MASKED]\"")
                .replaceAll("(\"bankAccount\"\\s*:\\s*\")[^\"]*\"", "$1[MASKED]\"")
                .replaceAll("(\"ssn\"\\s*:\\s*\")[^\"]*\"", "$1[MASKED]\"")
                .replaceAll("(\"creditCard\"\\s*:\\s*\")[^\"]*\"", "$1[MASKED]\"");
        } catch (Exception e) {
            log.warn("Erreur lors du masquage des données sensibles: {}", e.getMessage());
            return "[CONTENT_MASKED_DUE_TO_ERROR]";
        }
    }

    /**
     * Vérifie si un header est sensible
     */
    private boolean isSensitiveHeader(String headerName) {
        return SENSITIVE_HEADERS.stream()
            .anyMatch(sensitive -> headerName.toLowerCase().contains(sensitive));
    }

    /**
     * Vérifie si le chemin doit être exclu du logging
     */
    private boolean shouldExcludePath(String path) {
        return EXCLUDED_PATHS.stream()
            .anyMatch(excluded -> path.startsWith(excluded));
    }

    /**
     * Récupère l'adresse IP du client
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}