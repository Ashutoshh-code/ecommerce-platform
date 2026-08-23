package com.example.ecommerceplatform.CartService.Client;

import com.example.ecommerceplatform.CartService.Dto.response.StockPriceResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * Cart Service's expected contract for Merchant Service.
 * Merchant Service is owned by another team; this interface is the contract Cart Service
 * relies on to validate stock and current price before adding an item to the cart.
 * Falls back to {@link MerchantServiceClientFallback} until it is reachable.
 */
@FeignClient(
        name = "merchant-service",
        url = "${clients.merchant-service.url}",
        fallback = MerchantServiceClientFallback.class
)
public interface MerchantServiceClient {

    @GetMapping("/api/merchants/{merchantId}/products/{productId}/stock")
    StockPriceResponse getStockAndPrice(
            @PathVariable("merchantId") UUID merchantId,
            @PathVariable("productId") UUID productId,
            @RequestParam(value = "variantId", required = false) UUID variantId
    );
}
