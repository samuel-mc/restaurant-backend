package com.platolisto.restaurant_backend.dto.superadmin;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImpersonateResponse {
    private String token;
    private String tenantSlug;
    private String restaurantName;
    private String loginPath;
    /** Segundos hasta que expire el JWT de soporte. */
    private long expiresInSeconds;
    /** Email del SuperAdmin que inició la sesión. */
    private String impersonatedBy;
    /** Email del OWNER/ADMIN impersonado. */
    private String impersonatedAs;
}
