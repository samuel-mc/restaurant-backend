package com.platolisto.restaurant_backend.plan;

import com.platolisto.restaurant_backend.entity.PaymentStatus;
import com.platolisto.restaurant_backend.entity.SubscriptionPlan;
import org.junit.jupiter.api.Test;

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
}
