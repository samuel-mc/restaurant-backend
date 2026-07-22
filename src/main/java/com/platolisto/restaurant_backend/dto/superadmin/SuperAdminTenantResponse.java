package com.platolisto.restaurant_backend.dto.superadmin;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuperAdminTenantResponse {
    private Long id;
    private String name;
    private String subdomain;
    private String plan;
    private String paymentStatus;
    private boolean active;
    private boolean websitePublished;
    private String createdAt;
    private String updatedAt;
}
