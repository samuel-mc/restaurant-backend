package com.platolisto.restaurant_backend.security;

import java.util.Set;

/**
 * Política de PIN de personal: 4 dígitos y rechazo de patrones triviales.
 */
public final class StaffPinPolicy {

    public static final int PIN_LENGTH = 4;
    public static final String PIN_REGEXP = "^\\d{4}$";
    public static final String PIN_MESSAGE = "El PIN debe ser de exactamente 4 dígitos";
    public static final String WEAK_PIN_MESSAGE =
            "Ese PIN es muy fácil de adivinar. Elige 4 dígitos que no sean consecutivos ni repetidos.";

    private static final Set<String> BANNED = Set.of(
            "0000", "1111", "2222", "3333", "4444",
            "5555", "6666", "7777", "8888", "9999",
            "1234", "4321", "0123", "9876",
            "1212", "2121", "1122", "2211",
            "1221", "2112", "1232", "2345",
            "3456", "4567", "5678", "6789",
            "0987", "1357", "2468"
    );

    private StaffPinPolicy() {
    }

    public static void requireStrong(String pin) {
        if (pin == null || !pin.matches(PIN_REGEXP)) {
            throw new IllegalArgumentException(PIN_MESSAGE);
        }
        if (isWeak(pin)) {
            throw new IllegalArgumentException(WEAK_PIN_MESSAGE);
        }
    }

    public static boolean isWeak(String pin) {
        if (pin == null || !pin.matches(PIN_REGEXP)) {
            return false;
        }
        if (pin.chars().distinct().count() <= 2) {
            return true;
        }
        if (BANNED.contains(pin)) {
            return true;
        }
        int[] digits = pin.chars().map(c -> c - '0').toArray();
        boolean ascending = true;
        boolean descending = true;
        for (int i = 1; i < digits.length; i++) {
            if (digits[i] != digits[i - 1] + 1) {
                ascending = false;
            }
            if (digits[i] != digits[i - 1] - 1) {
                descending = false;
            }
        }
        if (ascending || descending) {
            return true;
        }
        // Alternancia tipo 1212
        boolean alternating = true;
        for (int i = 2; i < digits.length; i++) {
            if (digits[i] != digits[i % 2]) {
                alternating = false;
                break;
            }
        }
        if (alternating && digits[0] != digits[1]) {
            return true;
        }
        // Pares gemelos tipo 1122
        boolean twinPairs = true;
        for (int i = 0; i + 1 < digits.length; i += 2) {
            if (digits[i] != digits[i + 1]) {
                twinPairs = false;
                break;
            }
        }
        return twinPairs;
    }
}
