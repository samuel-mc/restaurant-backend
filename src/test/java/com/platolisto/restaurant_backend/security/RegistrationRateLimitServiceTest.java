package com.platolisto.restaurant_backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegistrationRateLimitServiceTest {

    private RegistrationRateLimitService service;

    @BeforeEach
    void setUp() {
        service = new RegistrationRateLimitService(3, 60);
    }

    @Test
    void blocksIpAfterMaxAttempts() {
        String ip = "register:ip:203.0.113.10";
        for (int i = 0; i < 3; i++) {
            assertDoesNotThrow(() -> service.assertAllowed(ip));
            service.record(ip);
        }
        assertThrows(RateLimitExceededException.class, () -> service.assertAllowed(ip));
    }

    @Test
    void blocksEmailKeyIndependently() {
        String email = "register:email:spam@example.com";
        for (int i = 0; i < 3; i++) {
            service.record(email);
        }
        assertThrows(
                RateLimitExceededException.class,
                () -> service.assertAllowed("register:ip:1.2.3.4", email)
        );
    }
}
