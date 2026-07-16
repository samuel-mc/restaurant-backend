package com.platolisto.restaurant_backend.dto;

import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryResponse {
    private Long id;
    private String name;
    private int displayOrder;
    private OffsetDateTime createdAt;
}
