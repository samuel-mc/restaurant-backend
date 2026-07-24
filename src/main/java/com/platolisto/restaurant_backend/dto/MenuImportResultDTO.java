package com.platolisto.restaurant_backend.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuImportResultDTO {
    private int totalProcesados;
    private int creadosExitosamente;
    @Builder.Default
    private List<MenuImportRowError> errores = new ArrayList<>();
    /** Productos creados en esta importación (para actualizar UI). */
    @Builder.Default
    private List<ProductResponse> products = new ArrayList<>();
    /** Categorías nuevas creadas durante la importación. */
    @Builder.Default
    private List<CategoryResponse> categoriesCreated = new ArrayList<>();
}
