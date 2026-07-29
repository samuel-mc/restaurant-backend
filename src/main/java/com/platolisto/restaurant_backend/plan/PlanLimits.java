package com.platolisto.restaurant_backend.plan;

import com.platolisto.restaurant_backend.entity.PaymentStatus;
import com.platolisto.restaurant_backend.entity.Product;
import com.platolisto.restaurant_backend.entity.SubscriptionPlan;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Límites y reglas por plan + estado de pago.
 */
public final class PlanLimits {

    public static final int BASIC_MAX_PRODUCTS = 30;

    public static final String BASIC_PRODUCT_LIMIT_UPGRADE_MESSAGE =
            "El Plan Básico permite hasta " + BASIC_MAX_PRODUCTS
                    + " platillos. Actualiza al Plan Pro para menú ilimitado.";

    private static final Comparator<Product> PUBLIC_CATALOG_ORDER =
            Comparator.comparing((Product p) -> p.getCategory().getDisplayOrder())
                    .thenComparing(Product::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Product::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private PlanLimits() {
    }

    /**
     * Catálogo público en Básico: como máximo {@link #BASIC_MAX_PRODUCTS} platillos
     * (orden estable por categoría, fecha de alta e id). Pro no limita.
     */
    public static List<Product> limitPublicCatalog(SubscriptionPlan plan, List<Product> availableProducts) {
        if (plan == SubscriptionPlan.PRO || availableProducts.size() <= BASIC_MAX_PRODUCTS) {
            return availableProducts;
        }
        return availableProducts.stream()
                .sorted(PUBLIC_CATALOG_ORDER)
                .limit(BASIC_MAX_PRODUCTS)
                .collect(Collectors.toList());
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

    public static String basicImportWouldExceedMessage(long currentCount, int fileRowCount) {
        return "El Plan Básico permite hasta " + BASIC_MAX_PRODUCTS
                + " platillos. Ya tienes " + currentCount
                + " y el archivo trae " + fileRowCount
                + ". Actualiza al Plan Pro para menú ilimitado.";
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
