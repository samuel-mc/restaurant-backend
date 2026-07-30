package com.platolisto.restaurant_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TableMergeRequest {

    /** Opcional: el tenant se resuelve por contexto/JWT; se acepta por compatibilidad de contrato. */
    @Size(max = 80)
    private String tenantSlug;

    @NotBlank(message = "La mesa principal es requerida")
    @Size(max = 20, message = "Mesa principal inválida")
    private String primaryTable;

    @NotEmpty(message = "Debes indicar al menos una mesa a vincular")
    private List<@NotBlank @Size(max = 20) String> secondaryTables;
}
