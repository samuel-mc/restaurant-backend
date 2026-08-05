package com.platolisto.restaurant_backend.dto.superadmin;

import com.platolisto.restaurant_backend.entity.PaymentStatus;
import com.platolisto.restaurant_backend.entity.SubscriptionPlan;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuperAdminTenantSubscriptionRequest {

    @NotNull(message = "El plan es requerido")
    private SubscriptionPlan plan;

    @NotNull(message = "El estado de pago es requerido")
    private PaymentStatus paymentStatus;

    /**
     * ISO-8601 opcional. Null = no cambiar.
     * Cadena vacía = limpiar período de renovación.
     */
    private String currentPeriodEnd;
}
