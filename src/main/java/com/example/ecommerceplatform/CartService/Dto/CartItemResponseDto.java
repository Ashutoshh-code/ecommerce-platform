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
public class CartItemResponseDto {

    private UUID cartItemId;
    private UUID productId;
    private UUID variantId;
    private UUID merchantId;

    private String productName;
    private String productImage;
    private boolean available;

    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
}
