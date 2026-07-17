package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.TenantRegisterRequest;
import com.platolisto.restaurant_backend.dto.TenantRegisterResponse;
import com.platolisto.restaurant_backend.service.TenantRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantRegistrationService tenantRegistrationService;

    /** Onboarding público: crea restaurante + usuario OWNER. */
    @PostMapping("/register")
    public ResponseEntity<TenantRegisterResponse> register(
            @Valid @RequestBody TenantRegisterRequest request
    ) {
        TenantRegisterResponse response = tenantRegistrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
