package com.platolisto.restaurant_backend.security;

/**
 * Límite de tasa excedido (registro, login, etc.).
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
