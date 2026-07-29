package com.platolisto.restaurant_backend.dto;

import com.platolisto.restaurant_backend.security.StaffPinPolicy;
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
    @Pattern(regexp = StaffPinPolicy.PIN_REGEXP, message = StaffPinPolicy.PIN_MESSAGE)
    private String pin;
}
