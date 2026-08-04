package com.platolisto.restaurant_backend.dto;

import com.platolisto.restaurant_backend.entity.OrderStatus;
import com.platolisto.restaurant_backend.entity.OrderType;
import com.platolisto.restaurant_backend.entity.PaymentMethod;
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
    /** ID interno para display en caja (#101). */
    private Long id;
    private UUID uuid;
    private String customerName;
    private String customerPhone;
    private OrderType orderType;
    private String tableNumber;
    /** Mesas secundarias unidas a esta cuenta (números normalizados). */
    private List<String> linkedTables;
    private UUID staffId;
    private String staffName;
    private String deliveryAddress;
    private OrderStatus status;
    /** Método de cobro al cerrar; null si la cuenta sigue abierta. */
    private PaymentMethod paymentMethod;
    private BigDecimal totalAmount;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<OrderDetailResponse> details;
}
