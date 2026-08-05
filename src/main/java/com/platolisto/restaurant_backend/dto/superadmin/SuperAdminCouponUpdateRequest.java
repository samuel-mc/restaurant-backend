package com.platolisto.restaurant_backend.dto.superadmin;

import com.platolisto.restaurant_backend.entity.SubscriptionPlan;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuperAdminCouponUpdateRequest {

    @Size(max = 255)
    private String description;

    private SubscriptionPlan grantsPlan;

    /** Null = no cambiar; usar clearMaxRedemptions para ilimitado. */
    @Positive(message = "El máximo de usos debe ser positivo")
    private Integer maxRedemptions;

    /** Si true, deja maxRedemptions en null (ilimitado). */
    private Boolean clearMaxRedemptions;

    /** ISO-8601; cadena vacía limpia la expiración. */
    private String expiresAt;

    /** Null = no cambiar; usar clearGrantDurationDays para quitar. */
    @Positive(message = "La duración del grant debe ser positiva")
    @Max(value = 3650, message = "La duración del grant no puede superar 3650 días")
    private Integer grantDurationDays;

    /** Si true, deja grantDurationDays en null. */
    private Boolean clearGrantDurationDays;

    private Boolean active;
}
