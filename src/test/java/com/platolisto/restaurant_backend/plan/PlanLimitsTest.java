package com.platolisto.restaurant_backend.plan;

import com.platolisto.restaurant_backend.entity.PaymentStatus;
import com.platolisto.restaurant_backend.entity.SubscriptionPlan;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlanLimitsTest {

    @Test
    void pickupAndDeliveryOnlyForProWithActivePayment() {
        assertThat(PlanLimits.canUsePickupAndDelivery(
                SubscriptionPlan.PRO, PaymentStatus.ACTIVE)).isTrue();
        assertThat(PlanLimits.canUsePickupAndDelivery(
                SubscriptionPlan.PRO, PaymentStatus.PENDING_PAYMENT)).isFalse();
        assertThat(PlanLimits.canUsePickupAndDelivery(
                SubscriptionPlan.BASIC, PaymentStatus.ACTIVE)).isFalse();
        assertThat(PlanLimits.canUsePickupAndDelivery(
                SubscriptionPlan.BASIC, PaymentStatus.PENDING_PAYMENT)).isFalse();
    }
}
