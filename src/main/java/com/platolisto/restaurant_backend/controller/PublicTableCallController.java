package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.TableCallRequest;
import com.platolisto.restaurant_backend.dto.TableCallResponse;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.security.ClientIpResolver;
import com.platolisto.restaurant_backend.security.TableCallRateLimitService;
import com.platolisto.restaurant_backend.service.TableCallService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders/table-calls")
@RequiredArgsConstructor
public class PublicTableCallController {

    private final TableCallService tableCallService;
    private final TableCallRateLimitService tableCallRateLimitService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping
    public ResponseEntity<TableCallResponse> createCall(
            @Valid @RequestBody TableCallRequest request,
            HttpServletRequest httpRequest
    ) {
        String ipKey = "table-calls:ip:" + clientIpResolver.resolve(httpRequest);
        Long tenantId = TenantContext.getCurrentTenant();
        String tenantKey = tenantId != null ? "table-calls:tenant:" + tenantId : null;
        String tableKey = request.getTableNumber() == null || request.getTableNumber().isBlank()
                ? null
                : "table-calls:table:" + tenantId + ":" + request.getTableNumber().trim().toLowerCase();

        tableCallRateLimitService.assertAllowed(ipKey, tenantKey, tableKey);
        try {
            TableCallResponse response = tableCallService.createCall(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } finally {
            tableCallRateLimitService.record(ipKey, tenantKey, tableKey);
        }
    }
}
