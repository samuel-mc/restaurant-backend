package com.platolisto.restaurant_backend.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsSummaryResponse {

    private Kpis kpis;
    private List<SalesPoint> salesTimeline;
    private List<TopProduct> topProducts;
    private String period;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Kpis {
        private BigDecimal totalSales;
        private long totalOrders;
        private BigDecimal averageTicket;
        /** Variación % vs periodo anterior (ej. mes previo). */
        private Double salesChangePercent;
        private Double ordersChangePercent;
        private Double ticketChangePercent;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SalesPoint {
        /** ISO date (yyyy-MM-dd). */
        private String date;
        private BigDecimal amount;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TopProduct {
        private String name;
        private long quantity;
        private BigDecimal revenue;
    }
}
