package com.platolisto.restaurant_backend.entity;

/**
 * Tipo de llamada desde el menú QR de mesa.
 */
public enum TableCallType {
    /** Comensal solicita atención del mesero. */
    WAITER,
    /** Comensal pide la cuenta (opcionalmente indica forma de pago). */
    BILL
}
