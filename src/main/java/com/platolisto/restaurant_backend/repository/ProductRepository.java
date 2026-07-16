package com.platolisto.restaurant_backend.repository;

import com.platolisto.restaurant_backend.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByUuid(UUID uuid);
    List<Product> findByIsAvailableTrue();
}
