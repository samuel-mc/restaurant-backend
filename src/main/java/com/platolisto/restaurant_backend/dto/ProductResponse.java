package com.platolisto.restaurant_backend.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {
    private UUID uuid;
    private String name;
    private String description;
    private BigDecimal price;
    private String imageUrl;
    private boolean isAvailable;
    private Long categoryId;
    private String categoryName;
    private OffsetDateTime createdAt;
    @Builder.Default
    private List<ProductModifierGroupResponse> modifierGroups = new ArrayList<>();
}
