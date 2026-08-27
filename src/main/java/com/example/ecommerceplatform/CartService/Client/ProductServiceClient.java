package com.example.ecommerceplatform.CartService.Client;

import com.example.ecommerceplatform.CartService.Dto.response.ProductResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Cart Service's contract for Product Service (ProductController#getProduct —
 * GET /api/products/{productId}). Keyed by productId, not variantId — Product Service
 * exposes no variant-level read endpoint, so name/thumbnail are product-level only.
 * Falls back via {@link ProductServiceClientFallbackFactory}, which distinguishes a
 * real "product not found" (404) from Product Service actually being unreachable.
 */
@FeignClient(
        name = "product-service",
        url = "${clients.product-service.url}",
        fallbackFactory = ProductServiceClientFallbackFactory.class
)
public interface ProductServiceClient {

    @GetMapping("/api/products/{productId}")
    ProductResponseDto getProduct(@PathVariable("productId") String productId);
}
