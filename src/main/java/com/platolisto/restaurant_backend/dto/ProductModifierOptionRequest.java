package com.platolisto.restaurant_backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductModifierOptionRequest {

    /** Si viene, se reutiliza el UUID existente al reemplazar grupos. */
    private UUID uuid;

    @NotBlank(message = "El nombre de la opción es requerido")
    @Size(max = 100)
    private String name;

    @NotNull
    @DecimalMin(value = "0.00", message = "El extra no puede ser negativo")
    private BigDecimal priceDelta;

    @Builder.Default
    private boolean available = true;

    @Builder.Default
    private int displayOrder = 0;
}
