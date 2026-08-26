package com.example.ecommerceplatform.CartService.Client;

import com.example.ecommerceplatform.CartService.Dto.request.StockAvailabilityRequestDto;
import com.example.ecommerceplatform.CartService.Dto.response.StockAvailabilityResponseDto;
import com.example.ecommerceplatform.CartService.Exception.DownstreamServiceUnavailableException;
import org.springframework.stereotype.Component;

@Component
public class MerchantServiceClientFallback implements MerchantServiceClient {

    @Override
    public StockAvailabilityResponseDto checkAvailability(StockAvailabilityRequestDto request) {
        throw new DownstreamServiceUnavailableException("Merchant Service", null);
    }
}
