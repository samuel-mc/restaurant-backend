package com.platolisto.restaurant_backend.entity;

/**
 * Resultado del Smart Rating hacia el comensal.
 */
public enum FeedbackOutcome {
    /** 5★ y hay Google Maps → CTA a reseña pública. */
    GOOGLE_REVIEW,
    /** 1–3★ → reclamo privado al gerente. */
    PRIVATE_COMPLAINT,
    /** 4★, o 5★ sin URL de Maps. */
    THANKS
}
