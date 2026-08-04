package com.platolisto.restaurant_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuUpdateEvent {
    private String type;
    private String productId;

    @JsonProperty("isAvailable")
    private boolean isAvailable;

    private String categoryId;
}
