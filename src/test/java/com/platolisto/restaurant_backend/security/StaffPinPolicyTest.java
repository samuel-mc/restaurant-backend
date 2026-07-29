package com.platolisto.restaurant_backend.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffPinPolicyTest {

    @Test
    void rejectsWeakPins() {
        assertTrue(StaffPinPolicy.isWeak("000000"));
        assertTrue(StaffPinPolicy.isWeak("123456"));
        assertTrue(StaffPinPolicy.isWeak("654321"));
        assertTrue(StaffPinPolicy.isWeak("121212"));
    }

    @Test
    void acceptsReasonablePins() {
        assertFalse(StaffPinPolicy.isWeak("582917"));
        assertDoesNotThrow(() -> StaffPinPolicy.requireStrong("582917"));
    }

    @Test
    void requireStrongRejectsShortAndWeak() {
        assertThrows(IllegalArgumentException.class, () -> StaffPinPolicy.requireStrong("1234"));
        assertThrows(IllegalArgumentException.class, () -> StaffPinPolicy.requireStrong("123456"));
    }
}
