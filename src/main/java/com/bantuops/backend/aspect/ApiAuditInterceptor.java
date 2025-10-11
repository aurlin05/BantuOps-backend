package com.bantuops.backend.aspect;

import com.bantuops.backend.service.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.time.LocalDateTime;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Intercepteur pour tracer les appels d'API
 * Conforme aux exigences 7.4, 7.5, 7.6, 6.1, 6.2 pour l'audit des APIs
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiAuditInterceptor implements HandlerInterceptor {

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    private static final String REQUEST_START_TIME = "requestStartTime";
    private static final String TRACE_ID = "traceId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Générer un ID de trace unique pour cette requête
        String traceId = UUID.randomUUID().toString();
        request.setAttribute(TRACE_ID, traceId);
        request.setAttribute(REQUEST_START_TIME, System.currentTimeMillis());

        // Ajouter l'ID de trace dans les headers de réponse
        response.setHeader("X-Trace-ID", traceId);

        // Logger le début de la requête
        log.info("API Request Started - TraceID: {}, Method: {}, URI: {}, User: {}", 
                traceId, request.getMethod(), request.getRequestURI(), getCurrentUsername());

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                               Object handler, Exception ex) {
        
        String traceId = (String) request.getAttribute(TRACE_ID);
        Long startTime = (Long) request.getAttribute(REQUEST_START_TIME);
        long duration = System.currentTimeMillis() - (startTime != null ? startTime : 0);

        try {
            // Collecter les informations de la requête
            ApiAuditInfo auditInfo = collectAuditInfo(request, response, traceId, duration, ex);
            
            // Enregistrer l'audit de manière asynchrone
            auditService.logApiCall(auditInfo);
            
            // Logger la fin de la requête
            log.info("API Request Completed - TraceID: {}, Status: {}, Duration: {}ms", 
                    traceId, response.getStatus(), duration);
            
            // Logger les erreurs si présentes
            if (ex != null) {
                log.error("API Request Failed - TraceID: {}, Error: {}", traceId, ex.getMessage(), ex);
            }
            
        } catch (Exception e) {
            log.error("Erreur lors de l'audit de l'API - TraceID: {}, Error: {}", traceId, e.getMessage());
        }
    }

    /**
     * Collecte les informations d'audit pour un appel d'API
     */
    private ApiAuditInfo collectAuditInfo(HttpServletRequest request, HttpServletResponse response, 
                                         String traceId, long duration, Exception exception) {
        
        return ApiAuditInfo.builder()
            .traceId(traceId)
            .timestamp(LocalDateTime.now())
            .method(request.getMethod())
            .uri(request.getRequestURI())
            .queryString(request.getQueryString())
            .userAgent(request.getHeader("User-Agent"))
            .ipAddress(getClientIpAddress(request))
            .username(getCurrentUsername())
            .userRoles(getCurrentUserRoles())
            .requestHeaders(extractHeaders(request))
            .requestBody(extractRequestBody(request))
            .responseStatus(response.getStatus())
            .responseHeaders(extractResponseHeaders(response))
            .responseBody(extractResponseBody(response))
            .duration(duration)
            .success(exception == null && response.getStatus() < 400)
            .errorMessage(exception != null ? exception.getMessage() : null)
            .errorType(exception != null ? exception.getClass().getSimpleName() : null)
            .sessionId(request.getSession(false) != null ? request.getSession().getId() : null)
            .build();
    }

    /**
     * Extrait les headers de la requête
     */
    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            
            // Masquer les headers sensibles
            if (isSensitiveHeader(headerName)) {
                headerValue = "[MASKED]";
            }
            
            headers.put(headerName, headerValue);
        }
        
        return headers;
    }

    /**
     * Extrait les headers de la réponse
     */
    private Map<String, String> extractResponseHeaders(HttpServletResponse response) {
        Map<String, String> headers = new HashMap<>();
        
        for (String headerName : response.getHeaderNames()) {
            String headerValue = response.getHeader(headerName);
            headers.put(headerName, headerValue);
        }
        
        return headers;
    }

    /**
     * Extrait le corps de la requête
     */
    private String extractRequestBody(HttpServletRequest request) {
        try {
            if (request instanceof ContentCachingRequestWrapper) {
                ContentCachingRequestWrapper wrapper = (ContentCachingRequestWrapper) request;
                byte[] content = wrapper.getContentAsByteArray();
                
                if (content.length > 0) {
                    String body = new String(content, wrapper.getCharacterEncoding());
                    
                    // Masquer les données sensibles dans le corps de la requête
                    return maskSensitiveData(body);
                }
            }
        } catch (Exception e) {
            log.warn("Impossible d'extraire le corps de la requête: {}", e.getMessage());
        }
        
        return null;
    }

    /**
     * Extrait le corps de la réponse
     */
    private String extractResponseBody(HttpServletResponse response) {
        try {
            if (response instanceof ContentCachingResponseWrapper) {
                ContentCachingResponseWrapper wrapper = (ContentCachingResponseWrapper) response;
                byte[] content = wrapper.getContentAsByteArray();
                
                if (content.length > 0) {
                    String body = new String(content, wrapper.getCharacterEncoding());
                    
                    // Masquer les données sensibles dans le corps de la réponse
                    return maskSensitiveData(body);
                }
            }
        } catch (Exception e) {
            log.warn("Impossible d'extraire le corps de la réponse: {}", e.getMessage());
        }
        
        return null;
    }

    /**
     * Masque les données sensibles dans le JSON
     */
    private String maskSensitiveData(String jsonContent) {
        if (jsonContent == null || jsonContent.trim().isEmpty()) {
            return jsonContent;
        }

        try {
            // Masquer les champs sensibles courants
            String maskedContent = jsonContent
                .replaceAll("(\"password\"\\s*:\\s*\")[^\"]*\"", "$1[MASKED]\"")
                .replaceAll("(\"token\"\\s*:\\s*\")[^\"]*\"", "$1[MASKED]\"")
                .replaceAll("(\"nationalId\"\\s*:\\s*\")[^\"]*\"", "$1[MASKED]\"")
                .replaceAll("(\"email\"\\s*:\\s*\")[^\"]*\"", "$1[MASKED]\"")
                .replaceAll("(\"phoneNumber\"\\s*:\\s*\")[^\"]*\"", "$1[MASKED]\"")
                .replaceAll("(\"bankAccount\"\\s*:\\s*\")[^\"]*\"", "$1[MASKED]\"");
            
            // Limiter la taille du contenu loggé
            if (maskedContent.length() > 1000) {
                maskedContent = maskedContent.substring(0, 1000) + "... [TRUNCATED]";
            }
            
            return maskedContent;
        } catch (Exception e) {
            log.warn("Erreur lors du masquage des données sensibles: {}", e.getMessage());
            return "[CONTENT_MASKED_DUE_TO_ERROR]";
        }
    }

    /**
     * Vérifie si un header est sensible
     */
    private boolean isSensitiveHeader(String headerName) {
        String lowerHeaderName = headerName.toLowerCase();
        return lowerHeaderName.contains("authorization") ||
               lowerHeaderName.contains("cookie") ||
               lowerHeaderName.contains("x-api-key") ||
               lowerHeaderName.contains("x-auth-token");
    }

    /**
     * Récupère le nom d'utilisateur actuel
     */
    private String getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            return authentication != null ? authentication.getName() : "anonymous";
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Récupère les rôles de l'utilisateur actuel
     */
    private String getCurrentUserRoles() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getAuthorities() != null) {
                return authentication.getAuthorities().toString();
            }
        } catch (Exception e) {
            log.debug("Impossible de récupérer les rôles utilisateur: {}", e.getMessage());
        }
        return "[]";
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

    /**
     * Classe pour les informations d'audit d'API
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ApiAuditInfo {
        private String traceId;
        private LocalDateTime timestamp;
        private String method;
        private String uri;
        private String queryString;
        private String userAgent;
        private String ipAddress;
        private String username;
        private String userRoles;
        private Map<String, String> requestHeaders;
        private String requestBody;
        private int responseStatus;
        private Map<String, String> responseHeaders;
        private String responseBody;
        private long duration;
        private boolean success;
        private String errorMessage;
        private String errorType;
        private String sessionId;
    }
}