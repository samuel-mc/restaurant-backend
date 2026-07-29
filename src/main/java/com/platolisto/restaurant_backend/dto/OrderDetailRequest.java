package com.platolisto.restaurant_backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    /** Anotaciones del comensal para cocina (ej. sin cebolla). */
    @Size(max = 255, message = "Las notas no pueden superar 255 caracteres")
    private String notes;
}
