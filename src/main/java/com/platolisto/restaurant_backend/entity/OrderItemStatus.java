package com.platolisto.restaurant_backend.entity;

/**
 * Estado individual de una línea de pedido (independiente de la orden).
 */
public enum OrderItemStatus {
    PENDING,
    PREPARING,
    DELIVERED
}
