package com.platolisto.restaurant_backend.dto;

import lombok.*;

/**
 * Respuesta de consulta de sesión activa por mesa.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActiveSessionResponse {
    private boolean hasActiveOrder;
    /** Presente solo si {@code hasActiveOrder} es true. */
    private OrderResponse order;
}
