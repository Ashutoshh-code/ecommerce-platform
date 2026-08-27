package com.example.ecommerceplatform.CartService.Exception;

public class ProductUnavailableException extends RuntimeException {

    public ProductUnavailableException(String variantId) {
        super("No merchant listing found for variant " + variantId);
    }
}
