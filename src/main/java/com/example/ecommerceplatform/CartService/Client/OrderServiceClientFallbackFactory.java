package com.example.ecommerceplatform.CartService.Client;

import com.example.ecommerceplatform.CartService.Exception.DownstreamServiceUnavailableException;
import com.example.ecommerceplatform.CartService.Exception.DuplicateOrderException;
import com.example.ecommerceplatform.CartService.Exception.OrderCreationFailedException;
import feign.FeignException;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Order Service's own docs define three real outcomes: 400 (validation error, total
 * mismatch, or unknown user/address), 409 (an order already exists for this cart), and
 * 503 (it couldn't reach User Service). Only the last one — plus network-level failures —
 * means Order Service itself is unreachable; the first two are real answers Cart Service
 * should surface clearly instead of a generic "unavailable".
 */
@Component
public class OrderServiceClientFallbackFactory implements FallbackFactory<OrderServiceClient> {

    @Override
    public OrderServiceClient create(Throwable cause) {
        return request -> {
            if (cause instanceof FeignException fe) {
                if (fe.status() == 400) {
                    throw new OrderCreationFailedException(fe.contentUTF8());
                }
                if (fe.status() == 409) {
                    throw new DuplicateOrderException(request.getCartId());
                }
            }
            throw new DownstreamServiceUnavailableException("Order Service", cause);
        };
    }
}
