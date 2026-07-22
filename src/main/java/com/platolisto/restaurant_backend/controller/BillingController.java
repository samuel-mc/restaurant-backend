package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.RedeemCouponRequest;
import com.platolisto.restaurant_backend.dto.RedeemCouponResponse;
import com.platolisto.restaurant_backend.service.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/billing")
@RequiredArgsConstructor
public class BillingController {

    private final CouponService couponService;

    @PostMapping("/redeem-coupon")
    public ResponseEntity<RedeemCouponResponse> redeemCoupon(
            @Valid @RequestBody RedeemCouponRequest request
    ) {
        return ResponseEntity.ok(couponService.redeem(request));
    }
}
