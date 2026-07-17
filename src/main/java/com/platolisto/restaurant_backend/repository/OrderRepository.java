package com.platolisto.restaurant_backend.repository;

import com.platolisto.restaurant_backend.entity.Order;
import com.platolisto.restaurant_backend.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
