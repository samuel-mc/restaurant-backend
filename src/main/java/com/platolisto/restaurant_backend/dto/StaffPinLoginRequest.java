package com.platolisto.restaurant_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffPinLoginRequest {

    @NotBlank(message = "El restaurante es requerido")
    private String tenantSlug;

    @NotNull(message = "El empleado es requerido")
    private UUID staffId;

    @NotBlank(message = "El PIN es requerido")
    @Pattern(regexp = "^\\d{4}$", message = "El PIN debe ser de exactamente 4 dígitos")
    private String pin;
}
