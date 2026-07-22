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
}
