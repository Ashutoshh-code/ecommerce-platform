package com.example.ecommerceplatform.CartService.Exception;

public class OrderCreationFailedException extends RuntimeException {

    public OrderCreationFailedException(String orderServiceMessage) {
        super("Order Service rejected the order: " + orderServiceMessage);
    }
}
