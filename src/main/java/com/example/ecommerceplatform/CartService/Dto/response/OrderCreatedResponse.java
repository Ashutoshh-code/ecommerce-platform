package com.example.ecommerceplatform.CartService.Dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Contract expected back from Order Service after POST /api/orders.
 * Not implemented here — Order Service team owns the real response shape.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCreatedResponse {

    private UUID orderId;
    private String status;
    private BigDecimal totalPrice;
}
