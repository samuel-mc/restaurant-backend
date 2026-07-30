package com.platolisto.restaurant_backend.dto;

import com.platolisto.restaurant_backend.entity.TableCallType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class TableCallRequest {

    @NotBlank(message = "El número de mesa es requerido.")
    @Size(max = 32, message = "Número de mesa inválido.")
    private String tableNumber;

    /** Token firmado del QR ({@code ?t=}). Obligatorio. */
    @NotBlank(message = "Escanea el código QR de tu mesa para pedir ayuda.")
    @Size(max = 256, message = "Token de mesa inválido.")
    private String tableToken;

    @NotNull(message = "Indica el tipo de llamada.")
    private TableCallType callType;

    /**
     * Forma de pago sugerida al pedir la cuenta:
     * {@code CASH} | {@code CARD} | {@code TRANSFER}.
     */
    @Size(max = 20, message = "Forma de pago inválida.")
    private String paymentMethod;

    @Size(max = 200, message = "La nota no puede superar 200 caracteres.")
    private String note;
}
