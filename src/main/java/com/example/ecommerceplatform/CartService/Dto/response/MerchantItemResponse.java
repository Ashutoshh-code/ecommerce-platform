package com.example.ecommerceplatform.CartService.Dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Contract expected from Merchant Service
 * (GET /api/merchants/{merchantId}/products/{productId}/stock).
 * merchantId/productId/variantId are Merchant Service's own string IDs, not UUIDs.
 * Merchant Service supplies listing/display name plus stock/price for a given
 * product+variant+merchant combination; the product image comes from Product Service
 * instead (see {@link ProductImageResponse}). Not implemented here — Merchant Service
 * team owns the real response shape.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MerchantItemResponse {

    private String productId;
    private String variantId;
    private String merchantId;

    private String productName;

    private BigDecimal price;
    private Integer availableStock;
    private MerchantItemStatus status;
}
