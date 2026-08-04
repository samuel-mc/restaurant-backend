package com.platolisto.restaurant_backend.dto;

import com.platolisto.restaurant_backend.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CloseOrderRequest {

    /** Método de pago real usado en el cobro. */
    @NotNull(message = "Indica el método de pago (CASH, CARD o TRANSFER).")
    private PaymentMethod paymentMethod;
}
