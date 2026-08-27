package com.example.ecommerceplatform.CartService.Client;

import com.example.ecommerceplatform.CartService.Dto.request.ReserveStockRequestDto;
import com.example.ecommerceplatform.CartService.Dto.request.StockAvailabilityRequestDto;
import com.example.ecommerceplatform.CartService.Dto.response.StockAvailabilityResponseDto;
import com.example.ecommerceplatform.CartService.Dto.response.StockReservationResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Cart Service's contract for Merchant Service's real stock endpoints
 * (StockController — POST /api/internal/stock/availability, /reserve, /release).
 * checkAvailability is a read-only pre-check (used for its price, and to fail fast
 * cheaply); reserveStock is the atomic operation that actually decides a stock race —
 * releaseStock is its compensating undo. Falls back via
 * {@link MerchantServiceClientFallbackFactory}, which distinguishes a real
 * "listing not found" (404) from Merchant Service actually being unreachable.
 */
@FeignClient(
        name = "merchant-service",
        url = "${clients.merchant-service.url}",
        fallbackFactory = MerchantServiceClientFallbackFactory.class
)
public interface MerchantServiceClient {

    @PostMapping("/api/internal/stock/availability")
    StockAvailabilityResponseDto checkAvailability(@RequestBody StockAvailabilityRequestDto request);

    @PostMapping("/api/internal/stock/reserve")
    StockReservationResponseDto reserveStock(@RequestBody ReserveStockRequestDto request);

    @PostMapping("/api/internal/stock/release")
    StockReservationResponseDto releaseStock(@RequestBody ReserveStockRequestDto request);
}
