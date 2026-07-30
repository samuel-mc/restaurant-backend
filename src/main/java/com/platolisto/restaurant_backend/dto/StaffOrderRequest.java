package com.platolisto.restaurant_backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;
import java.util.UUID;

/**
 * Comanda tomada por mesero/admin desde el panel (sin token QR de mesa).
 * El {@code staffId} se toma del SecurityContext, no del body.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffOrderRequest {

    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    private String customerName;

    @NotBlank(message = "El número de mesa es requerido")
    @Size(max = 20, message = "El número de mesa no puede superar los 20 caracteres")
    private String tableNumber;

    /** UUID de orden activa para adición (misma cuenta / mesas unidas). */
    private UUID activeOrderUuid;

    @NotEmpty(message = "El pedido debe contener al menos un producto")
    @Valid
    private List<OrderDetailRequest> details;
}
