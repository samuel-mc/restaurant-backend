package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.AdminOrderListFilter;
import com.platolisto.restaurant_backend.dto.OrderStatusRequest;
import com.platolisto.restaurant_backend.dto.OrderItemStatusRequest;
import com.platolisto.restaurant_backend.dto.OrderResponse;
import com.platolisto.restaurant_backend.entity.OrderStatus;
import com.platolisto.restaurant_backend.entity.OrderType;
import com.platolisto.restaurant_backend.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MESERO', 'COCINA', 'ADMIN', 'OWNER')")
public class AdminOrderController {

    private final OrderService orderService;

    /**
     * Listado paginado de pedidos/cuentas para el panel admin.
     * {@code filter}: ALL | OPEN | CLOSED | PICKUP (atajo de UI).
     * También acepta {@code status} y {@code orderType} sueltos.
     */
    @GetMapping
    public ResponseEntity<Page<OrderResponse>> listOrders(
            @RequestParam(required = false) AdminOrderListFilter filter,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) OrderType orderType,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ResponseEntity.ok(
                orderService.listOrders(filter, status, orderType, pageable)
        );
    }

    /** Snapshot de comandas activas para el monitor de cocina/caja. */
    @GetMapping("/active")
    public ResponseEntity<List<OrderResponse>> getActiveOrders() {
        return ResponseEntity.ok(orderService.getActiveOrders());
    }

    @PatchMapping("/{uuid}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable UUID uuid,
            @Valid @RequestBody OrderStatusRequest request
    ) {
        OrderResponse response = orderService.updateOrderStatus(uuid, request.getStatus());
        return ResponseEntity.ok(response);
    }

    /** Cierra y cobra la cuenta (libera la mesa). */
    @PatchMapping("/{uuid}/close")
    public ResponseEntity<OrderResponse> closeOrder(@PathVariable UUID uuid) {
        return ResponseEntity.ok(orderService.closeOrder(uuid));
    }

    /** Actualiza el estado de un ítem individual (p. ej. marcar un platillo como entregado). */
    @PatchMapping("/{uuid}/items/{detailId}/status")
    public ResponseEntity<OrderResponse> updateOrderItemStatus(
            @PathVariable UUID uuid,
            @PathVariable Long detailId,
            @Valid @RequestBody OrderItemStatusRequest request
    ) {
        return ResponseEntity.ok(
                orderService.updateOrderItemStatus(uuid, detailId, request.getStatus())
        );
    }
}
