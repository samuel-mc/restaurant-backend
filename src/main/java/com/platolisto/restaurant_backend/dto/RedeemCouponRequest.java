package com.platolisto.restaurant_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RedeemCouponRequest {

    @NotBlank(message = "El código del cupón es requerido")
    @Size(max = 40, message = "El código no puede superar 40 caracteres")
    private String code;
}
