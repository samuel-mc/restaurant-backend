package com.platolisto.restaurant_backend.repository;

import com.platolisto.restaurant_backend.entity.Order;
import com.platolisto.restaurant_backend.entity.OrderStatus;
import com.platolisto.restaurant_backend.entity.OrderType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
    Optional<Order> findByUuid(UUID uuid);

    /**
     * Pedidos activos del tenant actual (filtro Hibernate) con líneas y productos.
     */
    @Query("""
            SELECT DISTINCT o FROM Order o
            LEFT JOIN FETCH o.details d
            LEFT JOIN FETCH d.product
            WHERE o.status IN :statuses
            """)
    List<Order> findActiveWithDetails(@Param("statuses") Collection<OrderStatus> statuses);

    /**
     * Órdenes abiertas de una mesa (IN_TABLE) para fusionar adiciones en el mismo ticket.
     * "Abierta" = PENDING | ACCEPTED | IN_KITCHEN (equivalente a OPEN del dominio).
     */
    @Query("""
            SELECT DISTINCT o FROM Order o
            LEFT JOIN FETCH o.details d
            LEFT JOIN FETCH d.product
            WHERE o.orderType = :orderType
              AND o.status IN :statuses
              AND LOWER(TRIM(o.tableNumber)) = LOWER(TRIM(:tableNumber))
            """)
    List<Order> findOpenInTableWithDetails(
            @Param("tableNumber") String tableNumber,
            @Param("orderType") OrderType orderType,
            @Param("statuses") Collection<OrderStatus> statuses
    );

    @Query("""
            SELECT DISTINCT o FROM Order o
            LEFT JOIN FETCH o.details d
            LEFT JOIN FETCH d.product
            WHERE o.uuid = :uuid
            """)
    Optional<Order> findByUuidWithDetails(@Param("uuid") UUID uuid);

    /** Hidrata líneas + producto tras una consulta paginada (evita JOIN FETCH + Page). */
    @Query("""
            SELECT DISTINCT o FROM Order o
            LEFT JOIN FETCH o.details d
            LEFT JOIN FETCH d.product
            WHERE o.id IN :ids
            """)
    List<Order> findByIdInWithDetails(@Param("ids") Collection<Long> ids);

    @Query("""
            SELECT COALESCE(SUM(o.totalAmount), 0), COUNT(o)
            FROM Order o
            WHERE o.status <> :cancelled
              AND o.createdAt >= :from
              AND o.createdAt < :to
            """)
    List<Object[]> aggregateSales(
            @Param("cancelled") OrderStatus cancelled,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );

    @Query("""
            SELECT FUNCTION('DATE', o.createdAt), COALESCE(SUM(o.totalAmount), 0)
            FROM Order o
            WHERE o.status <> :cancelled
              AND o.createdAt >= :from
              AND o.createdAt < :to
            GROUP BY FUNCTION('DATE', o.createdAt)
            ORDER BY FUNCTION('DATE', o.createdAt)
            """)
    List<Object[]> sumSalesByDay(
            @Param("cancelled") OrderStatus cancelled,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );

    @Query("""
            SELECT p.name, COALESCE(SUM(d.quantity), 0), COALESCE(SUM(d.unitPrice * d.quantity), 0)
            FROM Order o
            JOIN o.details d
            JOIN d.product p
            WHERE o.status <> :cancelled
              AND o.createdAt >= :from
              AND o.createdAt < :to
            GROUP BY p.id, p.name
            ORDER BY COALESCE(SUM(d.unitPrice * d.quantity), 0) DESC
            """)
    List<Object[]> findTopProductsByRevenue(
            @Param("cancelled") OrderStatus cancelled,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable
    );
}
