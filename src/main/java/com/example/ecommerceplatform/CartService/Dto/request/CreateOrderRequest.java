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
 * Contract Cart Service sends to Order Service (POST /api/orders) at checkout. Order
 * Service no longer calls User Service itself, so Cart Service snapshots the customer's
 * identity and address here instead.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOrderRequest {

    private UUID cartId;
    private UUID userId;

    private String email;
    private String firstName;
    private String lastName;
    private ShippingAddressRequest shippingAddress;

    private List<OrderLineItemRequest> items;
    private BigDecimal totalPrice;
}
