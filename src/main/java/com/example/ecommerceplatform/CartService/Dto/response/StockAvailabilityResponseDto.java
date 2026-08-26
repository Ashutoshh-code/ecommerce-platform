package com.example.ecommerceplatform.CartService.Dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Contract expected back from Merchant Service (POST /api/internal/stock/availability).
 * Mirrors Merchant Service's own StockAvailabilityResponse record exactly. Still no product
 * name here — Merchant Service's stock endpoint answers availability + price only.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockAvailabilityResponseDto {

    private boolean available;
    private String listingId;
    private String merchantId;
    private String variantId;
    private BigDecimal price;
    private Integer requestedQuantity;
    private Integer availableQuantity;
}
