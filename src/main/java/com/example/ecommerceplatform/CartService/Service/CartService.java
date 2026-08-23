package com.example.ecommerceplatform.CartService.Service;

import com.example.ecommerceplatform.CartService.Dto.AddCartItemRequestDto;
import com.example.ecommerceplatform.CartService.Dto.CartItemCountResponseDto;
import com.example.ecommerceplatform.CartService.Dto.CartResponseDto;

import java.util.UUID;

public interface CartService {

    CartResponseDto getCart(UUID userId);

    CartResponseDto addItemToCart(UUID userId, AddCartItemRequestDto request);

    CartResponseDto removeItem(UUID userId, UUID cartItemId);

    void clearCart(UUID userId);

    CartItemCountResponseDto getItemCount(UUID userId);
}
