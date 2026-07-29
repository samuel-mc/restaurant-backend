package com.platolisto.restaurant_backend.dto.superadmin;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuperAdminCouponResponse {
    private Long id;
    private String code;
    private String description;
    private String grantsPlan;
    private Integer maxRedemptions;
    private int redemptionCount;
    private boolean active;
    private String expiresAt;
    private String createdAt;
}
