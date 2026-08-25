package com.example.ecommerceplatform.CartService.Dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Contract expected from Product Service (GET /api/products/variants/{variantId}/image).
 * Looked up by variantId, since the same product's different variants can have different
 * photos. Not implemented here — Product Service team owns the real response shape.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImageResponse {

    private String variantId;
    private String imageUrl;
}
