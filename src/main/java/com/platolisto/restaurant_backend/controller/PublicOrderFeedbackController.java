package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.FeedbackStatusResponse;
import com.platolisto.restaurant_backend.dto.SubmitFeedbackRequest;
import com.platolisto.restaurant_backend.dto.SubmitFeedbackResponse;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.security.ClientIpResolver;
import com.platolisto.restaurant_backend.security.OrderCreationRateLimitService;
import com.platolisto.restaurant_backend.service.OrderFeedbackService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Smart Rating público: evaluación post-{@code CLOSED} por UUID del pedido.
 */
@RestController
@RequestMapping("/api/v1/orders/{uuid}/feedback")
@RequiredArgsConstructor
public class PublicOrderFeedbackController {

    private final OrderFeedbackService orderFeedbackService;
    private final OrderCreationRateLimitService orderCreationRateLimitService;
    private final ClientIpResolver clientIpResolver;

    @GetMapping
    public ResponseEntity<FeedbackStatusResponse> status(@PathVariable UUID uuid) {
        return ResponseEntity.ok(orderFeedbackService.status(uuid));
    }

    @PostMapping
    public ResponseEntity<SubmitFeedbackResponse> submit(
            @PathVariable UUID uuid,
            @Valid @RequestBody SubmitFeedbackRequest request,
            HttpServletRequest httpRequest
    ) {
        String ipKey = "feedback:ip:" + clientIpResolver.resolve(httpRequest);
        Long tenantId = TenantContext.getCurrentTenant();
        String tenantKey = tenantId != null ? "feedback:tenant:" + tenantId : null;
        orderCreationRateLimitService.assertAllowed(ipKey, tenantKey);
        try {
            SubmitFeedbackResponse response = orderFeedbackService.submit(uuid, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } finally {
            orderCreationRateLimitService.record(ipKey, tenantKey);
        }
    }
}
