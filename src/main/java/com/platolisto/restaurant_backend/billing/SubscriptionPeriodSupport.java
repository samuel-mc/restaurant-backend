package com.platolisto.restaurant_backend.billing;

import com.platolisto.restaurant_backend.entity.BillingInterval;
import com.platolisto.restaurant_backend.entity.Restaurant;

import java.time.OffsetDateTime;

/**
 * Aplica / extiende el período de suscripción en el tenant.
 * La renovación vive aquí; no en la expiración del cupón.
 */
public final class SubscriptionPeriodSupport {

    private SubscriptionPeriodSupport() {
    }

    /**
     * Extiende (o inicia) el período a partir de {@code grantDurationDays}.
     * Si ya hay un {@code currentPeriodEnd} futuro, suma desde ahí; si no, desde ahora.
     */
    public static void applyGrantDuration(Restaurant restaurant, int grantDurationDays) {
        if (grantDurationDays < 1) {
            throw new IllegalArgumentException("La duración del grant debe ser al menos 1 día.");
        }
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime base = restaurant.getCurrentPeriodEnd() != null
                && restaurant.getCurrentPeriodEnd().isAfter(now)
                ? restaurant.getCurrentPeriodEnd()
                : now;
        if (restaurant.getCurrentPeriodStart() == null) {
            restaurant.setCurrentPeriodStart(now);
        }
        restaurant.setCurrentPeriodEnd(base.plusDays(grantDurationDays));
        if (restaurant.getBillingInterval() == null) {
            restaurant.setBillingInterval(BillingInterval.MONTHLY);
        }
    }

    /**
     * Fija el fin de período (y el inicio si faltaba).
     * {@code periodEnd} null limpia inicio, fin e intervalo.
     */
    public static void setPeriodEnd(Restaurant restaurant, OffsetDateTime periodEnd) {
        if (periodEnd == null) {
            restaurant.setCurrentPeriodStart(null);
            restaurant.setCurrentPeriodEnd(null);
            restaurant.setBillingInterval(null);
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (restaurant.getCurrentPeriodStart() == null) {
            restaurant.setCurrentPeriodStart(now);
        }
        restaurant.setCurrentPeriodEnd(periodEnd);
        if (restaurant.getBillingInterval() == null) {
            restaurant.setBillingInterval(BillingInterval.MONTHLY);
        }
    }
}
