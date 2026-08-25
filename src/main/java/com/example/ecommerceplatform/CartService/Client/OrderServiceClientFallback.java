package com.example.ecommerceplatform.CartService.Client;

import com.example.ecommerceplatform.CartService.Dto.request.CreateOrderRequest;
import com.example.ecommerceplatform.CartService.Dto.response.OrderCreatedResponse;
import com.example.ecommerceplatform.CartService.Exception.DownstreamServiceUnavailableException;
import org.springframework.stereotype.Component;

@Component
public class OrderServiceClientFallback implements OrderServiceClient {

    @Override
    public OrderCreatedResponse createOrder(CreateOrderRequest request) {
        throw new DownstreamServiceUnavailableException("Order Service", null);
    }
}
