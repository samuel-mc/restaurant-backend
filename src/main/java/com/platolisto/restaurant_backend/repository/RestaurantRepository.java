package com.platolisto.restaurant_backend.repository;

import com.platolisto.restaurant_backend.entity.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {
    Optional<Restaurant> findBySubdomainAndIsActiveTrue(String subdomain);
    Optional<Restaurant> findByCustomDomainAndIsActiveTrue(String customDomain);
}
