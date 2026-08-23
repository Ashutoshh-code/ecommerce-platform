package com.example.ecommerceplatform.CartService.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItemCountResponseDto {

    private UUID cartId;
    private UUID userId;

    private long distinctItemCount;
    private long totalQuantity;
}
