package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.ProductRequest;
import com.platolisto.restaurant_backend.dto.ProductResponse;
import com.platolisto.restaurant_backend.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /** Alta JSON (sin archivo). */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        ProductResponse response = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Alta multipart (R2-ready): campos de texto + archivo {@code image}.
     * <p>
     * {@code multipart/*} acepta {@code multipart/form-data} con cualquier parámetro
     * ({@code boundary}, {@code charset}, etc.) — necesario para el FormData del BFF (undici).
     */
    @PostMapping(consumes = "multipart/*")
    public ResponseEntity<ProductResponse> createProductMultipart(
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("price") BigDecimal price,
            @RequestParam("categoryId") Long categoryId,
            @RequestParam(value = "image", required = false) MultipartFile image
    ) {
        ProductRequest request = ProductRequest.builder()
                .name(name)
                .description(description)
                .price(price)
                .categoryId(categoryId)
                .build();
        ProductResponse response = productService.createProduct(request, image);
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

    @PutMapping(path = "/{uuid}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable UUID uuid,
            @Valid @RequestBody ProductRequest request
    ) {
        ProductResponse response = productService.updateProduct(uuid, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping(path = "/{uuid}", consumes = "multipart/*")
    public ResponseEntity<ProductResponse> updateProductMultipart(
            @PathVariable UUID uuid,
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("price") BigDecimal price,
            @RequestParam("categoryId") Long categoryId,
            @RequestParam(value = "image", required = false) MultipartFile image
    ) {
        ProductRequest request = ProductRequest.builder()
                .name(name)
                .description(description)
                .price(price)
                .categoryId(categoryId)
                .build();
        ProductResponse response = productService.updateProduct(uuid, request, image);
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
