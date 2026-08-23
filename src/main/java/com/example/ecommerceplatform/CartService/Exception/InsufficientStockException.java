package com.example.ecommerceplatform.CartService.Exception;

import java.util.UUID;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(UUID productId, int requestedQuantity, int availableStock) {
        super("Insufficient stock for product " + productId + ": requested " + requestedQuantity
                + " but only " + availableStock + " available");
    }
}
