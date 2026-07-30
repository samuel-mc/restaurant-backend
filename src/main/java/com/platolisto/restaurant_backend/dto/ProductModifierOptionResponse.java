package com.platolisto.restaurant_backend.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductModifierOptionResponse {
    private UUID uuid;
    private String name;
    private BigDecimal priceDelta;
    private boolean available;
    private int displayOrder;
}
