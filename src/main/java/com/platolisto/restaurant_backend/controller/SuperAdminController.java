package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.LoginRequest;
import com.platolisto.restaurant_backend.dto.LoginResponse;
import com.platolisto.restaurant_backend.dto.superadmin.ImpersonateResponse;
import com.platolisto.restaurant_backend.dto.superadmin.SuperAdminMetricsResponse;
import com.platolisto.restaurant_backend.dto.superadmin.SuperAdminTenantResponse;
import com.platolisto.restaurant_backend.dto.superadmin.SuperAdminTenantStatusRequest;
import com.platolisto.restaurant_backend.service.SuperAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/superadmin")
@RequiredArgsConstructor
public class SuperAdminController {

    private final SuperAdminService superAdminService;

    @PostMapping("/auth/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(superAdminService.login(request));
    }

    @GetMapping("/metrics")
    public ResponseEntity<SuperAdminMetricsResponse> metrics() {
        return ResponseEntity.ok(superAdminService.metrics());
    }

    @GetMapping("/tenants")
    public ResponseEntity<List<SuperAdminTenantResponse>> listTenants() {
        return ResponseEntity.ok(superAdminService.listTenants());
    }

    @PatchMapping("/tenants/{id}/status")
    public ResponseEntity<SuperAdminTenantResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody SuperAdminTenantStatusRequest request
    ) {
        return ResponseEntity.ok(
                superAdminService.updateTenantStatus(id, Boolean.TRUE.equals(request.getActive()))
        );
    }

    @PostMapping("/tenants/{id}/impersonate")
    public ResponseEntity<ImpersonateResponse> impersonate(@PathVariable Long id) {
        return ResponseEntity.ok(superAdminService.impersonate(id));
    }
}
