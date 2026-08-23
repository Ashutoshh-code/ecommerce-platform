package com.example.ecommerceplatform.CartService.Client;

import com.example.ecommerceplatform.CartService.Dto.response.StockPriceResponse;
import com.example.ecommerceplatform.CartService.Exception.DownstreamServiceUnavailableException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MerchantServiceClientFallback implements MerchantServiceClient {

    @Override
    public StockPriceResponse getStockAndPrice(UUID merchantId, UUID productId, UUID variantId) {
        throw new DownstreamServiceUnavailableException("Merchant Service", null);
    }
}
