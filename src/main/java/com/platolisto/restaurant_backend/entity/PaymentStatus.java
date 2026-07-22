package com.platolisto.restaurant_backend.entity;

/**
 * Estado de cobro del plan (early access: efectivo / transferencia + cupón).
 */
public enum PaymentStatus {
    /** Plan gratuito o Pro ya activado tras pago/cupón. */
    ACTIVE,
    /** Eligió Pro; falta confirmar pago (cupón o activación manual). */
    PENDING_PAYMENT
}
