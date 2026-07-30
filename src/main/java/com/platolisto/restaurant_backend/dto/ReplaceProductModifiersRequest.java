package com.platolisto.restaurant_backend.dto;

import jakarta.validation.Valid;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReplaceProductModifiersRequest {

    /** Lista completa que reemplaza los grupos del platillo (puede ser vacía). */
    @Valid
    @Builder.Default
    private List<ProductModifierGroupRequest> groups = new ArrayList<>();
}
