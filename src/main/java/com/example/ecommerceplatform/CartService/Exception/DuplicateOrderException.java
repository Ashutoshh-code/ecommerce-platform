package com.example.ecommerceplatform.CartService.Exception;

import java.util.UUID;

public class DuplicateOrderException extends RuntimeException {

    public DuplicateOrderException(UUID cartId) {
        super("An order already exists for cart " + cartId);
    }
}
