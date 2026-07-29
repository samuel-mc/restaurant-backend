package com.platolisto.restaurant_backend.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Revocación de JWT por {@code jti} en memoria (una JVM; sin Redis).
 * Las entradas se purgan tras la expiración natural del token.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JwtDenylistService {

    private final JwtService jwtService;

    /** jti → instante en que el token habría expirado (ya no hace falta retenerlo). */
    private final ConcurrentHashMap<String, Instant> revokedUntil = new ConcurrentHashMap<>();

    /**
     * Marca el token como inválido hasta su {@code exp}.
     * Tokens sin {@code jti} (legado) no se pueden revocar.
     */
    public void revoke(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            String jti = jwtService.extractJti(token);
            if (jti == null || jti.isBlank()) {
                log.debug("Logout sin jti: no se puede revocar el token.");
                return;
            }
            Date exp = jwtService.extractExpirationPublic(token);
            Instant until = exp != null ? exp.toInstant() : Instant.now().plusSeconds(60);
            if (until.isBefore(Instant.now())) {
                return;
            }
            cleanupExpired();
            revokedUntil.put(jti, until);
            log.info("JWT revocado (jti={})", jti);
        } catch (Exception e) {
            log.debug("No se pudo revocar JWT: {}", e.getMessage());
        }
    }

    public boolean isRevoked(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            String jti = jwtService.extractJti(token);
            if (jti == null || jti.isBlank()) {
                return false;
            }
            return isJtiRevoked(jti);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isJtiRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        cleanupExpired();
        Instant until = revokedUntil.get(jti);
        if (until == null) {
            return false;
        }
        if (until.isBefore(Instant.now())) {
            revokedUntil.remove(jti, until);
            return false;
        }
        return true;
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, Instant>> it = revokedUntil.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Instant> entry = it.next();
            if (entry.getValue().isBefore(now)) {
                it.remove();
            }
        }
    }
}
