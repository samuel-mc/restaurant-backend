package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.OrderItemStatusRequest;
import com.platolisto.restaurant_backend.dto.OrderResponse;
import com.platolisto.restaurant_backend.dto.OrderStatusRequest;
import com.platolisto.restaurant_backend.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints del KDS (Kitchen Display).
 * La UI de cocina también usa {@code /api/v1/admin/orders}; este path
 * deja explícito el contrato de permisos ROLE_COCINA.
 */
@RestController
@RequestMapping("/api/v1/admin/kitchen")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('COCINA', 'ADMIN', 'OWNER')")
public class AdminKitchenController {

    private final OrderService orderService;

    @GetMapping("/orders/active")
    public ResponseEntity<List<OrderResponse>> getActiveOrders() {
        return ResponseEntity.ok(orderService.getActiveOrders());
    }

    @PatchMapping("/orders/{uuid}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable UUID uuid,
            @Valid @RequestBody OrderStatusRequest request
    ) {
        return ResponseEntity.ok(orderService.updateOrderStatus(uuid, request.getStatus()));
    }

    @PatchMapping("/orders/{uuid}/items/{detailId}/status")
    public ResponseEntity<OrderResponse> updateItemStatus(
            @PathVariable UUID uuid,
            @PathVariable Long detailId,
            @Valid @RequestBody OrderItemStatusRequest request
    ) {
        return ResponseEntity.ok(
                orderService.updateOrderItemStatus(uuid, detailId, request.getStatus())
        );
    }
}
