package com.example.ecommerceplatform.CartService.Client;

import com.example.ecommerceplatform.CartService.Dto.response.ProductImageResponse;
import com.example.ecommerceplatform.CartService.Exception.DownstreamServiceUnavailableException;
import org.springframework.stereotype.Component;

@Component
public class ProductServiceClientFallback implements ProductServiceClient {

    @Override
    public ProductImageResponse getProductImage(String variantId) {
        throw new DownstreamServiceUnavailableException("Product Service", null);
    }
}
