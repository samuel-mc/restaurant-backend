package com.platolisto.restaurant_backend.dto;

import com.platolisto.restaurant_backend.entity.StaffRole;
import lombok.*;

import java.util.UUID;

/**
 * Directorio público de personal activo (sin PIN ni datos sensibles).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicStaffMemberResponse {

    private UUID id;
    private String name;
    private StaffRole role;
}
