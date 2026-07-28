package com.platolisto.restaurant_backend.entity;

/**
 * Roles operativos del equipo del restaurante (login por PIN).
 * Independiente de {@link UserRole} (email/password del dueño y superadmin).
 */
public enum StaffRole {
    ADMIN,
    MESERO,
    COCINA
}
