package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.RestaurantProfileRequest;
import com.platolisto.restaurant_backend.dto.RestaurantProfileResponse;
import com.platolisto.restaurant_backend.service.RestaurantProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/restaurants/profile")
@RequiredArgsConstructor
public class RestaurantProfileController {

    private final RestaurantProfileService restaurantProfileService;

    @GetMapping
    public ResponseEntity<RestaurantProfileResponse> getProfile() {
        return ResponseEntity.ok(restaurantProfileService.getProfile());
    }

    /** Actualización JSON (sin archivos). */
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RestaurantProfileResponse> updateProfileJson(
            @Valid @RequestBody RestaurantProfileRequest request
    ) {
        return ResponseEntity.ok(restaurantProfileService.updateProfile(request, null, null, null));
    }

    /**
     * Actualización multipart: campos de texto + {@code logo} / {@code banner} / {@code favicon} opcionales.
     * {@code multipart/*} tolera {@code charset} del BFF (undici).
     */
    @PutMapping(consumes = "multipart/*")
    public ResponseEntity<RestaurantProfileResponse> updateProfileMultipart(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "primaryColor", required = false) String primaryColor,
            @RequestParam(value = "secondaryColor", required = false) String secondaryColor,
            @RequestParam(value = "address", required = false) String address,
            @RequestParam(value = "googleMapsUrl", required = false) String googleMapsUrl,
            @RequestParam(value = "whatsapp", required = false) String whatsapp,
            @RequestParam(value = "businessHours", required = false) String businessHours,
            @RequestParam(value = "hasDelivery", required = false) Boolean hasDelivery,
            @RequestParam(value = "hasPickup", required = false) Boolean hasPickup,
            @RequestParam(value = "hasReservations", required = false) Boolean hasReservations,
            @RequestParam(value = "orderingEnabled", required = false) Boolean orderingEnabled,
            @RequestParam(value = "tableCount", required = false) Integer tableCount,
            @RequestParam(value = "websitePublished", required = false) Boolean websitePublished,
            @RequestParam(value = "logo", required = false) MultipartFile logo,
            @RequestParam(value = "banner", required = false) MultipartFile banner,
            @RequestParam(value = "favicon", required = false) MultipartFile favicon
    ) {
        RestaurantProfileRequest request = RestaurantProfileRequest.builder()
                .name(name)
                .description(description)
                .primaryColor(primaryColor)
                .secondaryColor(secondaryColor)
                .address(address)
                .googleMapsUrl(googleMapsUrl)
                .whatsapp(whatsapp)
                .businessHours(businessHours)
                .hasDelivery(hasDelivery)
                .hasPickup(hasPickup)
                .hasReservations(hasReservations)
                .orderingEnabled(orderingEnabled)
                .tableCount(tableCount)
                .websitePublished(websitePublished)
                .build();

        return ResponseEntity.ok(
                restaurantProfileService.updateProfile(request, logo, banner, favicon)
        );
    }
}
