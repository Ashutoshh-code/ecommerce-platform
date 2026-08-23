package com.example.ecommerceplatform.CartService.Client;

import com.example.ecommerceplatform.CartService.Dto.response.ProductDetailsResponse;
import com.example.ecommerceplatform.CartService.Exception.DownstreamServiceUnavailableException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ProductServiceClientFallback implements ProductServiceClient {

    @Override
    public ProductDetailsResponse getProductDetails(UUID productId, UUID variantId) {
        throw new DownstreamServiceUnavailableException("Product Service", null);
    }
}
