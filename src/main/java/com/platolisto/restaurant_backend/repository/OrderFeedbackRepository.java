package com.platolisto.restaurant_backend.repository;

import com.platolisto.restaurant_backend.entity.FeedbackStatus;
import com.platolisto.restaurant_backend.entity.OrderFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderFeedbackRepository extends JpaRepository<OrderFeedback, Long> {

    boolean existsByOrderUuid(UUID orderUuid);

    Optional<OrderFeedback> findByOrderUuid(UUID orderUuid);

    Optional<OrderFeedback> findByIdAndRestaurant_Id(Long id, Long restaurantId);

    @Query("""
            SELECT f FROM OrderFeedback f
            WHERE f.restaurant.id = :restaurantId
              AND (:status IS NULL OR f.status = :status)
              AND (:urgentOnly = FALSE OR f.urgent = TRUE)
            ORDER BY f.urgent DESC, f.createdAt DESC
            """)
    List<OrderFeedback> findForAdmin(
            @Param("restaurantId") Long restaurantId,
            @Param("status") FeedbackStatus status,
            @Param("urgentOnly") boolean urgentOnly
    );

    long countByRestaurant_IdAndStatus(Long restaurantId, FeedbackStatus status);

    long countByRestaurant_IdAndStatusAndUrgentTrue(Long restaurantId, FeedbackStatus status);

    @Query("SELECT AVG(f.stars) FROM OrderFeedback f WHERE f.restaurant.id = :restaurantId")
    Double findAverageRatingByRestaurantId(@Param("restaurantId") Long restaurantId);
}
