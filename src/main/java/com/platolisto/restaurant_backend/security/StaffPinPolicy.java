package com.platolisto.restaurant_backend.security;

/**
 * Política de PIN de personal: longitud fija y rechazo de patrones triviales.
 */
public final class StaffPinPolicy {

    public static final int PIN_LENGTH = 6;
    public static final String PIN_REGEXP = "^\\d{6}$";
    public static final String PIN_MESSAGE = "El PIN debe ser de exactamente 6 dígitos";
    public static final String WEAK_PIN_MESSAGE =
            "Ese PIN es muy fácil de adivinar. Elige 6 dígitos que no sean consecutivos ni repetidos.";

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
        if (pin.chars().distinct().count() == 1) {
            return true;
        }
        if (pin.equals("123456")
                || pin.equals("654321")
                || pin.equals("012345")
                || pin.equals("987654")
                || pin.equals("112233")
                || pin.equals("121212")
                || pin.equals("123123")
                || pin.equals("111222")) {
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
        return ascending || descending;
    }
}
