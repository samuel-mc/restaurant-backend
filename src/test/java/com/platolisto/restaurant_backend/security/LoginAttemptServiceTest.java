package com.platolisto.restaurant_backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginAttemptServiceTest {

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService();
    }

    @Test
    void locksAccountKeyAfterMaxFailuresRegardlessOfIp() {
        String account = "pin:staff:mario:abc";
        for (int i = 0; i < 5; i++) {
            service.recordFailure(account, "pin:ip:1.1.1." + i);
        }
        assertThrows(TooManyLoginAttemptsException.class,
                () -> service.assertNotLocked(account, "pin:ip:9.9.9.9"));
    }

    @Test
    void successClearsLock() {
        String account = "auth:email:a@b.com";
        String ip = "auth:ip:10.0.0.1";
        for (int i = 0; i < 5; i++) {
            service.recordFailure(account, ip);
        }
        service.recordSuccess(account, ip);
        assertDoesNotThrow(() -> service.assertNotLocked(account, ip));
    }
}
