package com.platolisto.restaurant_backend.controller;

import com.platolisto.restaurant_backend.dto.ProductResponse;
import com.platolisto.restaurant_backend.dto.ReplaceProductModifiersRequest;
import com.platolisto.restaurant_backend.service.ProductModifierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/products/{uuid}/modifier-groups")
@RequiredArgsConstructor
public class ProductModifierController {

    private final ProductModifierService productModifierService;

    /** Reemplaza por completo los grupos/opciones del platillo. */
    @PutMapping
    public ResponseEntity<ProductResponse> replaceGroups(
            @PathVariable UUID uuid,
            @Valid @RequestBody ReplaceProductModifiersRequest request
    ) {
        return ResponseEntity.ok(productModifierService.replaceGroups(uuid, request));
    }
}
