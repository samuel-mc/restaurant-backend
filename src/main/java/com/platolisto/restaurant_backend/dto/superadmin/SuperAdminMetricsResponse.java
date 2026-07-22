package com.platolisto.restaurant_backend.dto.superadmin;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuperAdminMetricsResponse {
    private long totalTenants;
    private long activeTenants;
    private long suspendedTenants;
    private long proTenants;
    private long basicTenants;
    /** MRR estimado en MXN (Pro activos × 999). */
    private long estimatedMrr;
    /** Suspendidos / total × 100. */
    private double churnRate;
    private List<RegistrationPoint> registrationGrowth;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RegistrationPoint {
        private String month;
        private long count;
    }
}
