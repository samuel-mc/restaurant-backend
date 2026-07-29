package com.platolisto.restaurant_backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Códigos de un solo uso para handoff de impersonación (sin Redis).
 * El JWT no viaja en la URL: SuperAdmin recibe un {@code code} corto;
 * el subdominio del tenant lo canjea una vez por el JWT.
 */
@Service
public class ImpersonationHandoffService {

    private static final int CODE_BYTES = 32;

    private final Duration ttl;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentHashMap<String, HandoffEntry> codes = new ConcurrentHashMap<>();

    public ImpersonationHandoffService(
            @Value("${application.security.impersonation.handoff-ttl-seconds:90}") long ttlSeconds
    ) {
        if (ttlSeconds < 15 || ttlSeconds > 300) {
            throw new IllegalArgumentException(
                    "application.security.impersonation.handoff-ttl-seconds debe estar entre 15 y 300."
            );
        }
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public String issue(String jwt, String tenantSlug, Long restaurantId) {
        if (jwt == null || jwt.isBlank()) {
            throw new IllegalArgumentException("JWT de impersonación requerido.");
        }
        if (tenantSlug == null || tenantSlug.isBlank()) {
            throw new IllegalArgumentException("tenantSlug requerido.");
        }
        if (restaurantId == null) {
            throw new IllegalArgumentException("restaurantId requerido.");
        }
        cleanupExpired();
        String code = generateCode();
        codes.put(
                code,
                new HandoffEntry(
                        jwt,
                        tenantSlug.trim().toLowerCase(Locale.ROOT),
                        restaurantId,
                        Instant.now().plus(ttl)
                )
        );
        return code;
    }

    /**
     * Canjea el código una sola vez. Debe coincidir el subdominio del tenant.
     */
    public RedeemedHandoff redeem(String code, String tenantSlug) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Código de impersonación requerido.");
        }
        if (tenantSlug == null || tenantSlug.isBlank()) {
            throw new IllegalArgumentException("Falta el identificador del restaurante.");
        }
        cleanupExpired();
        HandoffEntry entry = codes.remove(code.trim());
        if (entry == null) {
            throw new IllegalArgumentException(
                    "Código de impersonación inválido o ya utilizado."
            );
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Código de impersonación caducado.");
        }
        String expectedSlug = tenantSlug.trim().toLowerCase(Locale.ROOT);
        if (!entry.tenantSlug().equals(expectedSlug)) {
            // No reinsertar: evita sondeo cruzado de tenants con el mismo code.
            throw new IllegalArgumentException(
                    "Código de impersonación no válido para este restaurante."
            );
        }
        return new RedeemedHandoff(entry.jwt(), entry.restaurantId(), entry.tenantSlug());
    }

    public long ttlSeconds() {
        return ttl.toSeconds();
    }

    private String generateCode() {
        byte[] bytes = new byte[CODE_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, HandoffEntry>> it = codes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, HandoffEntry> entry = it.next();
            if (entry.getValue().expiresAt().isBefore(now)) {
                it.remove();
            }
        }
    }

    private record HandoffEntry(
            String jwt,
            String tenantSlug,
            Long restaurantId,
            Instant expiresAt
    ) {}

    public record RedeemedHandoff(String jwt, Long restaurantId, String tenantSlug) {}
}
