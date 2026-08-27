package com.example.ecommerceplatform.CartService.Client;

import com.example.ecommerceplatform.CartService.Dto.request.ReserveStockRequestDto;
import com.example.ecommerceplatform.CartService.Dto.request.StockAvailabilityRequestDto;
import com.example.ecommerceplatform.CartService.Dto.response.StockAvailabilityResponseDto;
import com.example.ecommerceplatform.CartService.Dto.response.StockReservationResponseDto;
import com.example.ecommerceplatform.CartService.Exception.DownstreamServiceUnavailableException;
import com.example.ecommerceplatform.CartService.Exception.ProductUnavailableException;
import feign.FeignException;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * A 404 from Merchant Service means "no listing for this merchant+variant" — a real,
 * meaningful answer, not an outage. Everything else (timeout, connection refused, 5xx)
 * means Merchant Service is genuinely unreachable. A plain {@code fallback} can't tell
 * these apart since it never sees the original exception; a {@code fallbackFactory} does.
 */
@Component
public class MerchantServiceClientFallbackFactory implements FallbackFactory<MerchantServiceClient> {

    @Override
    public MerchantServiceClient create(Throwable cause) {
        return new MerchantServiceClient() {
            @Override
            public StockAvailabilityResponseDto checkAvailability(StockAvailabilityRequestDto request) {
                throw translate(cause, request.getVariantId());
            }

            @Override
            public StockReservationResponseDto reserveStock(ReserveStockRequestDto request) {
                throw translate(cause, request.getVariantId());
            }

            @Override
            public StockReservationResponseDto releaseStock(ReserveStockRequestDto request) {
                throw translate(cause, request.getVariantId());
            }
        };
    }

    private static RuntimeException translate(Throwable cause, String variantId) {
        if (cause instanceof FeignException fe && fe.status() == 404) {
            return new ProductUnavailableException(variantId);
        }
        return new DownstreamServiceUnavailableException("Merchant Service", cause);
    }
}
