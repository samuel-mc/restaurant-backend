package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.PublicHealthResponse;
import com.platolisto.restaurant_backend.service.PublicHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/health")
@RequiredArgsConstructor
public class PublicHealthController {

    private final PublicHealthService publicHealthService;

    @GetMapping
    public ResponseEntity<PublicHealthResponse> checkHealth() {
        return ResponseEntity.ok(publicHealthService.checkHealth());
    }
}
