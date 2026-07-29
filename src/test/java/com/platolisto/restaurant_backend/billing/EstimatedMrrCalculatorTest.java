package com.platolisto.restaurant_backend.billing;

import com.platolisto.restaurant_backend.entity.PaymentStatus;
import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.entity.SubscriptionPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EstimatedMrrCalculatorTest {

    private final EstimatedMrrCalculator calculator = new EstimatedMrrCalculator(1000L);

    @Test
    void zeroWhenNoProActive() {
        List<Restaurant> restaurants = List.of(
                restaurant("basic", SubscriptionPlan.BASIC, true, PaymentStatus.ACTIVE),
                restaurant("pro-off", SubscriptionPlan.PRO, false, PaymentStatus.ACTIVE)
        );

        EstimatedMrrCalculator.EstimatedMrr mrr = calculator.estimate(restaurants);

        assertThat(mrr.amount()).isZero();
        assertThat(mrr.proActiveCount()).isZero();
        assertThat(mrr.unitPriceMxn()).isEqualTo(1000L);
    }

    @Test
    void multipliesProActiveByConfiguredListPrice() {
        EstimatedMrrCalculator custom = new EstimatedMrrCalculator(1000L);
        List<Restaurant> restaurants = List.of(
                restaurant("a", SubscriptionPlan.PRO, true, PaymentStatus.ACTIVE),
                restaurant("b", SubscriptionPlan.PRO, true, PaymentStatus.ACTIVE),
                restaurant("c", SubscriptionPlan.PRO, true, PaymentStatus.ACTIVE)
        );

        EstimatedMrrCalculator.EstimatedMrr mrr = custom.estimate(restaurants);

        assertThat(mrr.proActiveCount()).isEqualTo(3L);
        assertThat(mrr.amount()).isEqualTo(3000L);
        assertThat(mrr.unitPriceMxn()).isEqualTo(1000L);
    }

    @Test
    void excludesSuspendedProAndBasic() {
        List<Restaurant> restaurants = List.of(
                restaurant("pro-on", SubscriptionPlan.PRO, true, PaymentStatus.ACTIVE),
                restaurant("pro-off", SubscriptionPlan.PRO, false, PaymentStatus.ACTIVE),
                restaurant("basic", SubscriptionPlan.BASIC, true, PaymentStatus.ACTIVE)
        );

        EstimatedMrrCalculator.EstimatedMrr mrr = calculator.estimate(restaurants);

        assertThat(mrr.proActiveCount()).isEqualTo(1L);
        assertThat(mrr.amount()).isEqualTo(1000L);
    }

    @Test
    void includesPendingPaymentProActive() {
        List<Restaurant> restaurants = List.of(
                restaurant("pending", SubscriptionPlan.PRO, true, PaymentStatus.PENDING_PAYMENT)
        );

        EstimatedMrrCalculator.EstimatedMrr mrr = calculator.estimate(restaurants);

        assertThat(mrr.proActiveCount()).isEqualTo(1L);
        assertThat(mrr.amount()).isEqualTo(1000L);
    }

    @Test
    void exposesStableMetadata() {
        EstimatedMrrCalculator.EstimatedMrr mrr = calculator.estimate(List.of());

        assertThat(mrr.currency()).isEqualTo(EstimatedMrrCalculator.CURRENCY);
        assertThat(mrr.period()).isEqualTo(EstimatedMrrCalculator.PERIOD);
        assertThat(mrr.method()).isEqualTo(EstimatedMrrCalculator.METHOD);
        assertThat(mrr.labelEs()).isEqualTo(EstimatedMrrCalculator.LABEL_ES);
        assertThat(mrr.disclaimerEs()).isEqualTo(EstimatedMrrCalculator.DISCLAIMER_ES);
        assertThat(mrr.asOf()).isNotNull();
        assertThat(mrr.asOf().getOffset().getTotalSeconds()).isZero();
    }

    private static Restaurant restaurant(
            String subdomain,
            SubscriptionPlan plan,
            boolean active,
            PaymentStatus paymentStatus
    ) {
        return Restaurant.builder()
                .name(subdomain)
                .subdomain(subdomain)
                .plan(plan)
                .paymentStatus(paymentStatus)
                .isActive(active)
                .build();
    }
}
