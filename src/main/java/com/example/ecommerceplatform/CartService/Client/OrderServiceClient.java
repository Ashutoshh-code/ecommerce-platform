package com.example.ecommerceplatform.CartService.Client;

import com.example.ecommerceplatform.CartService.Dto.request.CreateOrderRequest;
import com.example.ecommerceplatform.CartService.Dto.response.OrderCreatedResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Cart Service's expected contract for Order Service.
 * Order Service is owned by another team; this interface is the contract Cart Service
 * relies on to hand off a validated, priced cart at checkout.
 * Falls back to {@link OrderServiceClientFallback} until it is reachable.
 */
@FeignClient(
        name = "order-service",
        url = "${clients.order-service.url}",
        fallback = OrderServiceClientFallback.class
)
public interface OrderServiceClient {

    @PostMapping("/api/orders")
    OrderCreatedResponse createOrder(@RequestBody CreateOrderRequest request);
}
