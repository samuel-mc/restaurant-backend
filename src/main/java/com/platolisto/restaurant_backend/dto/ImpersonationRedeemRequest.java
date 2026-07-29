package com.platolisto.restaurant_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ImpersonationRedeemRequest {

    @NotBlank(message = "El código de impersonación es requerido")
    @Size(max = 128, message = "Código de impersonación inválido")
    private String code;
}
