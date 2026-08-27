package com.example.ecommerceplatform.CartService.Dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Contract expected back from Product Service (GET /api/products/{productId}).
 * Mirrors the fields Cart Service actually needs from Product Service's Product
 * document — name and thumbnail are product-level, not per-variant, since Product
 * Service exposes no variant-level read endpoint. Extra fields on the real response
 * (title, description, usp, status, attributes, timestamps) are simply ignored.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponseDto {

    private String productId;
    private String name;
    private String thumbnail;
}
