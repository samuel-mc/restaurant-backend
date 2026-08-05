package com.platolisto.restaurant_backend.billing;

import com.platolisto.restaurant_backend.entity.BillingInterval;
import com.platolisto.restaurant_backend.entity.Restaurant;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionPeriodSupportTest {

    @Test
    void applyGrantDurationStartsFromNowWhenNoPeriod() {
        Restaurant restaurant = Restaurant.builder().subdomain("demo").name("Demo").build();
        OffsetDateTime before = OffsetDateTime.now().minusSeconds(1);

        SubscriptionPeriodSupport.applyGrantDuration(restaurant, 30);

        assertThat(restaurant.getCurrentPeriodStart()).isAfter(before);
        assertThat(restaurant.getCurrentPeriodEnd())
                .isEqualTo(restaurant.getCurrentPeriodStart().plusDays(30));
        assertThat(restaurant.getBillingInterval()).isEqualTo(BillingInterval.MONTHLY);
    }

    @Test
    void applyGrantDurationExtendsFromFuturePeriodEnd() {
        OffsetDateTime futureEnd = OffsetDateTime.now().plusDays(10);
        Restaurant restaurant = Restaurant.builder()
                .subdomain("demo")
                .name("Demo")
                .currentPeriodStart(OffsetDateTime.now().minusDays(20))
                .currentPeriodEnd(futureEnd)
                .billingInterval(BillingInterval.MONTHLY)
                .build();

        SubscriptionPeriodSupport.applyGrantDuration(restaurant, 30);

        assertThat(restaurant.getCurrentPeriodEnd()).isEqualTo(futureEnd.plusDays(30));
    }

    @Test
    void setPeriodEndClearsWhenNull() {
        Restaurant restaurant = Restaurant.builder()
                .subdomain("demo")
                .name("Demo")
                .currentPeriodStart(OffsetDateTime.now())
                .currentPeriodEnd(OffsetDateTime.now().plusDays(30))
                .billingInterval(BillingInterval.MONTHLY)
                .build();

        SubscriptionPeriodSupport.setPeriodEnd(restaurant, null);

        assertThat(restaurant.getCurrentPeriodStart()).isNull();
        assertThat(restaurant.getCurrentPeriodEnd()).isNull();
        assertThat(restaurant.getBillingInterval()).isNull();
    }

    @Test
    void applyGrantDurationRejectsNonPositive() {
        Restaurant restaurant = Restaurant.builder().subdomain("demo").name("Demo").build();
        assertThatThrownBy(() -> SubscriptionPeriodSupport.applyGrantDuration(restaurant, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
