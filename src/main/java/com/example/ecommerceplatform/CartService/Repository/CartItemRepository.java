package com.example.ecommerceplatform.CartService.Repository;

import com.example.ecommerceplatform.CartService.Entity.CartItems;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItems, UUID> {

    List<CartItems> findByCartCartId(UUID cartId);

    Optional<CartItems> findByCartCartIdAndProductIdAndVariantIdAndMerchantId(
            UUID cartId,
            String productId,
            String variantId,
            String merchantId
    );

    long countByCartCartId(UUID cartId);

    void deleteByCartCartId(UUID cartId);
}
