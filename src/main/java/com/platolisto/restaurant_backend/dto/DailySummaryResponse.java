package com.platolisto.restaurant_backend.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailySummaryResponse {

    private BigDecimal totalSales;
    private long totalClosedOrders;
    private BigDecimal averageTicket;
    private Double averageRating;
    private Map<String, BigDecimal> paymentMethods;
    private List<AnalyticsSummaryResponse.TopProduct> topProducts;
    private String date;
}
