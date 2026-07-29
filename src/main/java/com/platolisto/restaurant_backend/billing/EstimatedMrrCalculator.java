package com.platolisto.restaurant_backend.billing;

import com.platolisto.restaurant_backend.entity.Restaurant;
import com.platolisto.restaurant_backend.entity.SubscriptionPlan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Única fuente de verdad del MRR estimado SuperAdmin.
 * Método: {@code pro_active_list_price} — tenants activos con plan PRO
 * × list price vigente en config (incluye {@code PENDING_PAYMENT}).
 */
@Component
public class EstimatedMrrCalculator {

    public static final String CURRENCY = "MXN";
    public static final String PERIOD = "calendar_month";
    public static final String METHOD = "pro_active_list_price";
    public static final String LABEL_ES = "Pro activos × list price vigente";
    public static final String DISCLAIMER_ES =
            "Estimación operativa; no es facturación cerrada.";

    private final long proListPriceMxn;

    public EstimatedMrrCalculator(
            @Value("${application.billing.pro-list-price-mxn:1000}") long proListPriceMxn
    ) {
        this.proListPriceMxn = proListPriceMxn;
    }

    public EstimatedMrr estimate(List<Restaurant> restaurants) {
        long proActiveCount = restaurants.stream()
                .filter(Restaurant::isActive)
                .filter(r -> r.getPlan() == SubscriptionPlan.PRO)
                .count();
        long amount = proActiveCount * proListPriceMxn;
        return new EstimatedMrr(
                amount,
                CURRENCY,
                OffsetDateTime.now(ZoneOffset.UTC),
                PERIOD,
                METHOD,
                LABEL_ES,
                DISCLAIMER_ES,
                proListPriceMxn,
                proActiveCount
        );
    }

    public long getProListPriceMxn() {
        return proListPriceMxn;
    }

    /**
     * Resultado del cálculo MRR estimado (pesos enteros, no centavos).
     */
    public record EstimatedMrr(
            long amount,
            String currency,
            OffsetDateTime asOf,
            String period,
            String method,
            String labelEs,
            String disclaimerEs,
            long unitPriceMxn,
            long proActiveCount
    ) {
    }
}
