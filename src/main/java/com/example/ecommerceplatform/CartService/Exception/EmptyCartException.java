package com.example.ecommerceplatform.CartService.Exception;

import java.util.UUID;

public class EmptyCartException extends RuntimeException {

    public EmptyCartException(UUID userId) {
        super("Cannot checkout: cart for userId " + userId + " has no items");
    }
}
