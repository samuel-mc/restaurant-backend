package com.platolisto.restaurant_backend.security;

import com.platolisto.restaurant_backend.entity.OrderStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderStatusAuthorizationTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void cocinaCannotCancel() {
        setRole("ROLE_COCINA");
        assertThrows(
                AccessDeniedException.class,
                () -> OrderStatusAuthorization.assertCanUpdateStatus(
                        OrderStatus.PENDING, OrderStatus.CANCELLED
                )
        );
    }

    @Test
    void meseroCannotCancel() {
        setRole("ROLE_MESERO");
        assertThrows(
                AccessDeniedException.class,
                () -> OrderStatusAuthorization.assertCanUpdateStatus(
                        OrderStatus.PENDING, OrderStatus.CANCELLED
                )
        );
    }

    @Test
    void adminCanCancel() {
        setRole("ROLE_ADMIN");
        assertDoesNotThrow(() -> OrderStatusAuthorization.assertCanUpdateStatus(
                OrderStatus.PENDING, OrderStatus.CANCELLED
        ));
    }

    @Test
    void cocinaCanAdvancePipeline() {
        setRole("ROLE_COCINA");
        assertDoesNotThrow(() -> OrderStatusAuthorization.assertCanUpdateStatus(
                OrderStatus.PENDING, OrderStatus.ACCEPTED
        ));
        assertDoesNotThrow(() -> OrderStatusAuthorization.assertCanUpdateStatus(
                OrderStatus.ACCEPTED, OrderStatus.IN_KITCHEN
        ));
        assertDoesNotThrow(() -> OrderStatusAuthorization.assertCanUpdateStatus(
                OrderStatus.IN_KITCHEN, OrderStatus.DELIVERED
        ));
    }

    @Test
    void meseroCanCloseViaStatus() {
        setRole("ROLE_MESERO");
        assertDoesNotThrow(() -> OrderStatusAuthorization.assertCanUpdateStatus(
                OrderStatus.DELIVERED, OrderStatus.CLOSED
        ));
    }

    @Test
    void rejectsSkippingTransitions() {
        setRole("ROLE_ADMIN");
        assertThrows(
                IllegalArgumentException.class,
                () -> OrderStatusAuthorization.assertCanUpdateStatus(
                        OrderStatus.PENDING, OrderStatus.DELIVERED
                )
        );
    }

    @Test
    void meseroAndCocinaCanCloseOrder() {
        setRole("ROLE_MESERO");
        assertDoesNotThrow(OrderStatusAuthorization::assertCanCloseOrder);
        setRole("ROLE_COCINA");
        assertDoesNotThrow(OrderStatusAuthorization::assertCanCloseOrder);
    }

    private static void setRole(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "user",
                        "n/a",
                        List.of(new SimpleGrantedAuthority(role))
                )
        );
    }
}
