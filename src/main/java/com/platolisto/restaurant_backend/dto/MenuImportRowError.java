package com.platolisto.restaurant_backend.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuImportRowError {
    /** Número de fila en el Excel (1 = encabezado). */
    private int row;
    private String reason;
}
