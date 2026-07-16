package com.platolisto.restaurant_backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDetailRequest {

    @NotNull(message = "El UUID del producto es requerido")
    private UUID productUuid;

    @Min(value = 1, message = "La cantidad mínima debe ser 1")
    private int quantity;

    private String notes;
}
