package com.platolisto.restaurant_backend.dto;

import com.platolisto.restaurant_backend.entity.OrderStatus;
import com.platolisto.restaurant_backend.entity.OrderType;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {
    private UUID uuid;
    private String customerName;
    private String customerPhone;
    private OrderType orderType;
    private String tableNumber;
    private String deliveryAddress;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private OffsetDateTime createdAt;
    private List<OrderDetailResponse> details;
}
