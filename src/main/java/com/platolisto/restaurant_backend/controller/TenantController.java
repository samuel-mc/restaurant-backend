package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.TenantRegisterRequest;
import com.platolisto.restaurant_backend.dto.TenantRegisterResponse;
import com.platolisto.restaurant_backend.security.ClientIpResolver;
import com.platolisto.restaurant_backend.security.RegistrationRateLimitService;
import com.platolisto.restaurant_backend.service.TenantRegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantRegistrationService tenantRegistrationService;
    private final RegistrationRateLimitService registrationRateLimitService;
    private final ClientIpResolver clientIpResolver;

    /** Onboarding público: crea restaurante + usuario OWNER. */
    @PostMapping("/register")
    public ResponseEntity<TenantRegisterResponse> register(
            @Valid @RequestBody TenantRegisterRequest request,
            HttpServletRequest httpRequest
    ) {
        String ipKey = "register:ip:" + clientIpResolver.resolve(httpRequest);
        String emailKey = emailKey(request);
        registrationRateLimitService.assertAllowed(ipKey, emailKey);
        try {
            TenantRegisterResponse response = tenantRegistrationService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } finally {
            // Cuenta éxitos y rechazos de negocio (slug/email ocupado) para frenar abuso.
            registrationRateLimitService.record(ipKey, emailKey);
        }
    }

    private static String emailKey(TenantRegisterRequest request) {
        String email = request.getOwnerEmail() != null
                ? request.getOwnerEmail().trim().toLowerCase(Locale.ROOT)
                : "unknown";
        return "register:email:" + email;
    }
}
