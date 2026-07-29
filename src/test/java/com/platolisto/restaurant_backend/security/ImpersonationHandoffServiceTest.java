package com.platolisto.restaurant_backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImpersonationHandoffServiceTest {

    private ImpersonationHandoffService service;

    @BeforeEach
    void setUp() {
        service = new ImpersonationHandoffService(90);
    }

    @Test
    void issueAndRedeemOnce() {
        String code = service.issue("jwt.a.b", "mario", 7L);
        assertTrue(code.length() >= 32);

        ImpersonationHandoffService.RedeemedHandoff redeemed =
                service.redeem(code, "mario");
        assertEquals("jwt.a.b", redeemed.jwt());
        assertEquals(7L, redeemed.restaurantId());

        assertThrows(IllegalArgumentException.class, () -> service.redeem(code, "mario"));
    }

    @Test
    void rejectsWrongTenant() {
        String code = service.issue("jwt.a.b", "mario", 7L);
        assertThrows(IllegalArgumentException.class, () -> service.redeem(code, "otros"));
        // Código consumido al fallar por tenant: no reutilizable
        assertThrows(IllegalArgumentException.class, () -> service.redeem(code, "mario"));
    }

    @Test
    void codesAreUnique() {
        String a = service.issue("jwt.1", "a", 1L);
        String b = service.issue("jwt.2", "b", 2L);
        assertNotEquals(a, b);
    }
}
