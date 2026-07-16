package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.ProductRequest;
import com.platolisto.restaurant_backend.dto.ProductResponse;
import com.platolisto.restaurant_backend.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        ProductResponse response = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ProductResponse>> getProducts() {
        List<ProductResponse> response = productService.getProducts();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable UUID uuid) {
        ProductResponse response = productService.getProduct(uuid);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{uuid}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable UUID uuid,
            @Valid @RequestBody ProductRequest request
    ) {
        ProductResponse response = productService.updateProduct(uuid, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> deleteProduct(@PathVariable UUID uuid) {
        productService.deleteProduct(uuid);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{uuid}/toggle-availability")
    public ResponseEntity<ProductResponse> toggleAvailability(@PathVariable UUID uuid) {
        ProductResponse response = productService.toggleAvailability(uuid);
        return ResponseEntity.ok(response);
    }
}
