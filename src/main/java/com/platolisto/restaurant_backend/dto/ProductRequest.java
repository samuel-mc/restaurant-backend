package com.platolisto.restaurant_backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRequest {

    @NotBlank(message = "El nombre del producto es requerido")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    private String name;

    private String description;

    @NotNull(message = "El precio es requerido")
    @DecimalMin(value = "0.00", message = "El precio no puede ser negativo")
    private BigDecimal price;

    private String imageUrl;

    @NotNull(message = "El id de categoría es requerido")
    private Long categoryId;
}
