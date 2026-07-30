package com.platolisto.restaurant_backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductModifierGroupRequest {

    private UUID uuid;

    @NotBlank(message = "El nombre del grupo es requerido")
    @Size(max = 100)
    private String name;

    @Min(0)
    @Builder.Default
    private int minSelect = 0;

    @Min(1)
    @Builder.Default
    private int maxSelect = 1;

    @Builder.Default
    private int displayOrder = 0;

    @NotEmpty(message = "Cada grupo necesita al menos una opción")
    @Valid
    @Builder.Default
    private List<ProductModifierOptionRequest> options = new ArrayList<>();
}
