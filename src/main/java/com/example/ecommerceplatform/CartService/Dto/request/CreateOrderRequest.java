package com.example.ecommerceplatform.CartService.Dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Contract Cart Service sends to Order Service (POST /api/orders) at checkout.
 * Not implemented here — Order Service team owns the real receiving endpoint.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOrderRequest {

    private UUID cartId;
    private UUID userId;

    private List<OrderLineItemRequest> items;
    private BigDecimal totalPrice;
}
