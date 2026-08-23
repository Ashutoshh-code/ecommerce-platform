package com.example.ecommerceplatform.CartService.Exception;

import java.util.UUID;

public class CartNotFoundException extends RuntimeException {

    public CartNotFoundException(UUID userId) {
        super("No active cart found for userId: " + userId);
    }
}
