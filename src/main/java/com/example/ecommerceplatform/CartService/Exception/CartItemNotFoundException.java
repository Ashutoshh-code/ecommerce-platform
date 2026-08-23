package com.example.ecommerceplatform.CartService.Exception;

import java.util.UUID;

public class CartItemNotFoundException extends RuntimeException {

    public CartItemNotFoundException(UUID cartItemId) {
        super("Cart item not found: " + cartItemId);
    }
}
