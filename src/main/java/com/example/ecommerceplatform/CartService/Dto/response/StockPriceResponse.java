package com.example.ecommerceplatform.CartService.Dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Contract expected from Merchant Service
 * (GET /api/merchants/{merchantId}/products/{productId}/stock).
 * Not implemented here — Merchant Service team owns the real response shape.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockPriceResponse {

    private UUID productId;
    private UUID variantId;
    private UUID merchantId;

    private BigDecimal price;
    private Integer availableStock;
    private boolean inStock;
}
