package com.example.ecommerceplatform.CartService.Dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Contract expected from Product Service (GET /api/products/{productId}).
 * Not implemented here — Product Service team owns the real response shape.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDetailsResponse {

    private UUID productId;
    private UUID variantId;
    private String name;
    private String imageUrl;
    private boolean available;
}
