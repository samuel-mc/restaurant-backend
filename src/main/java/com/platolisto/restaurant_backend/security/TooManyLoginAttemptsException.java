package com.platolisto.restaurant_backend.security;

/**
 * Demasiados intentos de login (PIN / credenciales).
 */
public class TooManyLoginAttemptsException extends RateLimitExceededException {

    public TooManyLoginAttemptsException(String message) {
        super(message);
    }
}
