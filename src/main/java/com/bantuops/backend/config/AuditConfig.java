package com.bantuops.backend.config;

import com.bantuops.backend.aspect.ApiAuditInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration pour l'audit des APIs
 * Conforme aux exigences 7.4, 7.5, 7.6, 6.1, 6.2 pour la configuration d'audit
 */
@Configuration
@RequiredArgsConstructor
public class AuditConfig implements WebMvcConfigurer {

    private final ApiAuditInterceptor apiAuditInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiAuditInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns(
                "/api/docs/**",
                "/api/health/**",
                "/actuator/**",
                "/swagger-ui/**",
                "/v3/api-docs/**"
            );
    }
}