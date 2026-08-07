package com.platolisto.restaurant_backend.service;

import com.platolisto.restaurant_backend.billing.EstimatedMrrCalculator;
import com.platolisto.restaurant_backend.dto.superadmin.SuperAdminTenantSubscriptionRequest;
import com.platolisto.restaurant_backend.entity.PaymentStatus;
import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.entity.SubscriptionPlan;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import com.platolisto.restaurant_backend.repository.UserRepository;
import com.platolisto.restaurant_backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAdminSubscriptionServiceTest {

    @Mock
    private RestaurantRepository restaurantRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private EstimatedMrrCalculator estimatedMrrCalculator;

    @InjectMocks
    private SuperAdminService superAdminService;

    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        restaurant = Restaurant.builder()
                .id(10L)
                .name("Demo")
                .subdomain("demo")
                .plan(SubscriptionPlan.BASIC)
                .paymentStatus(PaymentStatus.ACTIVE)
                .websitePublished(false)
                .isActive(true)
                .build();
    }

    @Test
    void upgradeToProActivePublishesWebsite() {
        when(restaurantRepository.findById(10L)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = SuperAdminTenantSubscriptionRequest.builder()
                .plan(SubscriptionPlan.PRO)
                .paymentStatus(PaymentStatus.ACTIVE)
                .build();

        var response = superAdminService.updateTenantSubscription(10L, request, "sa@platolisto.com");

        assertThat(response.getPlan()).isEqualTo("PRO");
        assertThat(response.getPaymentStatus()).isEqualTo("ACTIVE");
        assertThat(response.isWebsitePublished()).isTrue();

        ArgumentCaptor<Restaurant> captor = ArgumentCaptor.forClass(Restaurant.class);
        verify(restaurantRepository).save(captor.capture());
        assertThat(captor.getValue().isWebsitePublished()).isTrue();
    }

    @Test
    void downgradeToBasicUnpublishesWebsiteAndClearsProModules() {
        restaurant.setPlan(SubscriptionPlan.PRO);
        restaurant.setPaymentStatus(PaymentStatus.ACTIVE);
        restaurant.setWebsitePublished(true);
        restaurant.setHasPickup(true);
        restaurant.setHasDelivery(true);
        restaurant.setHasReservations(true);
        when(restaurantRepository.findById(10L)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = SuperAdminTenantSubscriptionRequest.builder()
                .plan(SubscriptionPlan.BASIC)
                .paymentStatus(PaymentStatus.ACTIVE)
                .build();

        var response = superAdminService.updateTenantSubscription(10L, request, "sa@platolisto.com");

        assertThat(response.getPlan()).isEqualTo("BASIC");
        assertThat(response.isWebsitePublished()).isFalse();
        ArgumentCaptor<Restaurant> captor = ArgumentCaptor.forClass(Restaurant.class);
        verify(restaurantRepository).save(captor.capture());
        assertThat(captor.getValue().isHasPickup()).isFalse();
        assertThat(captor.getValue().isHasDelivery()).isFalse();
        assertThat(captor.getValue().isHasReservations()).isFalse();
    }

    @Test
    void proWithPendingPaymentUnpublishesWebsite() {
        restaurant.setPlan(SubscriptionPlan.PRO);
        restaurant.setPaymentStatus(PaymentStatus.ACTIVE);
        restaurant.setWebsitePublished(true);
        when(restaurantRepository.findById(10L)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = SuperAdminTenantSubscriptionRequest.builder()
                .plan(SubscriptionPlan.PRO)
                .paymentStatus(PaymentStatus.PENDING_PAYMENT)
                .build();

        var response = superAdminService.updateTenantSubscription(10L, request, "sa@platolisto.com");

        assertThat(response.getPaymentStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(response.isWebsitePublished()).isFalse();
    }

    @Test
    void setsCurrentPeriodEndWhenProvided() {
        when(restaurantRepository.findById(10L)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(inv -> inv.getArgument(0));

        String periodEnd = OffsetDateTime.now().plusDays(45).toString();
        var request = SuperAdminTenantSubscriptionRequest.builder()
                .plan(SubscriptionPlan.PRO)
                .paymentStatus(PaymentStatus.ACTIVE)
                .currentPeriodEnd(periodEnd)
                .build();

        var response = superAdminService.updateTenantSubscription(10L, request, "sa@platolisto.com");

        assertThat(response.getCurrentPeriodEnd()).isNotNull();
        assertThat(response.getBillingInterval()).isEqualTo("MONTHLY");
        ArgumentCaptor<Restaurant> captor = ArgumentCaptor.forClass(Restaurant.class);
        verify(restaurantRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentPeriodEnd()).isNotNull();
        assertThat(captor.getValue().getCurrentPeriodStart()).isNotNull();
    }

    @Test
    void clearsCurrentPeriodEndWhenEmptyString() {
        restaurant.setCurrentPeriodStart(OffsetDateTime.now());
        restaurant.setCurrentPeriodEnd(OffsetDateTime.now().plusDays(10));
        when(restaurantRepository.findById(10L)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = SuperAdminTenantSubscriptionRequest.builder()
                .plan(SubscriptionPlan.PRO)
                .paymentStatus(PaymentStatus.ACTIVE)
                .currentPeriodEnd("")
                .build();

        var response = superAdminService.updateTenantSubscription(10L, request, "sa@platolisto.com");

        assertThat(response.getCurrentPeriodEnd()).isNull();
        ArgumentCaptor<Restaurant> captor = ArgumentCaptor.forClass(Restaurant.class);
        verify(restaurantRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentPeriodEnd()).isNull();
    }

    @Test
    void expiredPeriodUnpublishesWebsiteAndDisablesModules() {
        restaurant.setHasPickup(true);
        restaurant.setHasDelivery(true);
        restaurant.setWebsitePublished(true);
        when(restaurantRepository.findById(10L)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(inv -> inv.getArgument(0));

        String expired = OffsetDateTime.now().minusDays(2).toString();
        var request = SuperAdminTenantSubscriptionRequest.builder()
                .plan(SubscriptionPlan.PRO)
                .paymentStatus(PaymentStatus.ACTIVE)
                .currentPeriodEnd(expired)
                .build();

        var response = superAdminService.updateTenantSubscription(10L, request, "sa@platolisto.com");

        assertThat(response.isWebsitePublished()).isFalse();
        ArgumentCaptor<Restaurant> captor = ArgumentCaptor.forClass(Restaurant.class);
        verify(restaurantRepository).save(captor.capture());
        assertThat(captor.getValue().isHasPickup()).isFalse();
        assertThat(captor.getValue().isHasDelivery()).isFalse();
        assertThat(captor.getValue().isWebsitePublished()).isFalse();
    }
}
