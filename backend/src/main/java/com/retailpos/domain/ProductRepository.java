package com.retailpos.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByFlavourIgnoreCase(String flavour);

    Optional<Product> findByNameIgnoreCase(String name);

    List<Product> findByIsActiveTrueOrderByIdAsc();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Product p SET p.orderCount = COALESCE(p.orderCount, 0) + :quantity WHERE p.id = :productId")
    int incrementOrderCount(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Product p SET p.orderCount = 0 WHERE p.id = :productId")
    int resetOrderCount(@Param("productId") Long productId);
}
