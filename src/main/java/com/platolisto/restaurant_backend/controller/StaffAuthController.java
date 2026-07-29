package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.StaffPinLoginRequest;
import com.platolisto.restaurant_backend.dto.StaffPinLoginResponse;
import com.platolisto.restaurant_backend.security.ClientIpResolver;
import com.platolisto.restaurant_backend.security.LoginAttemptService;
import com.platolisto.restaurant_backend.service.StaffAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
public class StaffAuthController {

    private final StaffAuthService staffAuthService;
    private final LoginAttemptService loginAttemptService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping("/login-pin")
    public ResponseEntity<StaffPinLoginResponse> loginWithPin(
            @Valid @RequestBody StaffPinLoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String accountKey = accountKey(request);
        String ipKey = ipKey(httpRequest);
        loginAttemptService.assertNotLocked(accountKey, ipKey);
        try {
            StaffPinLoginResponse response = staffAuthService.loginWithPin(request);
            loginAttemptService.recordSuccess(accountKey, ipKey);
            return ResponseEntity.ok(response);
        } catch (BadCredentialsException ex) {
            loginAttemptService.recordFailure(accountKey, ipKey);
            throw ex;
        }
    }

    private static String accountKey(StaffPinLoginRequest body) {
        String staffId = body.getStaffId() != null ? body.getStaffId().toString() : "unknown";
        String slug = body.getTenantSlug() != null ? body.getTenantSlug().trim().toLowerCase() : "unknown";
        return "pin:staff:" + slug + ":" + staffId;
    }

    private String ipKey(HttpServletRequest request) {
        return "pin:ip:" + clientIpResolver.resolve(request);
    }
}
