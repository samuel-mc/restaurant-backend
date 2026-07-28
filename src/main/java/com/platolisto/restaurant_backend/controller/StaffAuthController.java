package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.StaffPinLoginRequest;
import com.platolisto.restaurant_backend.dto.StaffPinLoginResponse;
import com.platolisto.restaurant_backend.service.StaffAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
public class StaffAuthController {

    private final StaffAuthService staffAuthService;

    @PostMapping("/login-pin")
    public ResponseEntity<StaffPinLoginResponse> loginWithPin(
            @Valid @RequestBody StaffPinLoginRequest request
    ) {
        return ResponseEntity.ok(staffAuthService.loginWithPin(request));
    }
}
