package com.platolisto.restaurant_backend.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductModifierGroupResponse {
    private UUID uuid;
    private String name;
    private int minSelect;
    private int maxSelect;
    private int displayOrder;
    @Builder.Default
    private List<ProductModifierOptionResponse> options = new ArrayList<>();
}
