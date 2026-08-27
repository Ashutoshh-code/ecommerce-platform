package com.example.ecommerceplatform.CartService.Exception;

import java.util.UUID;

public class AddressNotFoundException extends RuntimeException {

    public AddressNotFoundException(UUID userId) {
        super("No default address found for user " + userId);
    }
}
