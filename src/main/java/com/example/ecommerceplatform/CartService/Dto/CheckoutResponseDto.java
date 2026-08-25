package com.example.ecommerceplatform.CartService.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutResponseDto {

    private UUID orderId;
    private UUID cartId;
    private UUID userId;

    private Integer itemCount;
    private BigDecimal totalPrice;
}
