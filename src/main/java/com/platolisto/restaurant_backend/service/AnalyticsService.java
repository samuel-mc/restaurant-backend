package com.platolisto.restaurant_backend.service;

import com.platolisto.restaurant_backend.dto.AnalyticsSummaryResponse;
import com.platolisto.restaurant_backend.entity.OrderStatus;
import com.platolisto.restaurant_backend.entity.PaymentMethod;
import com.platolisto.restaurant_backend.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.platolisto.restaurant_backend.dto.DailySummaryResponse;
import com.platolisto.restaurant_backend.dto.ShiftCloseResponse;
import com.platolisto.restaurant_backend.multitenancy.TenantContext;
import com.platolisto.restaurant_backend.repository.OrderFeedbackRepository;
import com.platolisto.restaurant_backend.repository.RestaurantRepository;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final OrderRepository orderRepository;
    private final OrderFeedbackRepository orderFeedbackRepository;
    private final RestaurantRepository restaurantRepository;

    @Transactional(readOnly = true)
    public DailySummaryResponse getDailySummary() {
        ZoneOffset zone = ZoneOffset.UTC;
        OffsetDateTime now = OffsetDateTime.now(zone);
        OffsetDateTime startOfDay = now.toLocalDate().atStartOfDay().atOffset(zone);

        Object[] todayAgg = safeAggregate(startOfDay, now);
        BigDecimal totalSales = toBigDecimal(todayAgg[0]);
        long totalClosedOrders = toLong(todayAgg[1]);
        BigDecimal averageTicket = averageTicket(totalSales, totalClosedOrders);

        Long restaurantId = TenantContext.getCurrentTenant();
        Double avgRatingRaw = restaurantId != null
                ? orderFeedbackRepository.findAverageRatingByRestaurantId(restaurantId)
                : null;
        Double averageRating = avgRatingRaw != null
                ? BigDecimal.valueOf(avgRatingRaw).setScale(1, RoundingMode.HALF_UP).doubleValue()
                : 5.0;

        Map<String, BigDecimal> paymentMethods = sumPaymentMethods(startOfDay, now);

        List<AnalyticsSummaryResponse.TopProduct> topProducts = new ArrayList<>();
        for (Object[] row : orderRepository.findTopProductsByRevenue(
                OrderStatus.CANCELLED,
                startOfDay,
                now,
                PageRequest.of(0, 5)
        )) {
            topProducts.add(AnalyticsSummaryResponse.TopProduct.builder()
                    .name(String.valueOf(row[0]))
                    .quantity(toLong(row[1]))
                    .revenue(toBigDecimal(row[2]))
                    .build());
        }

        return DailySummaryResponse.builder()
                .totalSales(totalSales)
                .totalClosedOrders(totalClosedOrders)
                .averageTicket(averageTicket)
                .averageRating(averageRating)
                .paymentMethods(paymentMethods)
                .topProducts(topProducts)
                .date(now.toLocalDate().toString())
                .build();
    }

    /**
     * Desglose real por método de cobro (órdenes CLOSED del día).
     * Claves en español para el panel / Corte Z: EFECTIVO, TARJETA, TRANSFERENCIA.
     * Órdenes históricas sin método → SIN_REGISTRAR.
     */
    private Map<String, BigDecimal> sumPaymentMethods(OffsetDateTime from, OffsetDateTime to) {
        Map<String, BigDecimal> paymentMethods = new HashMap<>();
        paymentMethods.put("EFECTIVO", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        paymentMethods.put("TARJETA", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        paymentMethods.put("TRANSFERENCIA", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

        for (Object[] row : orderRepository.sumClosedSalesByPaymentMethod(
                OrderStatus.CLOSED, from, to
        )) {
            PaymentMethod method = row[0] instanceof PaymentMethod pm ? pm : null;
            BigDecimal amount = toBigDecimal(row[1]);
            String key = analyticsPaymentKey(method);
            paymentMethods.merge(key, amount, BigDecimal::add);
        }

        // Normaliza escala en buckets fijos.
        for (String key : List.of("EFECTIVO", "TARJETA", "TRANSFERENCIA")) {
            paymentMethods.put(key, paymentMethods.get(key).setScale(2, RoundingMode.HALF_UP));
        }
        if (paymentMethods.containsKey("SIN_REGISTRAR")) {
            paymentMethods.put(
                    "SIN_REGISTRAR",
                    paymentMethods.get("SIN_REGISTRAR").setScale(2, RoundingMode.HALF_UP)
            );
        }
        return paymentMethods;
    }

    private static String analyticsPaymentKey(PaymentMethod method) {
        if (method == null) {
            return "SIN_REGISTRAR";
        }
        return switch (method) {
            case CASH -> "EFECTIVO";
            case CARD -> "TARJETA";
            case TRANSFER -> "TRANSFERENCIA";
        };
    }

    @Transactional
    public ShiftCloseResponse closeShift(String closedBy) {
        DailySummaryResponse daily = getDailySummary();
        Long restaurantId = TenantContext.getCurrentTenant();
        String restaurantName = "PlatoListo Restaurant";
        if (restaurantId != null) {
            restaurantName = restaurantRepository.findById(restaurantId)
                    .map(r -> r.getName())
                    .orElse(restaurantName);
        }

        String corteId = "CORTEZ-" + LocalDate.now().toString().replace("-", "") + "-"
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        return ShiftCloseResponse.builder()
                .id(corteId)
                .restaurantName(restaurantName)
                .closedAt(OffsetDateTime.now().toString())
                .closedBy(closedBy != null && !closedBy.isBlank() ? closedBy : "Manager")
                .totalSales(daily.getTotalSales())
                .totalClosedOrders(daily.getTotalClosedOrders())
                .averageTicket(daily.getAverageTicket())
                .averageRating(daily.getAverageRating())
                .paymentMethods(daily.getPaymentMethods())
                .topProducts(daily.getTopProducts())
                .status("CLOSED_AUDITED")
                .build();
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse getSummary(String period) {
        String normalized = period == null || period.isBlank() ? "month" : period.trim().toLowerCase();
        ZoneOffset zone = ZoneOffset.UTC;
        OffsetDateTime now = OffsetDateTime.now(zone);

        PeriodWindow current = resolveCurrentWindow(normalized, now);
        PeriodWindow previous = resolvePreviousWindow(normalized, current);

        Object[] currentAgg = safeAggregate(current.from(), current.to());
        Object[] previousAgg = safeAggregate(previous.from(), previous.to());

        BigDecimal currentSales = toBigDecimal(currentAgg[0]);
        long currentOrders = toLong(currentAgg[1]);
        BigDecimal previousSales = toBigDecimal(previousAgg[0]);
        long previousOrders = toLong(previousAgg[1]);

        BigDecimal currentTicket = averageTicket(currentSales, currentOrders);
        BigDecimal previousTicket = averageTicket(previousSales, previousOrders);

        AnalyticsSummaryResponse.Kpis kpis = AnalyticsSummaryResponse.Kpis.builder()
                .totalSales(currentSales)
                .totalOrders(currentOrders)
                .averageTicket(currentTicket)
                .salesChangePercent(percentChange(previousSales, currentSales))
                .ordersChangePercent(percentChange(
                        BigDecimal.valueOf(previousOrders),
                        BigDecimal.valueOf(currentOrders)
                ))
                .ticketChangePercent(percentChange(previousTicket, currentTicket))
                .build();

        Map<LocalDate, BigDecimal> byDay = new HashMap<>();
        for (Object[] row : orderRepository.sumSalesByDay(
                OrderStatus.CANCELLED, current.from(), current.to()
        )) {
            LocalDate day = toLocalDate(row[0]);
            if (day != null) {
                byDay.put(day, toBigDecimal(row[1]));
            }
        }

        List<AnalyticsSummaryResponse.SalesPoint> timeline = new ArrayList<>();
        LocalDate cursor = current.from().toLocalDate();
        LocalDate endExclusive = current.to().toLocalDate();
        while (cursor.isBefore(endExclusive)) {
            timeline.add(AnalyticsSummaryResponse.SalesPoint.builder()
                    .date(cursor.toString())
                    .amount(byDay.getOrDefault(cursor, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)))
                    .build());
            cursor = cursor.plusDays(1);
        }

        // Timeline de gráfico: últimos 30 días dentro del periodo (o todo si es week).
        List<AnalyticsSummaryResponse.SalesPoint> chartTimeline = timeline;
        if (timeline.size() > 30) {
            chartTimeline = timeline.subList(timeline.size() - 30, timeline.size());
        }

        List<AnalyticsSummaryResponse.TopProduct> topProducts = new ArrayList<>();
        for (Object[] row : orderRepository.findTopProductsByRevenue(
                OrderStatus.CANCELLED,
                current.from(),
                current.to(),
                PageRequest.of(0, 5)
        )) {
            topProducts.add(AnalyticsSummaryResponse.TopProduct.builder()
                    .name(String.valueOf(row[0]))
                    .quantity(toLong(row[1]))
                    .revenue(toBigDecimal(row[2]))
                    .build());
        }

        return AnalyticsSummaryResponse.builder()
                .kpis(kpis)
                .salesTimeline(chartTimeline)
                .topProducts(topProducts)
                .period(normalized)
                .build();
    }

    private Object[] safeAggregate(OffsetDateTime from, OffsetDateTime to) {
        List<Object[]> rows = orderRepository.aggregateSales(OrderStatus.CANCELLED, from, to);
        if (rows == null || rows.isEmpty()) {
            return new Object[]{BigDecimal.ZERO, 0L};
        }
        Object[] row = rows.get(0);
        if (row == null || row.length < 2) {
            return new Object[]{BigDecimal.ZERO, 0L};
        }
        return row;
    }

    private static PeriodWindow resolveCurrentWindow(String period, OffsetDateTime now) {
        return switch (period) {
            case "week" -> new PeriodWindow(now.minusDays(7), now);
            case "year" -> new PeriodWindow(
                    now.toLocalDate().withDayOfYear(1).atStartOfDay().atOffset(now.getOffset()),
                    now
            );
            default -> {
                // month: mes calendario actual hasta ahora
                OffsetDateTime start = now.toLocalDate().withDayOfMonth(1)
                        .atStartOfDay()
                        .atOffset(now.getOffset());
                yield new PeriodWindow(start, now);
            }
        };
    }

    private static PeriodWindow resolvePreviousWindow(String period, PeriodWindow current) {
        return switch (period) {
            case "week" -> new PeriodWindow(
                    current.from().minusDays(7),
                    current.from()
            );
            case "year" -> {
                OffsetDateTime prevStart = current.from().minusYears(1);
                yield new PeriodWindow(prevStart, current.from());
            }
            default -> {
                OffsetDateTime prevStart = current.from().minusMonths(1);
                yield new PeriodWindow(prevStart, current.from());
            }
        };
    }

    private static BigDecimal averageTicket(BigDecimal sales, long orders) {
        if (orders <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return sales.divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP);
    }

    private static Double percentChange(BigDecimal previous, BigDecimal current) {
        if (previous == null || current == null) return 0.0;
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return current.compareTo(BigDecimal.ZERO) == 0 ? 0.0 : 100.0;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (value instanceof BigDecimal bd) return bd.setScale(2, RoundingMode.HALF_UP);
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private static long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        return 0L;
    }

    private static LocalDate toLocalDate(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate ld) return ld;
        if (value instanceof java.sql.Date sqlDate) return sqlDate.toLocalDate();
        if (value instanceof java.util.Date utilDate) {
            return utilDate.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        }
        if (value instanceof OffsetDateTime odt) return odt.toLocalDate();
        return LocalDate.parse(String.valueOf(value));
    }

    private record PeriodWindow(OffsetDateTime from, OffsetDateTime to) {}
}
