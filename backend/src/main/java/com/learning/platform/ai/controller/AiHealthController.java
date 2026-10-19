package com.learning.platform.ai.controller;

import com.learning.platform.ai.application.AiTutorApplicationService;
import com.learning.platform.ai.context.Neo4jLearningContextService;
import com.learning.platform.ai.domain.AiModelProvider;
import com.learning.platform.common.Result;
import com.learning.platform.service.CurrentUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiHealthController {
    private static final long HEALTH_CACHE_MS = 15000L;
    private final CurrentUserService currentUserService;
    private final AiModelProvider provider;
    private final Neo4jLearningContextService neo4jService;
    private final AiTutorApplicationService tutorService;
    private volatile HealthSnapshot cachedHealth;

    public AiHealthController(CurrentUserService currentUserService,
                              AiModelProvider provider,
                              Neo4jLearningContextService neo4jService,
                              AiTutorApplicationService tutorService) {
        this.currentUserService = currentUserService;
        this.provider = provider;
        this.neo4jService = neo4jService;
        this.tutorService = tutorService;
    }

    @GetMapping("/health")
    public Result<Map<String, Object>> health(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> user = currentUserService.requireLogin(authorization);
        HealthSnapshot snapshot = currentHealth();
        Map<String, Object> providerHealth = snapshot.provider;
        Map<String, Object> neo4jHealth = snapshot.neo4j;
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("available", Boolean.TRUE.equals(providerHealth.get("available")));
        result.put("provider", provider.providerCode());
        result.put("neo4jAvailable", Boolean.TRUE.equals(neo4jHealth.get("available")));
        result.put("activeRequests", tutorService.activeCount());
        result.put("queuedRequests", tutorService.queueSize());
        if ("admin".equals(user.get("role"))) {
            result.put("model", provider.modelCode());
            result.put("modelService", providerHealth);
            if ("ollama".equals(provider.providerCode())) result.put("ollama", providerHealth);
            result.put("neo4j", neo4jHealth);
        }
        return Result.success(result);
    }

    private HealthSnapshot currentHealth() {
        long now = System.currentTimeMillis();
        HealthSnapshot snapshot = cachedHealth;
        if (snapshot != null && now < snapshot.expiresAt) return snapshot;
        synchronized (this) {
            now = System.currentTimeMillis();
            snapshot = cachedHealth;
            if (snapshot != null && now < snapshot.expiresAt) return snapshot;
            snapshot = new HealthSnapshot(new LinkedHashMap<String, Object>(provider.health()),
                    new LinkedHashMap<String, Object>(neo4jService.health()), now + HEALTH_CACHE_MS);
            cachedHealth = snapshot;
            return snapshot;
        }
    }

    private static class HealthSnapshot {
        private final Map<String, Object> provider;
        private final Map<String, Object> neo4j;
        private final long expiresAt;

        private HealthSnapshot(Map<String, Object> provider, Map<String, Object> neo4j, long expiresAt) {
            this.provider = provider;
            this.neo4j = neo4j;
            this.expiresAt = expiresAt;
        }
    }
}
