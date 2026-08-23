package com.example.ecommerceplatform.CartService.Exception;

import java.util.UUID;

public class ProductUnavailableException extends RuntimeException {

    public ProductUnavailableException(UUID productId) {
        super("Product " + productId + " is not available");
    }
}
