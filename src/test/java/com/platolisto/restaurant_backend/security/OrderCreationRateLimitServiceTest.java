package com.platolisto.restaurant_backend.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderCreationRateLimitServiceTest {

    @Test
    void blocksAfterMaxAttempts() {
        OrderCreationRateLimitService service = new OrderCreationRateLimitService(2, 15);
        String key = "orders:ip:127.0.0.1";
        service.record(key);
        service.record(key);
        assertThrows(RateLimitExceededException.class, () -> service.assertAllowed(key));
    }
}
