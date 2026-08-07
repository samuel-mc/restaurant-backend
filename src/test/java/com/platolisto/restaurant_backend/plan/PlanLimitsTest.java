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
                SubscriptionPlan.PRO, PaymentStatus.ACTIVE, null)).isTrue();
        assertThat(PlanLimits.canUseProServiceModules(
                SubscriptionPlan.PRO, PaymentStatus.PENDING_PAYMENT, null)).isFalse();
        assertThat(PlanLimits.canUseProServiceModules(
                SubscriptionPlan.BASIC, PaymentStatus.ACTIVE, null)).isFalse();
        assertThat(PlanLimits.canUseProServiceModules(
                SubscriptionPlan.BASIC, PaymentStatus.PENDING_PAYMENT, null)).isFalse();
    }

    @Test
    void basicProductLimitIsTwenty() {
        assertThat(PlanLimits.BASIC_MAX_PRODUCTS).isEqualTo(20);
        assertThat(PlanLimits.BASIC_PRODUCT_LIMIT_UPGRADE_MESSAGE).contains("20");
    }

    @Test
    void createProductLimitedForBasicAndProPending() {
        assertThat(PlanLimits.canCreateProduct(
                SubscriptionPlan.BASIC, PaymentStatus.ACTIVE, null, 19)).isTrue();
        assertThat(PlanLimits.canCreateProduct(
                SubscriptionPlan.BASIC, PaymentStatus.ACTIVE, null, 20)).isFalse();

        assertThat(PlanLimits.canCreateProduct(
                SubscriptionPlan.PRO, PaymentStatus.PENDING_PAYMENT, null, 19)).isTrue();
        assertThat(PlanLimits.canCreateProduct(
                SubscriptionPlan.PRO, PaymentStatus.PENDING_PAYMENT, null, 20)).isFalse();

        assertThat(PlanLimits.canCreateProduct(
                SubscriptionPlan.PRO, PaymentStatus.ACTIVE, null, 100)).isTrue();
        assertThat(PlanLimits.basicImportWouldExceedMessage(15, 10))
                .contains("15")
                .contains("10")
                .contains("Plan Pro")
                .contains("20");
    }

    @Test
    void expiredPeriodRevokesProEntitlement() {
        OffsetDateTime expired = OffsetDateTime.now().minusDays(1);
        OffsetDateTime future = OffsetDateTime.now().plusDays(10);

        assertThat(PlanLimits.isPeriodExpired(null)).isFalse();
        assertThat(PlanLimits.isPeriodExpired(future)).isFalse();
        assertThat(PlanLimits.isPeriodExpired(expired)).isTrue();

        assertThat(PlanLimits.isProEntitled(
                SubscriptionPlan.PRO, PaymentStatus.ACTIVE, expired)).isFalse();
        assertThat(PlanLimits.isProEntitled(
                SubscriptionPlan.PRO, PaymentStatus.ACTIVE, future)).isTrue();
        assertThat(PlanLimits.isProEntitled(
                SubscriptionPlan.PRO, PaymentStatus.ACTIVE, null)).isTrue();

        assertThat(PlanLimits.canCreateProduct(
                SubscriptionPlan.PRO, PaymentStatus.ACTIVE, expired, 20)).isFalse();
        assertThat(PlanLimits.canCreateProduct(
                SubscriptionPlan.PRO, PaymentStatus.ACTIVE, future, 100)).isTrue();
        assertThat(PlanLimits.canUseProServiceModules(
                SubscriptionPlan.PRO, PaymentStatus.ACTIVE, expired)).isFalse();
        assertThat(PlanLimits.canPublishWebsite(
                SubscriptionPlan.PRO, PaymentStatus.ACTIVE, expired)).isFalse();
    }

    @Test
    void publicCatalogUnlimitedOnlyForProActive() {
        List<Product> products = products(PlanLimits.BASIC_MAX_PRODUCTS + 5);
        assertThat(PlanLimits.limitPublicCatalog(
                SubscriptionPlan.PRO, PaymentStatus.ACTIVE, null, products))
                .hasSize(PlanLimits.BASIC_MAX_PRODUCTS + 5);
        assertThat(PlanLimits.limitPublicCatalog(
                SubscriptionPlan.PRO, PaymentStatus.PENDING_PAYMENT, null, products))
                .hasSize(PlanLimits.BASIC_MAX_PRODUCTS);
        assertThat(PlanLimits.limitPublicCatalog(
                SubscriptionPlan.PRO,
                PaymentStatus.ACTIVE,
                OffsetDateTime.now().minusHours(1),
                products))
                .hasSize(PlanLimits.BASIC_MAX_PRODUCTS);
    }

    @Test
    void publicCatalogCappedForBasicOverLimit() {
        List<Product> products = products(PlanLimits.BASIC_MAX_PRODUCTS + 8);
        List<Product> visible = PlanLimits.limitPublicCatalog(
                SubscriptionPlan.BASIC, PaymentStatus.ACTIVE, null, products);
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
