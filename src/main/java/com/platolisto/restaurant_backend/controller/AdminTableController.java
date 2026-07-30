package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.OrderResponse;
import com.platolisto.restaurant_backend.dto.TableMergeRequest;
import com.platolisto.restaurant_backend.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/tables")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MESERO', 'ADMIN', 'OWNER')")
public class AdminTableController {

    private final OrderService orderService;

    /**
     * Une mesas secundarias a la cuenta de la mesa principal.
     * Tras el merge, {@code GET /orders/active-session} de cualquiera
     * de las mesas vinculadas devuelve la misma orden.
     */
    @PostMapping("/merge")
    public ResponseEntity<OrderResponse> mergeTables(@Valid @RequestBody TableMergeRequest request) {
        return ResponseEntity.ok(orderService.mergeTables(request));
    }
}
