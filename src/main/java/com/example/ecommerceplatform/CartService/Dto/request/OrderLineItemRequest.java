package com.example.ecommerceplatform.CartService.Dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderLineItemRequest {

    private String productId;
    private String variantId;
    private String merchantId;

    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
}
