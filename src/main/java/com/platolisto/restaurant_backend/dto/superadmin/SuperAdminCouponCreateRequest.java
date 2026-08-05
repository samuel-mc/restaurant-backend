package com.platolisto.restaurant_backend.dto.superadmin;

import com.platolisto.restaurant_backend.entity.SubscriptionPlan;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuperAdminCouponCreateRequest {

    @NotBlank(message = "El código es requerido")
    @Size(min = 3, max = 40, message = "El código debe tener entre 3 y 40 caracteres")
    @Pattern(
            regexp = "^[A-Za-z0-9_-]+$",
            message = "El código solo puede contener letras, números, guiones y guiones bajos"
    )
    private String code;

    @Size(max = 255)
    private String description;

    /** Default PRO si viene null. */
    private SubscriptionPlan grantsPlan;

    /** Null = usos ilimitados. */
    @Positive(message = "El máximo de usos debe ser positivo")
    private Integer maxRedemptions;

    /** ISO-8601 opcional (ej. 2026-12-31T23:59:59Z). */
    private String expiresAt;

    /** Días de entitlement al canjear. Null = no fija período en el tenant. */
    @Positive(message = "La duración del grant debe ser positiva")
    @Max(value = 3650, message = "La duración del grant no puede superar 3650 días")
    private Integer grantDurationDays;
}
