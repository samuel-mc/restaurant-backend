package com.platolisto.restaurant_backend.entity;

public enum UserRole {
    OWNER,
    ADMIN,
    CASHIER,
    KITCHEN,
    /** Operador global de la plataforma (sin tenant). */
    SUPER_ADMIN
}
