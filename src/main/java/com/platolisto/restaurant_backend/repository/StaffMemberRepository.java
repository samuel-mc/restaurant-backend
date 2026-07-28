package com.platolisto.restaurant_backend.repository;

import com.platolisto.restaurant_backend.entity.StaffMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffMemberRepository extends JpaRepository<StaffMember, UUID> {

    List<StaffMember> findByRestaurantIdOrderByNameAsc(Long restaurantId);

    List<StaffMember> findByRestaurantIdAndActiveTrue(Long restaurantId);

    Optional<StaffMember> findByIdAndRestaurantId(UUID id, Long restaurantId);

    long countByRestaurantIdAndActiveTrue(Long restaurantId);
}
