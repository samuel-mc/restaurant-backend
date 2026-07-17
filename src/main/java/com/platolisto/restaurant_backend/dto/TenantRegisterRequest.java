package com.platolisto.restaurant_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantRegisterRequest {

    @NotBlank(message = "El nombre del restaurante es requerido")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    private String restaurantName;

    @NotBlank(message = "El subdominio es requerido")
    @Size(min = 2, max = 50, message = "El subdominio debe tener entre 2 y 50 caracteres")
    @Pattern(
            regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
            message = "El subdominio solo puede contener minúsculas, números y guiones"
    )
    private String tenantSlug;

    @NotBlank(message = "El email del propietario es requerido")
    @Email(message = "El email no tiene un formato válido")
    @Size(max = 100)
    private String ownerEmail;

    @NotBlank(message = "El nombre del propietario es requerido")
    @Size(max = 100)
    private String ownerName;

    @NotBlank(message = "La contraseña es requerida")
    @Size(min = 8, max = 100, message = "La contraseña debe tener al menos 8 caracteres")
    private String ownerPassword;
}
