package com.platolisto.restaurant_backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limit en memoria para creación pública de pedidos ({@code POST /api/v1/orders}).
 * Suficiente en una sola instancia; sin Redis.
 */
@Service
public class OrderCreationRateLimitService {

    private final int maxAttempts;
    private final Duration window;

    private final ConcurrentHashMap<String, AttemptWindow> attempts = new ConcurrentHashMap<>();

    public OrderCreationRateLimitService(
            @Value("${application.security.orders.max-attempts-per-key:30}") int maxAttempts,
            @Value("${application.security.orders.window-minutes:15}") long windowMinutes
    ) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException(
                    "application.security.orders.max-attempts-per-key debe ser >= 1."
            );
        }
        if (windowMinutes < 1) {
            throw new IllegalArgumentException(
                    "application.security.orders.window-minutes debe ser >= 1."
            );
        }
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    public void assertAllowed(String... keys) {
        cleanupExpired();
        Instant now = Instant.now();
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            AttemptWindow state = attempts.get(key);
            if (state == null) {
                continue;
            }
            if (state.windowStarted.isBefore(now.minus(window))) {
                continue;
            }
            if (state.count.get() >= maxAttempts) {
                throw new RateLimitExceededException(
                        "Demasiados pedidos en poco tiempo. Espera un rato e inténtalo de nuevo."
                );
            }
        }
    }

    public void record(String... keys) {
        Instant now = Instant.now();
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            AttemptWindow state = attempts.computeIfAbsent(key, ignored -> new AttemptWindow(now));
            if (state.windowStarted.isBefore(now.minus(window))) {
                state.windowStarted = now;
                state.count.set(0);
            }
            state.count.incrementAndGet();
        }
    }

    private void cleanupExpired() {
        Instant cutoff = Instant.now().minus(window);
        Iterator<Map.Entry<String, AttemptWindow>> it = attempts.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, AttemptWindow> entry = it.next();
            if (entry.getValue().windowStarted.isBefore(cutoff)) {
                it.remove();
            }
        }
    }

    private static final class AttemptWindow {
        private volatile Instant windowStarted;
        private final AtomicInteger count = new AtomicInteger(0);

        private AttemptWindow(Instant started) {
            this.windowStarted = started;
        }
    }
}
