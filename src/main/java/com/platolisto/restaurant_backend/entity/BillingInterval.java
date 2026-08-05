package com.platolisto.restaurant_backend.entity;

/**
 * Intervalo comercial de la suscripción del tenant.
 * Independiente de la expiración del cupón ({@code Coupon.expiresAt}).
 */
public enum BillingInterval {
    MONTHLY,
    YEARLY
}
