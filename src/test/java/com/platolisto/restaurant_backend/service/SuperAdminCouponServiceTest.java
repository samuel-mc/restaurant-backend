package com.platolisto.restaurant_backend.service;

import com.platolisto.restaurant_backend.dto.superadmin.SuperAdminCouponCreateRequest;
import com.platolisto.restaurant_backend.dto.superadmin.SuperAdminCouponUpdateRequest;
import com.platolisto.restaurant_backend.entity.Coupon;
import com.platolisto.restaurant_backend.entity.SubscriptionPlan;
import com.platolisto.restaurant_backend.repository.CouponRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAdminCouponServiceTest {

    @Mock
    private CouponRepository couponRepository;

    @InjectMocks
    private SuperAdminCouponService superAdminCouponService;

    @Test
    void createRejectsDuplicateCode() {
        when(couponRepository.existsByCodeIgnoreCase("PRO-DEMO")).thenReturn(true);

        var request = SuperAdminCouponCreateRequest.builder()
                .code("pro-demo")
                .grantsPlan(SubscriptionPlan.PRO)
                .build();

        assertThatThrownBy(() ->
                superAdminCouponService.createCoupon(request, "sa@platolisto.com")
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ya existe");
    }

    @Test
    void createNormalizesCodeAndDefaultsToPro() {
        when(couponRepository.existsByCodeIgnoreCase("EARLY-100")).thenReturn(false);
        when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> {
            Coupon c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        var request = SuperAdminCouponCreateRequest.builder()
                .code(" early-100 ")
                .description("Efectivo")
                .maxRedemptions(10)
                .build();

        var response = superAdminCouponService.createCoupon(request, "sa@platolisto.com");

        assertThat(response.getCode()).isEqualTo("EARLY-100");
        assertThat(response.getGrantsPlan()).isEqualTo("PRO");
        assertThat(response.getMaxRedemptions()).isEqualTo(10);
        assertThat(response.isActive()).isTrue();
    }

    @Test
    void updateCanDeactivateCoupon() {
        Coupon existing = Coupon.builder()
                .id(5L)
                .code("SETUP-CASH")
                .grantsPlan(SubscriptionPlan.PRO)
                .active(true)
                .redemptionCount(2)
                .build();
        when(couponRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = SuperAdminCouponUpdateRequest.builder()
                .active(false)
                .build();

        var response = superAdminCouponService.updateCoupon(5L, request, "sa@platolisto.com");

        assertThat(response.isActive()).isFalse();
        ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
        verify(couponRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
    }

    @Test
    void createPersistsGrantDurationDays() {
        when(couponRepository.existsByCodeIgnoreCase("PRO-30")).thenReturn(false);
        when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> {
            Coupon c = inv.getArgument(0);
            c.setId(2L);
            return c;
        });

        var request = SuperAdminCouponCreateRequest.builder()
                .code("pro-30")
                .grantDurationDays(30)
                .build();

        var response = superAdminCouponService.createCoupon(request, "sa@platolisto.com");

        assertThat(response.getGrantDurationDays()).isEqualTo(30);
        ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
        verify(couponRepository).save(captor.capture());
        assertThat(captor.getValue().getGrantDurationDays()).isEqualTo(30);
    }
}
