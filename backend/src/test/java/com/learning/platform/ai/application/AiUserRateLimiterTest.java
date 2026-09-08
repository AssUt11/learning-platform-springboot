package com.learning.platform.ai.application;

import com.learning.platform.ai.config.AiProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiUserRateLimiterTest {
    @Test
    void enforcesTheConfiguredLimitPerUser() {
        AiProperties properties = new AiProperties();
        properties.setRateLimitPerMinute(2);
        AiUserRateLimiter limiter = new AiUserRateLimiter(properties);

        assertTrue(limiter.tryAcquire(1L));
        assertTrue(limiter.tryAcquire(1L));
        assertFalse(limiter.tryAcquire(1L));
        assertTrue(limiter.tryAcquire(2L));
    }
}
