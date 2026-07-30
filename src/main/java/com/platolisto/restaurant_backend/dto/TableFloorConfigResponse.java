package com.platolisto.restaurant_backend.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TableFloorConfigResponse {
    /** Total de mesas del salón (1..N). */
    private int tableCount;
}
