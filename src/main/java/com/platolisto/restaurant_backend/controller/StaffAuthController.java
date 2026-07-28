package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.StaffPinLoginRequest;
import com.platolisto.restaurant_backend.dto.StaffPinLoginResponse;
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

    @PostMapping("/login-pin")
    public ResponseEntity<StaffPinLoginResponse> loginWithPin(
            @Valid @RequestBody StaffPinLoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String attemptKey = buildAttemptKey(httpRequest, request);
        loginAttemptService.assertNotLocked(attemptKey);
        try {
            StaffPinLoginResponse response = staffAuthService.loginWithPin(request);
            loginAttemptService.recordSuccess(attemptKey);
            return ResponseEntity.ok(response);
        } catch (BadCredentialsException ex) {
            loginAttemptService.recordFailure(attemptKey);
            throw ex;
        }
    }

    private String buildAttemptKey(HttpServletRequest request, StaffPinLoginRequest body) {
        String ip = clientIp(request);
        String staffId = body.getStaffId() != null ? body.getStaffId().toString() : "unknown";
        String slug = body.getTenantSlug() != null ? body.getTenantSlug().trim().toLowerCase() : "unknown";
        return ip + "|" + slug + "|" + staffId;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }
}
