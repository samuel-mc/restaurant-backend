package com.platolisto.restaurant_backend.dto;

import com.platolisto.restaurant_backend.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusRequest {

    @NotNull(message = "El nuevo estado del pedido es requerido")
    private OrderStatus status;
}
