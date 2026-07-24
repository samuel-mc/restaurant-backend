package com.platolisto.restaurant_backend.dto;

import com.platolisto.restaurant_backend.entity.OrderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderRequest {

    @NotBlank(message = "El nombre del cliente es requerido")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    private String customerName;

    @Size(max = 20, message = "El teléfono no puede superar los 20 caracteres")
    private String customerPhone;

    @NotNull(message = "El tipo de pedido es requerido")
    private OrderType orderType;

    @Size(max = 10, message = "El número de mesa no puede superar los 10 caracteres")
    private String tableNumber;

    private String deliveryAddress;

    /**
     * UUID de una orden activa de la mesa (sesión del comensal).
     * Si está presente y la orden sigue abierta, se agrega una nueva ronda.
     */
    private UUID activeOrderUuid;

    @NotEmpty(message = "El pedido debe contener al menos un producto")
    @Valid
    private List<OrderDetailRequest> details;
}
