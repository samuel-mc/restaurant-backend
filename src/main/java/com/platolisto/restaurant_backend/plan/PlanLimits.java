package com.platolisto.restaurant_backend.plan;

import com.platolisto.restaurant_backend.entity.PaymentStatus;
import com.platolisto.restaurant_backend.entity.SubscriptionPlan;

/**
 * Límites y reglas por plan + estado de pago.
 */
public final class PlanLimits {

    public static final int BASIC_MAX_PRODUCTS = 30;

    private PlanLimits() {
    }

    /**
     * Sitio institucional: solo Pro con pago activo (cupón / confirmación).
     */
    public static boolean canPublishWebsite(SubscriptionPlan plan, PaymentStatus paymentStatus) {
        return plan == SubscriptionPlan.PRO && paymentStatus == PaymentStatus.ACTIVE;
    }

    /**
     * Menú ilimitado mientras el plan sea Pro (aunque el pago esté pendiente),
     * para que puedan cargar carta antes de activar el sitio.
     */
    public static boolean canCreateProduct(SubscriptionPlan plan, long activeProductCount) {
        if (plan == SubscriptionPlan.PRO) {
            return true;
        }
        return activeProductCount < BASIC_MAX_PRODUCTS;
    }

    public static boolean isProEntitled(SubscriptionPlan plan, PaymentStatus paymentStatus) {
        return plan == SubscriptionPlan.PRO && paymentStatus == PaymentStatus.ACTIVE;
    }

    /**
     * Pickup, delivery y reservaciones: solo Plan Pro con pago activo.
     */
    public static boolean canUseProServiceModules(SubscriptionPlan plan, PaymentStatus paymentStatus) {
        return isProEntitled(plan, paymentStatus);
    }

    /** @deprecated usar {@link #canUseProServiceModules} */
    public static boolean canUsePickupAndDelivery(SubscriptionPlan plan, PaymentStatus paymentStatus) {
        return canUseProServiceModules(plan, paymentStatus);
    }
}
