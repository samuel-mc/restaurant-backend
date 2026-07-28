package com.platolisto.restaurant_backend.security;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limit / lockout en memoria para login por PIN (por IP + staffId).
 */
@Service
public class LoginAttemptService {

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);

    private final ConcurrentHashMap<String, AttemptState> attempts = new ConcurrentHashMap<>();

    public void assertNotLocked(String key) {
        cleanupExpired();
        AttemptState state = attempts.get(key);
        if (state == null) {
            return;
        }
        Instant lockedUntil = state.lockedUntil;
        if (lockedUntil != null && lockedUntil.isAfter(Instant.now())) {
            throw new TooManyLoginAttemptsException(
                    "Demasiados intentos fallidos. Espera unos minutos e inténtalo de nuevo."
            );
        }
    }

    public void recordFailure(String key) {
        AttemptState state = attempts.computeIfAbsent(key, ignored -> new AttemptState());
        Instant now = Instant.now();
        if (state.windowStarted.isBefore(now.minus(ATTEMPT_WINDOW))) {
            state.windowStarted = now;
            state.failures.set(0);
            state.lockedUntil = null;
        }
        int failures = state.failures.incrementAndGet();
        if (failures >= MAX_FAILURES) {
            state.lockedUntil = now.plus(LOCK_DURATION);
        }
    }

    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, AttemptState>> it = attempts.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, AttemptState> entry = it.next();
            AttemptState state = entry.getValue();
            boolean unlocked = state.lockedUntil == null || state.lockedUntil.isBefore(now);
            boolean windowExpired = state.windowStarted.isBefore(now.minus(ATTEMPT_WINDOW));
            if (unlocked && windowExpired && state.failures.get() == 0) {
                it.remove();
            } else if (unlocked && windowExpired && (state.lockedUntil == null || state.lockedUntil.isBefore(now))) {
                // Ventana vieja sin lock activo: limpiar.
                if (state.lockedUntil == null || !state.lockedUntil.isAfter(now)) {
                    it.remove();
                }
            }
        }
    }

    private static final class AttemptState {
        private final AtomicInteger failures = new AtomicInteger(0);
        private volatile Instant windowStarted = Instant.now();
        private volatile Instant lockedUntil;
    }
}
