package com.platolisto.restaurant_backend.dto;

import com.platolisto.restaurant_backend.entity.StaffRole;
import com.platolisto.restaurant_backend.security.StaffPinPolicy;
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

    @Pattern(regexp = StaffPinPolicy.PIN_REGEXP, message = StaffPinPolicy.PIN_MESSAGE)
    private String pin;

    private Boolean active;
}
