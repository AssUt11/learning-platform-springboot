package com.learning.platform.ai.application;

import com.learning.platform.ai.config.AiProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AiUserRateLimiter {
    private static final long WINDOW_MS = 60_000L;
    private final int limit;
    private final Map<Long, UserRateWindow> windows = new ConcurrentHashMap<Long, UserRateWindow>();

    public AiUserRateLimiter(AiProperties properties) {
        this.limit = Math.max(1, properties.getRateLimitPerMinute());
    }

    public boolean tryAcquire(long userId) {
        UserRateWindow window = windows.computeIfAbsent(userId, ignored -> new UserRateWindow());
        synchronized (window) {
            long now = System.currentTimeMillis();
            if (now - window.startedAt >= WINDOW_MS) {
                window.startedAt = now;
                window.count = 0;
            }
            if (window.count >= limit) return false;
            window.count++;
            return true;
        }
    }

    private static class UserRateWindow {
        private long startedAt = System.currentTimeMillis();
        private int count;
    }
}
