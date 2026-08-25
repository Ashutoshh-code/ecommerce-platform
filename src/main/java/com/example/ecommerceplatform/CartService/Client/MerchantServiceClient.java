package com.example.ecommerceplatform.CartService.Client;

import com.example.ecommerceplatform.CartService.Dto.response.MerchantItemResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Cart Service's expected contract for Merchant Service.
 * Merchant Service is owned by another team; this interface is the contract Cart Service
 * relies on for both product display data (name, image) and stock/price validation before
 * adding an item to the cart. Falls back to {@link MerchantServiceClientFallback} until
 * it is reachable.
 */
@FeignClient(
        name = "merchant-service",
        url = "${clients.merchant-service.url}",
        fallback = MerchantServiceClientFallback.class
)
public interface MerchantServiceClient {

    @GetMapping("/api/merchants/{merchantId}/products/{productId}/stock")
    MerchantItemResponse getItemDetails(
            @PathVariable("merchantId") String merchantId,
            @PathVariable("productId") String productId,
            @RequestParam("variantId") String variantId
    );
}
