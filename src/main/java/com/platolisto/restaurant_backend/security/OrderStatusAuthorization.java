package com.platolisto.restaurant_backend.security;

import com.platolisto.restaurant_backend.entity.OrderStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Reglas de ciclo de vida del pedido según rol de panel.
 * Sin Redis: evaluación síncrona sobre el SecurityContext.
 */
public final class OrderStatusAuthorization {

    private static final Set<OrderStatus> KITCHEN_PIPELINE = EnumSet.of(
            OrderStatus.PENDING,
            OrderStatus.ACCEPTED,
            OrderStatus.IN_KITCHEN,
            OrderStatus.DELIVERED
    );

    private OrderStatusAuthorization() {}

    public static String requirePanelRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Se requiere autenticación.");
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String role = normalizeRole(authority.getAuthority());
            if (role != null) {
                return role;
            }
        }
        // OWNER por email (UserDetails) a veces viene como ROLE_OWNER
        String name = auth.getName();
        if (name != null && !name.isBlank()) {
            // Sin authorities reconocidas: denegar por defecto
        }
        throw new AccessDeniedException("Rol no autorizado para esta acción.");
    }

    public static void assertCanUpdateStatus(OrderStatus from, OrderStatus to) {
        if (to == null) {
            throw new IllegalArgumentException("El estado del pedido es requerido.");
        }
        if (from == to) {
            return;
        }
        if (from == OrderStatus.CLOSED || from == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException(
                    "No se puede cambiar el estado de un pedido cerrado o cancelado."
            );
        }
        if (!isAllowedTransition(from, to)) {
            throw new IllegalArgumentException(
                    "Transición de estado no permitida: " + from + " → " + to + "."
            );
        }

        String role = requirePanelRole();
        switch (role) {
            case "OWNER", "ADMIN" -> {
                // Puede cualquier transición válida, incluida CANCELLED / CLOSED.
            }
            case "COCINA", "KITCHEN" -> {
                if (to == OrderStatus.CANCELLED || to == OrderStatus.CLOSED) {
                    throw new AccessDeniedException(
                            "Cocina no puede cancelar ni cerrar pedidos por este medio. Usa Cobrar para cerrar."
                    );
                }
                if (!KITCHEN_PIPELINE.contains(to)) {
                    throw new AccessDeniedException("Estado no permitido para cocina.");
                }
            }
            case "MESERO", "CASHIER" -> {
                if (to == OrderStatus.CANCELLED) {
                    throw new AccessDeniedException(
                            "Solo un administrador puede cancelar pedidos."
                    );
                }
                if (to != OrderStatus.CLOSED && to != OrderStatus.DELIVERED) {
                    throw new AccessDeniedException(
                            "El mesero solo puede marcar entregado o cerrar la cuenta."
                    );
                }
            }
            default -> throw new AccessDeniedException("Rol no autorizado para cambiar el estado.");
        }
    }

    public static void assertCanCloseOrder() {
        String role = requirePanelRole();
        switch (role) {
            case "OWNER", "ADMIN", "MESERO", "CASHIER", "COCINA", "KITCHEN" -> {
                // Cobro: mesero, cocina (KDS) y admin.
            }
            default -> throw new AccessDeniedException(
                    "No tienes permiso para cerrar / cobrar esta cuenta."
            );
        }
    }

    public static void assertCanCancelOrder() {
        String role = requirePanelRole();
        if (!"OWNER".equals(role) && !"ADMIN".equals(role)) {
            throw new AccessDeniedException("Solo un administrador puede cancelar pedidos.");
        }
    }

    private static boolean isAllowedTransition(OrderStatus from, OrderStatus to) {
        return switch (from) {
            case PENDING -> to == OrderStatus.ACCEPTED || to == OrderStatus.CANCELLED;
            case ACCEPTED -> to == OrderStatus.IN_KITCHEN || to == OrderStatus.CANCELLED;
            case IN_KITCHEN -> to == OrderStatus.DELIVERED || to == OrderStatus.CANCELLED;
            case DELIVERED -> to == OrderStatus.CLOSED || to == OrderStatus.CANCELLED;
            case CLOSED, CANCELLED -> false;
        };
    }

    private static String normalizeRole(String authority) {
        if (authority == null || authority.isBlank()) {
            return null;
        }
        String raw = authority.trim().toUpperCase(Locale.ROOT);
        if (raw.startsWith("ROLE_")) {
            raw = raw.substring(5);
        }
        return switch (raw) {
            case "OWNER", "ADMIN", "MESERO", "COCINA", "CASHIER", "KITCHEN" -> raw;
            default -> null;
        };
    }
}
