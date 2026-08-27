package com.example.ecommerceplatform.CartService.Client;

import com.example.ecommerceplatform.CartService.Dto.response.ProductResponseDto;
import com.example.ecommerceplatform.CartService.Exception.DownstreamServiceUnavailableException;
import com.example.ecommerceplatform.CartService.Exception.ProductNotFoundException;
import feign.FeignException;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * A 404 from Product Service means "no such product" — a real, meaningful answer, not
 * an outage. Everything else means Product Service is genuinely unreachable.
 */
@Component
public class ProductServiceClientFallbackFactory implements FallbackFactory<ProductServiceClient> {

    @Override
    public ProductServiceClient create(Throwable cause) {
        return productId -> {
            if (cause instanceof FeignException fe && fe.status() == 404) {
                throw new ProductNotFoundException(productId);
            }
            throw new DownstreamServiceUnavailableException("Product Service", cause);
        };
    }
}
