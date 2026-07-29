package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.LoginRequest;
import com.platolisto.restaurant_backend.dto.LoginResponse;
import com.platolisto.restaurant_backend.dto.superadmin.ImpersonateResponse;
import com.platolisto.restaurant_backend.dto.superadmin.SuperAdminMetricsResponse;
import com.platolisto.restaurant_backend.dto.superadmin.SuperAdminTenantResponse;
import com.platolisto.restaurant_backend.dto.superadmin.SuperAdminTenantStatusRequest;
import com.platolisto.restaurant_backend.security.ClientIpResolver;
import com.platolisto.restaurant_backend.security.LoginAttemptService;
import com.platolisto.restaurant_backend.service.SuperAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/superadmin")
@RequiredArgsConstructor
public class SuperAdminController {

    private final SuperAdminService superAdminService;
    private final LoginAttemptService loginAttemptService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping("/auth/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String email = request.getEmail() != null
                ? request.getEmail().trim().toLowerCase(Locale.ROOT)
                : "unknown";
        String accountKey = "sa:email:" + email;
        String ipKey = "sa:ip:" + clientIpResolver.resolve(httpRequest);
        loginAttemptService.assertNotLocked(accountKey, ipKey);
        try {
            LoginResponse response = superAdminService.login(request);
            loginAttemptService.recordSuccess(accountKey, ipKey);
            return ResponseEntity.ok(response);
        } catch (BadCredentialsException | UsernameNotFoundException ex) {
            loginAttemptService.recordFailure(accountKey, ipKey);
            throw ex;
        } catch (AuthenticationException ex) {
            loginAttemptService.recordFailure(accountKey, ipKey);
            throw ex;
        } catch (IllegalArgumentException ex) {
            // Password ok pero sin rol SuperAdmin / cuenta inactiva: cuenta igual.
            loginAttemptService.recordFailure(accountKey, ipKey);
            throw ex;
        }
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
