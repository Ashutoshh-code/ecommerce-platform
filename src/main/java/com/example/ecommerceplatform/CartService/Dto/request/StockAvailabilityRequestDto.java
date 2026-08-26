package com.example.ecommerceplatform.CartService.Dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Contract Cart Service sends to Merchant Service (POST /api/internal/stock/availability).
 * Mirrors Merchant Service's own StockAvailabilityRequest record exactly — no productId,
 * since variantId alone identifies a merchant's listing.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockAvailabilityRequestDto {

    private String merchantId;
    private String variantId;
    private Integer quantity;
}
