package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.ProductResponse;
import com.platolisto.restaurant_backend.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @GetMapping("/catalog")
    public ResponseEntity<List<ProductResponse>> getCatalog() {
        List<ProductResponse> catalog = menuService.getPublicCatalog();
        return ResponseEntity.ok(catalog);
    }
}
