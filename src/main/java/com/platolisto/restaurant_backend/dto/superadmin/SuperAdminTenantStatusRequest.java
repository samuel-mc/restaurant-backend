package com.platolisto.restaurant_backend.dto.superadmin;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuperAdminTenantStatusRequest {
    @NotNull(message = "El estado activo es requerido")
    private Boolean active;
}
