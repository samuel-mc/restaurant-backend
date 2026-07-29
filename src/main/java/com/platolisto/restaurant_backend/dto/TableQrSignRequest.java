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
public class TableQrSignRequest {

    @NotEmpty(message = "Indica al menos un número de mesa")
    private List<@NotBlank @Size(max = 10) String> tableNumbers;
}
