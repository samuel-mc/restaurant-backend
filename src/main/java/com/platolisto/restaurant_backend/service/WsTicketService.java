package com.platolisto.restaurant_backend.service;

import com.platolisto.restaurant_backend.dto.WsTicketResponse;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.security.JwtService;
import com.platolisto.restaurant_backend.security.StaffUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Emite tickets STOMP de corta vida y los invalida tras el primer CONNECT (jti).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WsTicketService {

    private final JwtService jwtService;

    /** jti → instante en que deja de importar (expiración del ticket). */
    private final ConcurrentHashMap<String, Instant> consumedJtis = new ConcurrentHashMap<>();

    public WsTicketResponse issueForCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("Se requiere autenticación.");
        }

        Object principal = authentication.getPrincipal();
        String subject;
        Long restaurantId;
        String role;

        if (principal instanceof StaffUserDetails staff) {
            subject = staff.getUsername();
            restaurantId = staff.getRestaurantId();
            role = staff.getRole().name();
        } else if (principal instanceof UserDetails userDetails) {
            subject = userDetails.getUsername();
            restaurantId = TenantContext.getCurrentTenant();
            role = extractRole(authentication);
        } else {
            throw new IllegalArgumentException("Principal no soportado para ticket WebSocket.");
        }

        if (restaurantId == null) {
            throw new IllegalArgumentException("No se pudo determinar el restaurante para el ticket.");
        }
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("No se pudo determinar el rol para el ticket.");
        }
        if ("SUPER_ADMIN".equalsIgnoreCase(role.replace("ROLE_", ""))) {
            throw new IllegalArgumentException("SuperAdmin no usa el canal de cocina por tenant.");
        }

        String ticket = jwtService.generateWsTicket(subject, restaurantId, role.replace("ROLE_", ""));
        long expiresIn = Math.max(1L, jwtService.getWsTicketExpirationMs() / 1000L);
        return WsTicketResponse.builder()
                .ticket(ticket)
                .expiresInSeconds(expiresIn)
                .build();
    }

    /**
     * Valida que el token sea un ticket WS vigente y lo consume (un solo CONNECT).
     */
    public void authenticateAndConsume(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Ticket WebSocket requerido.");
        }
        if (!jwtService.isWsTicket(token) || !jwtService.isTokenSignatureValid(token)) {
            throw new IllegalArgumentException("Ticket WebSocket inválido o expirado.");
        }
        String jti = jwtService.extractJti(token);
        if (jti == null || jti.isBlank()) {
            throw new IllegalArgumentException("Ticket WebSocket sin identificador.");
        }
        cleanupExpired();
        Instant expiresAt = Instant.now().plusMillis(jwtService.getWsTicketExpirationMs());
        Instant previous = consumedJtis.putIfAbsent(jti, expiresAt);
        if (previous != null) {
            throw new IllegalArgumentException("Ticket WebSocket ya utilizado.");
        }
    }

    private static String extractRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                .findFirst()
                .orElse(null);
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, Instant>> it = consumedJtis.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Instant> entry = it.next();
            if (entry.getValue().isBefore(now)) {
                it.remove();
            }
        }
    }
}
