package com.platolisto.restaurant_backend.security;

import java.security.Principal;

/**
 * Principal STOMP con claims del JWT (tenant + rol) para autorizar suscripciones.
 */
public record WsAuthenticatedPrincipal(
        String name,
        Long restaurantId,
        String role
) implements Principal {

    @Override
    public String getName() {
        return name;
    }

    public boolean canAccessKitchenTopics() {
        if (role == null || role.isBlank()) {
            return false;
        }
        String normalized = role.trim().toUpperCase().replace("ROLE_", "");
        return switch (normalized) {
            case "OWNER", "ADMIN", "MESERO", "COCINA", "CASHIER", "KITCHEN" -> true;
            default -> false;
        };
    }
}
