package com.example.ecommerceplatform.CartService.Exception;

public class ProductUnavailableException extends RuntimeException {

    public ProductUnavailableException(String productId) {
        super("Product " + productId + " is not available");
    }
}
