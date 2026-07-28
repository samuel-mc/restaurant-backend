package com.platolisto.restaurant_backend.dto;

import com.platolisto.restaurant_backend.entity.StaffRole;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffMemberUpdateRequest {

    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    private String name;

    private StaffRole role;

    @Pattern(regexp = "^\\d{4}$", message = "El PIN debe ser de exactamente 4 dígitos")
    private String pin;

    private Boolean active;
}
