package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.RestaurantProfileResponse;
import com.platolisto.restaurant_backend.service.RestaurantProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Perfil público del restaurante (identidad + módulos).
 * Requiere cabecera {@code X-Tenant} (TenantFilter).
 */
@RestController
@RequestMapping("/api/v1/restaurants")
@RequiredArgsConstructor
public class PublicRestaurantController {

    private final RestaurantProfileService restaurantProfileService;

    @GetMapping("/profile")
    public ResponseEntity<RestaurantProfileResponse> getPublicProfile() {
        return ResponseEntity.ok(restaurantProfileService.getProfile());
    }
}
