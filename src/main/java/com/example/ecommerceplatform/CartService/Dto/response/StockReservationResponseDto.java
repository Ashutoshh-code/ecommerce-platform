package com.example.ecommerceplatform.CartService.Dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Contract expected back from Merchant Service's /reserve and /release endpoints.
 * success=false is the concurrency-safe answer when two checkouts race for the last
 * unit — whichever request's atomic decrement at Merchant Service loses gets false
 * here, not an exception.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReservationResponseDto {

    private boolean success;
    private String listingId;
    private String merchantId;
    private String variantId;
    private Integer quantity;
    private Integer remainingStock;
}
