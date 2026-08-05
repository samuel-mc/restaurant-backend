package com.platolisto.restaurant_backend.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffPinPolicyTest {

    @Test
    void rejectsWeakPins() {
        assertTrue(StaffPinPolicy.isWeak("0000"));
        assertTrue(StaffPinPolicy.isWeak("1234"));
        assertTrue(StaffPinPolicy.isWeak("4321"));
        assertTrue(StaffPinPolicy.isWeak("1212"));
        assertTrue(StaffPinPolicy.isWeak("1122"));
        assertTrue(StaffPinPolicy.isWeak("1221"));
    }

    @Test
    void acceptsReasonablePins() {
        assertFalse(StaffPinPolicy.isWeak("5829"));
        assertDoesNotThrow(() -> StaffPinPolicy.requireStrong("5829"));
    }

    @Test
    void requireStrongRejectsShortAndWeak() {
        assertThrows(IllegalArgumentException.class, () -> StaffPinPolicy.requireStrong("123"));
        assertThrows(IllegalArgumentException.class, () -> StaffPinPolicy.requireStrong("1234"));
        assertThrows(IllegalArgumentException.class, () -> StaffPinPolicy.requireStrong("123456"));
    }
}
