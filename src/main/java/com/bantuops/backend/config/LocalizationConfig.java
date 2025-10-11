package com.bantuops.backend.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.util.Arrays;
import java.util.Locale;

/**
 * Configuration pour la localisation et l'internationalisation
 * Support du français, anglais et wolof pour le contexte sénégalais
 * Conforme aux exigences 4.2, 4.3, 4.4 pour les messages localisés
 */
@Configuration
public class LocalizationConfig implements WebMvcConfigurer {

    /**
     * Configuration du MessageSource pour les messages localisés
     */
    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasename("classpath:messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setCacheSeconds(3600); // Cache pendant 1 heure
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setDefaultLocale(Locale.FRENCH); // Français par défaut pour le Sénégal
        return messageSource;
    }

    /**
     * Configuration du LocaleResolver pour détecter la langue
     */
    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver localeResolver = new AcceptHeaderLocaleResolver();
        localeResolver.setSupportedLocales(Arrays.asList(
            Locale.FRENCH,      // fr - Français (langue officielle du Sénégal)
            Locale.ENGLISH,     // en - Anglais (pour les utilisateurs internationaux)
            new Locale("wo")    // wo - Wolof (langue nationale du Sénégal)
        ));
        localeResolver.setDefaultLocale(Locale.FRENCH);
        return localeResolver;
    }

    /**
     * Intercepteur pour changer la langue via paramètre de requête
     */
    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang"); // ?lang=en, ?lang=fr, ?lang=wo
        return interceptor;
    }

    /**
     * Enregistrement de l'intercepteur de changement de langue
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }
}