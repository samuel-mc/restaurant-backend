package com.platolisto.restaurant_backend.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantRegisterResponse {
    private Long restaurantId;
    private String restaurantName;
    private String tenantSlug;
    private String ownerEmail;
    private String loginPath;
}
