package com.platolisto.restaurant_backend.dto;

import com.platolisto.restaurant_backend.entity.StaffRole;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffMemberResponse {

    private UUID id;
    private String name;
    private StaffRole role;
    private boolean active;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
