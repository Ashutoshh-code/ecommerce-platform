package com.example.ecommerceplatform.CartService.Dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Contract Cart Service sends to Merchant Service for both
 * POST /api/internal/stock/reserve and POST /api/internal/stock/release — Merchant
 * Service's own controller reuses the same request shape for both.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReserveStockRequestDto {

    private String merchantId;
    private String variantId;
    private Integer quantity;
}
