package com.platolisto.restaurant_backend.repository;

import com.platolisto.restaurant_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByRestaurantIdAndEmail(Long restaurantId, String email);
}
