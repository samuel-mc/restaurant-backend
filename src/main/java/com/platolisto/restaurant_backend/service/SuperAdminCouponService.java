package com.platolisto.restaurant_backend.service;

import com.platolisto.restaurant_backend.dto.superadmin.SuperAdminCouponCreateRequest;
import com.platolisto.restaurant_backend.dto.superadmin.SuperAdminCouponResponse;
import com.platolisto.restaurant_backend.dto.superadmin.SuperAdminCouponUpdateRequest;
import com.platolisto.restaurant_backend.entity.Coupon;
import com.platolisto.restaurant_backend.entity.SubscriptionPlan;
import com.platolisto.restaurant_backend.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Administración de cupones desde SuperAdmin (CRUD sin hard-delete).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SuperAdminCouponService {

    private final CouponRepository couponRepository;

    @Transactional(readOnly = true)
    public List<SuperAdminCouponResponse> listCoupons() {
        return couponRepository.findAll().stream()
                .sorted(Comparator.comparing(
                        Coupon::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SuperAdminCouponResponse createCoupon(SuperAdminCouponCreateRequest request, String actorEmail) {
        String code = normalizeCode(request.getCode());
        if (couponRepository.existsByCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("Ya existe un cupón con el código \"" + code + "\".");
        }

        SubscriptionPlan grantsPlan = resolveGrantsPlan(request.getGrantsPlan());
        OffsetDateTime expiresAt = parseExpiresAt(request.getExpiresAt(), false);

        Coupon coupon = couponRepository.save(Coupon.builder()
                .code(code)
                .description(trimToNull(request.getDescription()))
                .grantsPlan(grantsPlan)
                .maxRedemptions(request.getMaxRedemptions())
                .redemptionCount(0)
                .active(true)
                .expiresAt(expiresAt)
                .build());

        log.info(
                "Cupón creado por SuperAdmin: actor={} code={} grantsPlan={} maxRedemptions={}",
                actorEmail,
                coupon.getCode(),
                coupon.getGrantsPlan(),
                coupon.getMaxRedemptions()
        );
        return toResponse(coupon);
    }

    @Transactional
    public SuperAdminCouponResponse updateCoupon(
            Long id,
            SuperAdminCouponUpdateRequest request,
            String actorEmail
    ) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cupón no encontrado."));

        if (request.getDescription() != null) {
            coupon.setDescription(trimToNull(request.getDescription()));
        }
        if (request.getGrantsPlan() != null) {
            coupon.setGrantsPlan(resolveGrantsPlan(request.getGrantsPlan()));
        }
        if (Boolean.TRUE.equals(request.getClearMaxRedemptions())) {
            coupon.setMaxRedemptions(null);
        } else if (request.getMaxRedemptions() != null) {
            coupon.setMaxRedemptions(request.getMaxRedemptions());
        }
        if (request.getExpiresAt() != null) {
            coupon.setExpiresAt(parseExpiresAt(request.getExpiresAt(), true));
        }
        if (request.getActive() != null) {
            coupon.setActive(request.getActive());
        }

        Coupon saved = couponRepository.save(coupon);
        log.info(
                "Cupón actualizado por SuperAdmin: actor={} id={} code={} active={}",
                actorEmail,
                saved.getId(),
                saved.getCode(),
                saved.isActive()
        );
        return toResponse(saved);
    }

    private SuperAdminCouponResponse toResponse(Coupon coupon) {
        return SuperAdminCouponResponse.builder()
                .id(coupon.getId())
                .code(coupon.getCode())
                .description(coupon.getDescription())
                .grantsPlan(coupon.getGrantsPlan() != null ? coupon.getGrantsPlan().name() : "PRO")
                .maxRedemptions(coupon.getMaxRedemptions())
                .redemptionCount(coupon.getRedemptionCount())
                .active(coupon.isActive())
                .expiresAt(format(coupon.getExpiresAt()))
                .createdAt(format(coupon.getCreatedAt()))
                .build();
    }

    private static SubscriptionPlan resolveGrantsPlan(SubscriptionPlan requested) {
        if (requested == null) {
            return SubscriptionPlan.PRO;
        }
        if (requested == SubscriptionPlan.BASIC || requested == SubscriptionPlan.PRO) {
            return requested;
        }
        throw new IllegalArgumentException("Plan del cupón no válido. Elige BASIC o PRO.");
    }

    private static String normalizeCode(String raw) {
        return raw.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * @param allowClear si true, cadena vacía → null (sin expiración).
     */
    private static OffsetDateTime parseExpiresAt(String raw, boolean allowClear) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            if (allowClear) {
                return null;
            }
            throw new IllegalArgumentException("La fecha de expiración no es válida.");
        }
        try {
            return OffsetDateTime.parse(trimmed);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "La fecha de expiración debe ser ISO-8601 (ej. 2026-12-31T23:59:59Z)."
            );
        }
    }

    private static String format(OffsetDateTime value) {
        return value == null ? null : value.toString();
    }
}
