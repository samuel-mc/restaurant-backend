package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.ActiveSessionResponse;
import com.platolisto.restaurant_backend.dto.OrderRequest;
import com.platolisto.restaurant_backend.dto.OrderResponse;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.security.ClientIpResolver;
import com.platolisto.restaurant_backend.security.OrderCreationRateLimitService;
import com.platolisto.restaurant_backend.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class PublicOrderController {

    private final OrderService orderService;
    private final OrderCreationRateLimitService orderCreationRateLimitService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody OrderRequest request,
            HttpServletRequest httpRequest
    ) {
        String ipKey = "orders:ip:" + clientIpResolver.resolve(httpRequest);
        Long tenantId = TenantContext.getCurrentTenant();
        String tenantKey = tenantId != null ? "orders:tenant:" + tenantId : null;
        orderCreationRateLimitService.assertAllowed(ipKey, tenantKey);
        try {
            OrderResponse response = orderService.createOrder(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } finally {
            orderCreationRateLimitService.record(ipKey, tenantKey);
        }
    }

    /**
     * Sesión activa de una mesa (cuenta abierta).
     * Requiere token del QR ({@code tableToken}); sin él no se revela si hay cuenta.
     */
    @GetMapping("/active-session")
    public ResponseEntity<ActiveSessionResponse> getActiveSession(
            @RequestParam("tableNumber") String tableNumber,
            @RequestParam(value = "tableToken", required = false) String tableToken
    ) {
        ActiveSessionResponse session = orderService.getActiveSessionByTable(tableNumber, tableToken);
        if (!session.isHasActiveOrder()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(session);
    }

    /** Estado inicial del pedido para la pantalla de tracking del comensal. */
    @GetMapping("/{uuid}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID uuid) {
        return ResponseEntity.ok(orderService.getOrderByUuid(uuid));
    }
}
