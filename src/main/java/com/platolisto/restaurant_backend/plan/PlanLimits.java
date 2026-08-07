package com.platolisto.restaurant_backend.plan;

import com.platolisto.restaurant_backend.entity.PaymentStatus;
import com.platolisto.restaurant_backend.entity.Product;
import com.platolisto.restaurant_backend.entity.SubscriptionPlan;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Límites y reglas por plan + estado de pago + fin de período.
 */
public final class PlanLimits {

    public static final int BASIC_MAX_PRODUCTS = 20;

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
     * Catálogo público sin límite solo con Pro vigente.
     * Básico, Pro pendiente o período vencido: como máximo {@link #BASIC_MAX_PRODUCTS}.
     */
    public static List<Product> limitPublicCatalog(
            SubscriptionPlan plan,
            PaymentStatus paymentStatus,
            OffsetDateTime currentPeriodEnd,
            List<Product> availableProducts
    ) {
        if (isProEntitled(plan, paymentStatus, currentPeriodEnd)
                || availableProducts.size() <= BASIC_MAX_PRODUCTS) {
            return availableProducts;
        }
        return availableProducts.stream()
                .sorted(PUBLIC_CATALOG_ORDER)
                .limit(BASIC_MAX_PRODUCTS)
                .collect(Collectors.toList());
    }

    /**
     * Sitio institucional: solo Pro con pago activo y período vigente.
     */
    public static boolean canPublishWebsite(
            SubscriptionPlan plan,
            PaymentStatus paymentStatus,
            OffsetDateTime currentPeriodEnd
    ) {
        return isProEntitled(plan, paymentStatus, currentPeriodEnd);
    }

    /**
     * Menú ilimitado solo con Pro vigente. Pro sin cupón/pago o con
     * {@code currentPeriodEnd} vencido queda bajo el tope del Plan Básico.
     */
    public static boolean canCreateProduct(
            SubscriptionPlan plan,
            PaymentStatus paymentStatus,
            OffsetDateTime currentPeriodEnd,
            long activeProductCount
    ) {
        if (isProEntitled(plan, paymentStatus, currentPeriodEnd)) {
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

    /**
     * Pro vigente: plan PRO, pago ACTIVE y período no vencido.
     * {@code currentPeriodEnd == null} = sin fecha de corte (vigente mientras ACTIVE).
     */
    public static boolean isProEntitled(
            SubscriptionPlan plan,
            PaymentStatus paymentStatus,
            OffsetDateTime currentPeriodEnd
    ) {
        if (plan != SubscriptionPlan.PRO || paymentStatus != PaymentStatus.ACTIVE) {
            return false;
        }
        return !isPeriodExpired(currentPeriodEnd);
    }

    /**
     * {@code null} = sin expiración configurada. Fecha en el pasado = vencido.
     */
    public static boolean isPeriodExpired(OffsetDateTime currentPeriodEnd) {
        return currentPeriodEnd != null && !currentPeriodEnd.isAfter(OffsetDateTime.now());
    }

    /**
     * Pickup, delivery y reservaciones: solo Plan Pro vigente.
     */
    public static boolean canUseProServiceModules(
            SubscriptionPlan plan,
            PaymentStatus paymentStatus,
            OffsetDateTime currentPeriodEnd
    ) {
        return isProEntitled(plan, paymentStatus, currentPeriodEnd);
    }

    /** @deprecated usar {@link #canUseProServiceModules} */
    public static boolean canUsePickupAndDelivery(
            SubscriptionPlan plan,
            PaymentStatus paymentStatus,
            OffsetDateTime currentPeriodEnd
    ) {
        return canUseProServiceModules(plan, paymentStatus, currentPeriodEnd);
    }
}
