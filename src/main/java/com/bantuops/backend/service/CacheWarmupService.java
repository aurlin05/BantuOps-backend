package com.bantuops.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CacheWarmupService {

    private final CachedCalculationService cachedCalculationService;

    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void warmUpCacheOnStartup() {
        log.info("Application ready, starting cache warm-up");
        
        try {
            // Warm up calculation caches
            cachedCalculationService.warmUpCache();
            
            log.info("Cache warm-up completed successfully");
        } catch (Exception e) {
            log.error("Cache warm-up failed", e);
        }
    }
}