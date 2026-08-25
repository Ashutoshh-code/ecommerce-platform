package com.example.ecommerceplatform.CartService.Client;

import com.example.ecommerceplatform.CartService.Dto.response.MerchantItemResponse;
import com.example.ecommerceplatform.CartService.Exception.DownstreamServiceUnavailableException;
import org.springframework.stereotype.Component;

@Component
public class MerchantServiceClientFallback implements MerchantServiceClient {

    @Override
    public MerchantItemResponse getItemDetails(String merchantId, String productId, String variantId) {
        throw new DownstreamServiceUnavailableException("Merchant Service", null);
    }
}
