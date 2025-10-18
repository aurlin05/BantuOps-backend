package com.bantuops.backend.config;

import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration pour personnaliser l'interface Swagger UI
 * Ajoute des styles personnalisés et améliore l'expérience utilisateur
 */
@Configuration
public class SwaggerUIConfig implements WebMvcConfigurer {

    /**
     * Configure les propriétés personnalisées de Swagger UI
     */
    @Bean
    public SwaggerUiConfigProperties swaggerUiConfigProperties() {
        SwaggerUiConfigProperties properties = new SwaggerUiConfigProperties();
        
        // Configuration de l'interface
        properties.setDocExpansion("none"); // Collapse all operations by default
        properties.setDefaultModelsExpandDepth(2); // Expand models to depth 2
        properties.setDefaultModelExpandDepth(2);
        properties.setDisplayRequestDuration(true); // Show request duration
        properties.setFilter("true"); // Enable filtering
        properties.setShowExtensions(true); // Show vendor extensions
        properties.setShowCommonExtensions(true);
        properties.setTryItOutEnabled(true); // Enable "Try it out" by default
        
        // Configuration des opérations
        properties.setOperationsSorter("alpha"); // Sort operations alphabetically
        properties.setTagsSorter("alpha"); // Sort tags alphabetically
        
        // Configuration de la validation
        properties.setValidatorUrl(""); // Disable validator badge
        
        // Configuration de l'affichage
        properties.setDeepLinking(true); // Enable deep linking
        properties.setDisplayOperationId(false); // Hide operation IDs
        
        // Personnalisation de l'interface
        properties.setSyntaxHighlight(new SwaggerUiConfigProperties.SyntaxHighlight());
        properties.getSyntaxHighlight().setActivated(true);
        properties.getSyntaxHighlight().setTheme("agate"); // Dark theme for code
        
        // Configuration des URLs
        properties.setConfigUrl("/v3/api-docs/swagger-config");
        properties.setUrl("/v3/api-docs");
        
        return properties;
    }



    /**
     * Configure les ressources statiques pour inclure les fichiers CSS personnalisés
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Ajouter les ressources statiques personnalisées
        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/swagger-ui/")
                .addResourceLocations("classpath:/static/swagger-ui/")
                .resourceChain(false);
        
        // Ajouter les ressources pour les assets
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/")
                .resourceChain(false);
    }
}