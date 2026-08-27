package com.example.ecommerceplatform.CartService.Dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Contract expected back from Order Service after POST /api/orders. Mirrors
 * OrderResponse's fields that Cart Service actually needs — the field is named "id",
 * not "orderId", to match OrderResponse.id() exactly. Extra fields on the real response
 * (email, shippingAddress, items, createdAt, updatedAt) are simply ignored.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCreatedResponse {

    private UUID id;
    private String status;
    private BigDecimal totalPrice;
}
