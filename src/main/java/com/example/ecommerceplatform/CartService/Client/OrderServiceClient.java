package com.example.ecommerceplatform.CartService.Client;

import com.example.ecommerceplatform.CartService.Dto.request.CreateOrderRequest;
import com.example.ecommerceplatform.CartService.Dto.response.OrderCreatedResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Cart Service's contract for Order Service (OrderController#createOrder —
 * POST /api/orders). Falls back via {@link OrderServiceClientFallbackFactory}, which
 * distinguishes a rejected order (400 — validation/total-mismatch/invalid-order-details)
 * and a duplicate checkout (409 — an order already exists for this cart) from Order
 * Service actually being unreachable.
 */
@FeignClient(
        name = "order-service",
        url = "${clients.order-service.url}",
        fallbackFactory = OrderServiceClientFallbackFactory.class
)
public interface OrderServiceClient {

    @PostMapping("/api/orders")
    OrderCreatedResponse createOrder(@RequestBody CreateOrderRequest request);
}
