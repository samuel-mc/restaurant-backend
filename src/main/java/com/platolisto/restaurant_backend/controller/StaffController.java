package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.PublicStaffMemberResponse;
import com.platolisto.restaurant_backend.service.PublicStaffService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Directorio público de personal activo del tenant (login por selección + PIN).
 */
@RestController
@RequestMapping("/api/v1/public/staff")
@RequiredArgsConstructor
public class StaffController {

    private final PublicStaffService publicStaffService;

    @GetMapping
    public ResponseEntity<List<PublicStaffMemberResponse>> listActiveStaff(
            @RequestParam String tenantSlug
    ) {
        return ResponseEntity.ok(publicStaffService.listActiveStaff(tenantSlug));
    }
}
