package com.platolisto.restaurant_backend.dto;

/**
 * Filtros de conveniencia para el listado admin de pedidos/cuentas.
 */
public enum AdminOrderListFilter {
    /** Todos los pedidos del tenant. */
    ALL,
    /** Cuentas abiertas en mesa (PENDING → DELIVERED, IN_TABLE). */
    OPEN,
    /** Cuentas cobradas / cerradas. */
    CLOSED,
    /** Pedidos para llevar (PICKUP), cualquier estado. */
    PICKUP
}
