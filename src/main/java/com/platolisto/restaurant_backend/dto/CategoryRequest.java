package com.platolisto.restaurant_backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryRequest {

    @NotBlank(message = "El nombre de la categoría es requerido")
    @Size(max = 50, message = "El nombre no puede superar los 50 caracteres")
    private String name;

    @Min(value = 0, message = "El orden de visualización no puede ser negativo")
    private int displayOrder;
}
