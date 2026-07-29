package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.superadmin.SuperAdminCouponCreateRequest;
import com.platolisto.restaurant_backend.dto.superadmin.SuperAdminCouponResponse;
import com.platolisto.restaurant_backend.dto.superadmin.SuperAdminCouponUpdateRequest;
import com.platolisto.restaurant_backend.service.SuperAdminCouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/superadmin/coupons")
@RequiredArgsConstructor
public class SuperAdminCouponController {

    private final SuperAdminCouponService superAdminCouponService;

    @GetMapping
    public ResponseEntity<List<SuperAdminCouponResponse>> list() {
        return ResponseEntity.ok(superAdminCouponService.listCoupons());
    }

    @PostMapping
    public ResponseEntity<SuperAdminCouponResponse> create(
            @Valid @RequestBody SuperAdminCouponCreateRequest request,
            Authentication authentication
    ) {
        String actor = authentication != null ? authentication.getName() : null;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(superAdminCouponService.createCoupon(request, actor));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SuperAdminCouponResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody SuperAdminCouponUpdateRequest request,
            Authentication authentication
    ) {
        String actor = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(superAdminCouponService.updateCoupon(id, request, actor));
    }
}
