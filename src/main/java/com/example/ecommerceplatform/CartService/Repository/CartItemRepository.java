package com.example.ecommerceplatform.CartService.Repository;

import com.example.ecommerceplatform.CartService.Entity.CartItems;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItems, UUID> {

    List<CartItems> findByCartCartId(UUID cartId);

    @Query("""
            SELECT ci FROM CartItems ci
            WHERE ci.cart.cartId = :cartId
              AND ci.productId = :productId
              AND ci.merchantId = :merchantId
              AND (:variantId IS NULL AND ci.variantId IS NULL OR ci.variantId = :variantId)
            """)
    Optional<CartItems> findMatchingLine(
            @Param("cartId") UUID cartId,
            @Param("productId") UUID productId,
            @Param("variantId") UUID variantId,
            @Param("merchantId") UUID merchantId
    );

    long countByCartCartId(UUID cartId);

    void deleteByCartCartId(UUID cartId);
}
