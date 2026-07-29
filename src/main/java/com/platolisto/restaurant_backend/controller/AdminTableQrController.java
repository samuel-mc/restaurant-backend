package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.TableQrSignRequest;
import com.platolisto.restaurant_backend.dto.TableQrSignResponse;
import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.exception.TenantNotFoundException;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;
import com.platolisto.restaurant_backend.service.TableQrTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/table-qr")
@RequiredArgsConstructor
public class AdminTableQrController {

    private static final int MAX_TABLES = 48;

    private final RestaurantRepository restaurantRepository;
    private final TableQrTokenService tableQrTokenService;

    @PostMapping("/sign")
    public ResponseEntity<TableQrSignResponse> sign(@Valid @RequestBody TableQrSignRequest request) {
        Long restaurantId = TenantContext.getCurrentTenant();
        if (restaurantId == null) {
            throw new IllegalStateException("No se pudo determinar el restaurante actual.");
        }
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new TenantNotFoundException("Restaurante no encontrado."));

        Set<String> unique = new LinkedHashSet<>();
        for (String raw : request.getTableNumbers()) {
            String table = TableQrTokenService.normalizeTable(raw);
            if (table != null) {
                unique.add(table);
            }
        }
        if (unique.isEmpty()) {
            throw new IllegalArgumentException("Indica al menos un número de mesa válido.");
        }
        if (unique.size() > MAX_TABLES) {
            throw new IllegalArgumentException("Máximo " + MAX_TABLES + " mesas por solicitud.");
        }

        List<TableQrSignResponse.TableQrLink> links = new ArrayList<>();
        for (String table : unique) {
            TableQrTokenService.SignedTableToken signed =
                    tableQrTokenService.signWithExpiry(restaurant, table);
            links.add(TableQrSignResponse.TableQrLink.builder()
                    .tableNumber(table)
                    .tableToken(signed.token())
                    .expiresAt(signed.expiresAt().toString())
                    .build());
        }
        return ResponseEntity.ok(TableQrSignResponse.builder().links(links).build());
    }
}
