package com.platolisto.restaurant_backend.repository;

import com.platolisto.restaurant_backend.entity.ProductModifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProductModifierRepository extends JpaRepository<ProductModifier, Long> {

    @Query("""
            SELECT m FROM ProductModifier m
            JOIN FETCH m.group g
            JOIN FETCH g.product p
            WHERE m.uuid IN :uuids
            """)
    List<ProductModifier> findByUuidInWithGroup(@Param("uuids") Collection<UUID> uuids);
}
