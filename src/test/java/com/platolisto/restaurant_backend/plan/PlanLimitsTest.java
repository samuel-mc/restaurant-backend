package com.platolisto.restaurant_backend.plan;

import com.platolisto.restaurant_backend.entity.Category;
import com.platolisto.restaurant_backend.entity.PaymentStatus;
import com.platolisto.restaurant_backend.entity.Product;
import com.platolisto.restaurant_backend.entity.SubscriptionPlan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanLimitsTest {

    @Test
    void pickupAndDeliveryOnlyForProWithActivePayment() {
        assertThat(PlanLimits.canUseProServiceModules(
                SubscriptionPlan.PRO, PaymentStatus.ACTIVE)).isTrue();
        assertThat(PlanLimits.canUseProServiceModules(
                SubscriptionPlan.PRO, PaymentStatus.PENDING_PAYMENT)).isFalse();
        assertThat(PlanLimits.canUseProServiceModules(
                SubscriptionPlan.BASIC, PaymentStatus.ACTIVE)).isFalse();
        assertThat(PlanLimits.canUseProServiceModules(
                SubscriptionPlan.BASIC, PaymentStatus.PENDING_PAYMENT)).isFalse();
    }

    @Test
    void basicProductLimitMessages() {
        assertThat(PlanLimits.canCreateProduct(SubscriptionPlan.BASIC, 29)).isTrue();
        assertThat(PlanLimits.canCreateProduct(SubscriptionPlan.BASIC, 30)).isFalse();
        assertThat(PlanLimits.canCreateProduct(SubscriptionPlan.PRO, 100)).isTrue();
        assertThat(PlanLimits.basicImportWouldExceedMessage(25, 10))
                .contains("25")
                .contains("10")
                .contains("Plan Pro");
    }

    @Test
    void publicCatalogUnlimitedForPro() {
        List<Product> products = products(PlanLimits.BASIC_MAX_PRODUCTS + 5);
        assertThat(PlanLimits.limitPublicCatalog(SubscriptionPlan.PRO, products))
                .hasSize(PlanLimits.BASIC_MAX_PRODUCTS + 5);
    }

    @Test
    void publicCatalogCappedForBasicOverLimit() {
        List<Product> products = products(PlanLimits.BASIC_MAX_PRODUCTS + 8);
        List<Product> visible = PlanLimits.limitPublicCatalog(SubscriptionPlan.BASIC, products);
        assertThat(visible).hasSize(PlanLimits.BASIC_MAX_PRODUCTS);
        assertThat(visible.getFirst().getName()).isEqualTo("P-0");
        assertThat(visible.getLast().getName()).isEqualTo("P-" + (PlanLimits.BASIC_MAX_PRODUCTS - 1));
    }

    private static List<Product> products(int count) {
        Category category = Category.builder().name("Cat").displayOrder(1).build();
        OffsetDateTime base = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        List<Product> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(Product.builder()
                    .id((long) i + 1)
                    .name("P-" + i)
                    .price(BigDecimal.ONE)
                    .category(category)
                    .createdAt(base.plusMinutes(i))
                    .build());
        }
        return list;
    }
}
