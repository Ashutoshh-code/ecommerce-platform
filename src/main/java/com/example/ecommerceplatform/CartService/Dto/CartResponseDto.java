package com.example.ecommerceplatform.CartService.Dto;

import com.example.ecommerceplatform.CartService.Entity.CartStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartResponseDto {

    private UUID cartId;
    private UUID userId;
    private CartStatus status;

    private List<CartItemResponseDto> items;

    private Integer totalItemCount;
    private BigDecimal totalPrice;
}
