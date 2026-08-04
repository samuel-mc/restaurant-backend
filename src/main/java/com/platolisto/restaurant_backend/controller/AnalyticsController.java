package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.AnalyticsSummaryResponse;
import com.platolisto.restaurant_backend.dto.DailySummaryResponse;
import com.platolisto.restaurant_backend.dto.ShiftCloseResponse;
import com.platolisto.restaurant_backend.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

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

    /**
     * Métricas diarias y resumen de caja acumulado del día.
     */
    @GetMapping("/daily-summary")
    public ResponseEntity<DailySummaryResponse> getDailySummary() {
        return ResponseEntity.ok(analyticsService.getDailySummary());
    }

    /**
     * Cierre de Caja / Turno (Corte Z).
     */
    @PostMapping("/close-shift")
    public ResponseEntity<ShiftCloseResponse> closeShift(Principal principal) {
        String name = principal != null ? principal.getName() : "Manager";
        return ResponseEntity.ok(analyticsService.closeShift(name));
    }
}
