package com.platolisto.restaurant_backend.dto;

import com.platolisto.restaurant_backend.entity.StaffRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffMemberRequest {

    @NotBlank(message = "El nombre es requerido")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    private String name;

    @NotNull(message = "El rol es requerido")
    private StaffRole role;

    @NotBlank(message = "El PIN es requerido")
    @Pattern(regexp = "^\\d{4}$", message = "El PIN debe ser de exactamente 4 dígitos")
    private String pin;
}
