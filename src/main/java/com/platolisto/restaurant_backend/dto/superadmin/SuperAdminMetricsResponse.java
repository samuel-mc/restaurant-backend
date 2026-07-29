package com.platolisto.restaurant_backend.dto.superadmin;

import lombok.*;

import java.time.OffsetDateTime;
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

    /**
     * MRR estimado en pesos MXN enteros (no centavos).
     * Calculado solo en servidor: Pro activos × list price vigente.
     * Elegibilidad: {@code isActive && plan == PRO} (incluye PENDING_PAYMENT).
     */
    private long estimatedMrr;

    /** Código ISO 4217 de la moneda de {@link #estimatedMrr} (p.ej. {@code MXN}). */
    private String estimatedMrrCurrency;

    /** Momento UTC en que se calculó este MRR (ISO-8601). */
    private OffsetDateTime estimatedMrrAsOf;

    /**
     * Periodo del estimado. Valor estable: {@code calendar_month}.
     */
    private String estimatedMrrPeriod;

    /**
     * Método de cálculo. Valor estable: {@code pro_active_list_price}.
     */
    private String estimatedMrrMethod;

    /** Frase corta lista para UI (≤80 chars). */
    private String estimatedMrrLabelEs;

    /** Aviso honestamente “estimado / no cerrado”. */
    private String estimatedMrrDisclaimerEs;

    /**
     * List price Pro vigente en servidor (MXN/mes), si el método usa precio unitario.
     */
    private Long estimatedMrrUnitPriceMxn;

    /** Conteo de Pro activos usado en el cálculo. */
    private Long estimatedMrrProActiveCount;

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
