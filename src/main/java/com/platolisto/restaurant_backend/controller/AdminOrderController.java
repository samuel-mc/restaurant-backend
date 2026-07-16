package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.OrderStatusRequest;
import com.platolisto.restaurant_backend.dto.OrderResponse;
import com.platolisto.restaurant_backend.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;

    @PatchMapping("/{uuid}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable UUID uuid,
            @Valid @RequestBody OrderStatusRequest request
    ) {
        OrderResponse response = orderService.updateOrderStatus(uuid, request.getStatus());
        return ResponseEntity.ok(response);
    }
}
