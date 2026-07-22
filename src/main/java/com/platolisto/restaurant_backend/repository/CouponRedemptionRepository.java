package com.platolisto.restaurant_backend.repository;

import com.platolisto.restaurant_backend.entity.CouponRedemption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {
    boolean existsByCoupon_IdAndRestaurant_Id(Long couponId, Long restaurantId);
}
