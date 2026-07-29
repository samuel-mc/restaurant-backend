package com.platolisto.restaurant_backend.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resuelve la IP del cliente. Por defecto ignora {@code X-Forwarded-For} /
 * {@code X-Real-IP} (spoofables). Solo los usa si
 * {@code application.security.trust-forwarded-headers=true} (proxy de confianza).
 */
@Component
public class ClientIpResolver {

    private final boolean trustForwardedHeaders;

    public ClientIpResolver(
            @Value("${application.security.trust-forwarded-headers:false}") boolean trustForwardedHeaders
    ) {
        this.trustForwardedHeaders = trustForwardedHeaders;
    }

    public String resolve(HttpServletRequest request) {
        if (trustForwardedHeaders) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        String remote = request.getRemoteAddr();
        return remote != null && !remote.isBlank() ? remote : "unknown";
    }
}
