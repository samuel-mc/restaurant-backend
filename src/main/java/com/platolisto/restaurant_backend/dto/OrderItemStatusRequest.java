package com.platolisto.restaurant_backend.dto;

import com.platolisto.restaurant_backend.entity.OrderItemStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemStatusRequest {
    @NotNull(message = "El estado del ítem es requerido")
    private OrderItemStatus status;
}
