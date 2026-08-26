package com.example.ecommerceplatform.CartService.Client;

import com.example.ecommerceplatform.CartService.Dto.request.StockAvailabilityRequestDto;
import com.example.ecommerceplatform.CartService.Dto.response.StockAvailabilityResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Cart Service's contract for Merchant Service's real stock endpoint
 * (StockController#checkAvailability — POST /api/internal/stock/availability).
 * Falls back to {@link MerchantServiceClientFallback} until it is reachable.
 */
@FeignClient(
        name = "merchant-service",
        url = "${clients.merchant-service.url}",
        fallback = MerchantServiceClientFallback.class
)
public interface MerchantServiceClient {

    @PostMapping("/api/internal/stock/availability")
    StockAvailabilityResponseDto checkAvailability(@RequestBody StockAvailabilityRequestDto request);
}
