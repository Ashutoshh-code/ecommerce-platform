package com.example.ecommerceplatform.CartService.Client;

import com.example.ecommerceplatform.CartService.Dto.response.ProductDetailsResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * Cart Service's expected contract for Product Service.
 * Product Service is owned by another team; this interface is the contract Cart Service
 * relies on. Falls back to {@link ProductServiceClientFallback} until it is reachable.
 */
@FeignClient(
        name = "product-service",
        url = "${clients.product-service.url}",
        fallback = ProductServiceClientFallback.class
)
public interface ProductServiceClient {

    @GetMapping("/api/products/{productId}")
    ProductDetailsResponse getProductDetails(
            @PathVariable("productId") UUID productId,
            @RequestParam(value = "variantId", required = false) UUID variantId
    );
}
