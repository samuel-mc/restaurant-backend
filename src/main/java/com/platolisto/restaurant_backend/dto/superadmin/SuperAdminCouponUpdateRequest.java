package com.platolisto.restaurant_backend.dto.superadmin;

import com.platolisto.restaurant_backend.entity.SubscriptionPlan;
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

    private Boolean active;
}
