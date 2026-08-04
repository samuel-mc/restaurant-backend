package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.ProductResponse;
import com.platolisto.restaurant_backend.service.ProductService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/menu")
@RequiredArgsConstructor
public class AdminMenuController {

    private final ProductService productService;

    @Data
    public static class ProductAvailabilityRequest {
        private Boolean isAvailable;
    }

    @PatchMapping("/products/{id}/availability")
    public ResponseEntity<ProductResponse> updateProductAvailability(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) ProductAvailabilityRequest request
    ) {
        Boolean targetAvailability = (request != null) ? request.getIsAvailable() : null;
        ProductResponse response = productService.updateProductAvailability(id, targetAvailability);
        return ResponseEntity.ok(response);
    }
}
