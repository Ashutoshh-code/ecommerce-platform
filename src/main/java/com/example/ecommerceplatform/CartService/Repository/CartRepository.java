package com.example.ecommerceplatform.CartService.Repository;

import com.example.ecommerceplatform.CartService.Entity.CartStatus;
import com.example.ecommerceplatform.CartService.Entity.Carts;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Carts, UUID> {

    Carts findByUserIdAndStatus(UUID userId, CartStatus status);

    List<Carts> findAllByUserId(UUID userId);
}
