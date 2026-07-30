package com.platolisto.restaurant_backend.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDetailModifierResponse {
    private UUID modifierUuid;
    private String name;
    private BigDecimal priceDelta;
}
