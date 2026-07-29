package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.ImpersonationRedeemRequest;
import com.platolisto.restaurant_backend.dto.ImpersonationRedeemResponse;
import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import com.platolisto.restaurant_backend.security.ImpersonationHandoffService;
import com.platolisto.restaurant_backend.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Canje público (un solo uso) del código de handoff de impersonación.
 * Requiere {@code X-Tenant} del subdominio donde se abre el panel.
 */
@RestController
@RequestMapping("/api/v1/auth/impersonation")
@RequiredArgsConstructor
public class ImpersonationRedeemController {

    private final ImpersonationHandoffService impersonationHandoffService;
    private final JwtService jwtService;
    private final RestaurantRepository restaurantRepository;

    @PostMapping("/redeem")
    public ResponseEntity<ImpersonationRedeemResponse> redeem(
            @Valid @RequestBody ImpersonationRedeemRequest request
    ) {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalArgumentException("Falta el identificador del restaurante (X-Tenant).");
        }

        Restaurant restaurant = restaurantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurante no encontrado."));

        ImpersonationHandoffService.RedeemedHandoff redeemed =
                impersonationHandoffService.redeem(request.getCode(), restaurant.getSubdomain());

        if (!tenantId.equals(redeemed.restaurantId())) {
            throw new IllegalArgumentException(
                    "Código de impersonación no válido para este restaurante."
            );
        }

        long expiresInSeconds = Math.max(1L, jwtService.getImpersonationExpirationMs() / 1000L);
        return ResponseEntity.ok(ImpersonationRedeemResponse.builder()
                .token(redeemed.jwt())
                .expiresInSeconds(expiresInSeconds)
                .build());
    }
}
