package com.example.ecommerceplatform.CartService.Exception;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String productId, int requestedQuantity, int availableStock) {
        super("Insufficient stock for product " + productId + ": requested " + requestedQuantity
                + " but only " + availableStock + " available");
    }
}
