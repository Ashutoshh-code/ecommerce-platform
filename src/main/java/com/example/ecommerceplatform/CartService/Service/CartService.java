package com.example.ecommerceplatform.CartService.Service;

import com.example.ecommerceplatform.CartService.Dto.AddCartItemRequestDto;
import com.example.ecommerceplatform.CartService.Dto.BuyNowResponseDto;
import com.example.ecommerceplatform.CartService.Dto.CartItemCountResponseDto;
import com.example.ecommerceplatform.CartService.Dto.CheckoutAddressRequestDto;
import com.example.ecommerceplatform.CartService.Dto.CartResponseDto;
import com.example.ecommerceplatform.CartService.Dto.CartValidationResponseDto;
import com.example.ecommerceplatform.CartService.Dto.CheckoutResponseDto;
import com.example.ecommerceplatform.CartService.Dto.PatchCartItemQuantityRequestDto;
import com.example.ecommerceplatform.CartService.Dto.PatchCartItemQuantityResponseDto;

import java.util.UUID;

public interface CartService {

    CartResponseDto getCart(UUID userId);

    CartResponseDto addItemToCart(UUID userId, AddCartItemRequestDto request);

    PatchCartItemQuantityResponseDto patchItemQuantity(UUID userId, UUID cartItemId, PatchCartItemQuantityRequestDto request);

    CartResponseDto removeItem(UUID userId, UUID cartItemId);

    void clearCart(UUID userId);

    CartItemCountResponseDto getItemCount(UUID userId);

    CheckoutResponseDto checkout(UUID userId, CheckoutAddressRequestDto customAddress);

    CartValidationResponseDto validateCart(UUID userId);

    BuyNowResponseDto buyNow(UUID userId, AddCartItemRequestDto request);

    CheckoutResponseDto checkoutItem(UUID userId, UUID cartItemId, CheckoutAddressRequestDto customAddress);
}
