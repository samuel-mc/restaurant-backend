package com.platolisto.restaurant_backend.service;

import com.platolisto.restaurant_backend.dto.RedeemCouponRequest;
import com.platolisto.restaurant_backend.dto.RedeemCouponResponse;
import com.platolisto.restaurant_backend.entity.Coupon;
import com.platolisto.restaurant_backend.entity.CouponRedemption;
import com.platolisto.restaurant_backend.entity.PaymentStatus;
import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.entity.SubscriptionPlan;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.repository.CouponRedemptionRepository;
import com.platolisto.restaurant_backend.repository.CouponRepository;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository couponRedemptionRepository;
    private final RestaurantRepository restaurantRepository;

    @Transactional
    public RedeemCouponResponse redeem(RedeemCouponRequest request) {
        Restaurant restaurant = requireCurrentRestaurant();
        String code = normalizeCode(request.getCode());

        Coupon coupon = couponRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cupón no válido. Verifica el código e intenta de nuevo."
                ));

        validateCoupon(coupon, restaurant.getId());

        couponRedemptionRepository.save(CouponRedemption.builder()
                .coupon(coupon)
                .restaurant(restaurant)
                .build());

        coupon.setRedemptionCount(coupon.getRedemptionCount() + 1);
        couponRepository.save(coupon);

        SubscriptionPlan granted = coupon.getGrantsPlan() != null
                ? coupon.getGrantsPlan()
                : SubscriptionPlan.PRO;

        restaurant.setPlan(granted);
        restaurant.setPaymentStatus(PaymentStatus.ACTIVE);
        if (granted == SubscriptionPlan.PRO) {
            restaurant.setWebsitePublished(true);
        }
        restaurantRepository.save(restaurant);

        log.info(
                "Cupón {} canjeado por restaurant={} → plan={}, payment=ACTIVE",
                coupon.getCode(),
                restaurant.getSubdomain(),
                granted
        );

        return RedeemCouponResponse.builder()
                .message("Cupón aplicado. Tu Plan Pro quedó activo.")
                .plan(restaurant.getPlan().name())
                .paymentStatus(restaurant.getPaymentStatus().name())
                .websitePublished(restaurant.isWebsitePublished())
                .redeemedCode(coupon.getCode())
                .build();
    }

    /**
     * Aplica cupón en el registro (mismo efecto que canjear en settings).
     * Devuelve true si se aplicó; false si no venía código.
     */
    @Transactional
    public boolean applyCouponAtRegistration(Restaurant restaurant, String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return false;
        }
        String code = normalizeCode(rawCode);
        Coupon coupon = couponRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cupón no válido. Déjalo vacío o usa un código correcto."
                ));

        validateCoupon(coupon, restaurant.getId());

        couponRedemptionRepository.save(CouponRedemption.builder()
                .coupon(coupon)
                .restaurant(restaurant)
                .build());
        coupon.setRedemptionCount(coupon.getRedemptionCount() + 1);
        couponRepository.save(coupon);

        SubscriptionPlan granted = coupon.getGrantsPlan() != null
                ? coupon.getGrantsPlan()
                : SubscriptionPlan.PRO;
        restaurant.setPlan(granted);
        restaurant.setPaymentStatus(PaymentStatus.ACTIVE);
        if (granted == SubscriptionPlan.PRO) {
            restaurant.setWebsitePublished(true);
        }
        return true;
    }

    private void validateCoupon(Coupon coupon, Long restaurantId) {
        if (!coupon.isActive()) {
            throw new IllegalArgumentException("Este cupón ya no está activo.");
        }
        if (coupon.getExpiresAt() != null && coupon.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("Este cupón expiró.");
        }
        if (coupon.getMaxRedemptions() != null
                && coupon.getRedemptionCount() >= coupon.getMaxRedemptions()) {
            throw new IllegalArgumentException("Este cupón ya alcanzó el máximo de usos.");
        }
        if (couponRedemptionRepository.existsByCoupon_IdAndRestaurant_Id(coupon.getId(), restaurantId)) {
            throw new IllegalArgumentException("Este restaurante ya canjeó ese cupón.");
        }
    }

    private Restaurant requireCurrentRestaurant() {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            throw new IllegalStateException("No se pudo identificar el restaurante en el contexto actual.");
        }
        return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("El restaurante asociado no existe."));
    }

    private static String normalizeCode(String raw) {
        return raw.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
