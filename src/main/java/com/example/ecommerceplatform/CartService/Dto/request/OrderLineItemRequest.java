package com.example.ecommerceplatform.CartService.Dto.request;

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
public class OrderLineItemRequest {

    private UUID cartItemId;
    private String productId;
    private String variantId;
    private String merchantId;
    private String productName;
    private String imageUrl;

    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
}
