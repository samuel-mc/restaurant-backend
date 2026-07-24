package com.platolisto.restaurant_backend.entity;

public enum OrderStatus {
    PENDING,
    ACCEPTED,
    IN_KITCHEN,
    DELIVERED,
    /** Cuenta cobrada / mesa liberada (cierre manual desde admin). */
    CLOSED,
    CANCELLED
}
