package com.example.ecommerceplatform.CartService.Client;

import com.example.ecommerceplatform.CartService.Dto.response.ProductImageResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Cart Service's expected contract for Product Service.
 * Product Service is owned by another team; Cart Service only needs the display image
 * for a given variant from it (stock/price/name come from Merchant Service instead).
 * Falls back to {@link ProductServiceClientFallback} until it is reachable.
 */
@FeignClient(
        name = "product-service",
        url = "${clients.product-service.url}",
        fallback = ProductServiceClientFallback.class
)
public interface ProductServiceClient {

    @GetMapping("/api/products/variants/{variantId}/image")
    ProductImageResponse getProductImage(@PathVariable("variantId") String variantId);
}
