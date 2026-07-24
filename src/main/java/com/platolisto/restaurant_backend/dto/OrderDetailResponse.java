package com.platolisto.restaurant_backend.dto;

import com.platolisto.restaurant_backend.entity.OrderItemStatus;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDetailResponse {
    private Long id;
    private UUID productUuid;
    private String productName;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    private String notes;
    private int batchNumber;
    private OrderItemStatus status;
}
