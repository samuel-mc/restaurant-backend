package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.OrderResponse;
import com.platolisto.restaurant_backend.dto.TableFloorConfigResponse;
import com.platolisto.restaurant_backend.dto.TableMergeRequest;
import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import com.platolisto.restaurant_backend.service.OrderService;
import com.platolisto.restaurant_backend.service.RestaurantProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final RestaurantRepository restaurantRepository;

    /** Configuración del piso (total de mesas) para el panel del mesero. */
    @GetMapping("/config")
    public ResponseEntity<TableFloorConfigResponse> getFloorConfig() {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            throw new IllegalStateException("No se pudo identificar el restaurante en el contexto actual.");
        }
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("El restaurante asociado no existe."));
        return ResponseEntity.ok(TableFloorConfigResponse.builder()
                .tableCount(RestaurantProfileService.normalizeStoredTableCount(restaurant.getTableCount()))
                .build());
    }

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
