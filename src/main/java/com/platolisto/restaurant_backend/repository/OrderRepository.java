package com.platolisto.restaurant_backend.repository;

import com.platolisto.restaurant_backend.entity.Order;
import com.platolisto.restaurant_backend.entity.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
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
