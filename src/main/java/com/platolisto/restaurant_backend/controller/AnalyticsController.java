package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.AnalyticsSummaryResponse;
import com.platolisto.restaurant_backend.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * Resumen de métricas de negocio.
     *
     * @param period {@code week} | {@code month} (default) | {@code year}
     */
    @GetMapping("/summary")
    public ResponseEntity<AnalyticsSummaryResponse> getSummary(
            @RequestParam(defaultValue = "month") String period
    ) {
        return ResponseEntity.ok(analyticsService.getSummary(period));
    }
}
