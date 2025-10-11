package com.bantuops.backend.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
@EnableRedisHttpSession(
    maxInactiveIntervalInSeconds = 3600, // 1 hour session timeout
    redisNamespace = "bantuops:session",
    cleanupCron = "0 */5 * * * *" // Cleanup every 5 minutes
)
@Slf4j
public class RedisConfig {

    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Configure JSON serialization
        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<>(Object.class);
        ObjectMapper objectMapper = createObjectMapper();
        jackson2JsonRedisSerializer.setObjectMapper(objectMapper);

        // Set serializers
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jackson2JsonRedisSerializer);
        template.setHashValueSerializer(jackson2JsonRedisSerializer);

        template.afterPropertiesSet();
        log.info("Redis template configured successfully");
        return template;
    }

    @Bean
    public RedisTemplate<String, String> stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Default cache configuration
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1)) // Default TTL: 1 hour
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(createObjectMapper())))
                .disableCachingNullValues();

        // Specific cache configurations with different TTLs
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // Employee data cache - 2 hours TTL
        cacheConfigurations.put("employees", defaultConfig
                .entryTtl(Duration.ofHours(2)));

        // Payroll calculations cache - 30 minutes TTL (frequently changing)
        cacheConfigurations.put("payroll-calculations", defaultConfig
                .entryTtl(Duration.ofMinutes(30)));

        // Tax rates cache - 24 hours TTL (rarely changing)
        cacheConfigurations.put("tax-rates", defaultConfig
                .entryTtl(Duration.ofHours(24)));

        // Attendance rules cache - 12 hours TTL
        cacheConfigurations.put("attendance-rules", defaultConfig
                .entryTtl(Duration.ofHours(12)));

        // User permissions cache - 1 hour TTL
        cacheConfigurations.put("user-permissions", defaultConfig
                .entryTtl(Duration.ofHours(1)));

        // Financial reports cache - 15 minutes TTL (for dashboard data)
        cacheConfigurations.put("financial-reports", defaultConfig
                .entryTtl(Duration.ofMinutes(15)));

        // Invoice data cache - 1 hour TTL
        cacheConfigurations.put("invoices", defaultConfig
                .entryTtl(Duration.ofHours(1)));

        // System configuration cache - 6 hours TTL
        cacheConfigurations.put("system-config", defaultConfig
                .entryTtl(Duration.ofHours(6)));

        // Frequent calculations cache - 10 minutes TTL (for real-time calculations)
        cacheConfigurations.put("frequent-calculations", defaultConfig
                .entryTtl(Duration.ofMinutes(10)));

        // Session metadata cache - 25 hours TTL (outlives session)
        cacheConfigurations.put("session-metadata", defaultConfig
                .entryTtl(Duration.ofHours(25)));

        // Business rules cache - 4 hours TTL
        cacheConfigurations.put("business-rules", defaultConfig
                .entryTtl(Duration.ofHours(4)));

        // Audit cache - 2 hours TTL
        cacheConfigurations.put("audit-cache", defaultConfig
                .entryTtl(Duration.ofHours(2)));

        RedisCacheManager cacheManager = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .enableStatistics() // Enable cache statistics for monitoring
                .build();

        log.info("Redis cache manager configured with {} cache configurations", cacheConfigurations.size());
        return cacheManager;
    }

    @Bean
    public RedisTemplate<String, Object> sessionRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Configure serialization for session data
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(createObjectMapper());
        
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        
        template.afterPropertiesSet();
        log.info("Session Redis template configured successfully");
        return template;
    }

    @Bean
    public RedisTemplate<String, String> distributedLockTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use string serialization for distributed locks
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);
        
        template.afterPropertiesSet();
        log.info("Distributed lock Redis template configured successfully");
        return template;
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.findAndRegisterModules();
        return objectMapper;
    }
}